resource "aws_db_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.data_subnet_ids
}
resource "aws_security_group" "db" {
  name_prefix = "${var.name}-aurora-"
  vpc_id      = var.vpc_id
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = var.allowed_security_group_ids
  }
  tags = merge(var.tags, { Name = "${var.name}-aurora" })
  lifecycle { create_before_destroy = true }
}

resource "aws_rds_cluster_parameter_group" "pg16" {
  name_prefix = "${var.name}-pg16-"
  family      = "aurora-postgresql16"
  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
  parameter {
    name  = "log_min_duration_statement"
    value = "500"
  }
  parameter {
    name         = "shared_preload_libraries"
    value        = "pg_stat_statements,pgaudit"
    apply_method = "pending-reboot"
  }
  parameter {
    name  = "pgaudit.log"
    value = "ddl,role"
  }
  lifecycle { create_before_destroy = true }
}

resource "aws_iam_role" "monitoring" {
  name_prefix = "${var.name}-rds-mon-"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "sts:AssumeRole", Principal = { Service = "monitoring.rds.amazonaws.com" } }]
  })
}
resource "aws_iam_role_policy_attachment" "monitoring" {
  role       = aws_iam_role.monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

resource "aws_rds_cluster" "this" {
  for_each                        = var.clusters
  cluster_identifier              = "${var.name}-${each.key}"
  engine                          = "aurora-postgresql"
  engine_version                  = var.engine_version
  database_name                   = replace(each.value.databases[0], "-", "_")
  master_username                 = "pj_admin"
  manage_master_user_password     = true
  master_user_secret_kms_key_id   = var.secrets_kms_key_arn
  db_subnet_group_name            = aws_db_subnet_group.this.name
  vpc_security_group_ids          = [aws_security_group.db.id]
  db_cluster_parameter_group_name = aws_rds_cluster_parameter_group.pg16.name
  storage_encrypted               = true
  kms_key_id                      = var.kms_key_arn
  iam_database_authentication_enabled = true
  backup_retention_period         = each.value.backup_days
  preferred_backup_window         = "21:00-22:00" # 02:30 IST
  copy_tags_to_snapshot           = true
  deletion_protection             = each.value.deletion_protection
  skip_final_snapshot             = !each.value.deletion_protection
  final_snapshot_identifier       = each.value.deletion_protection ? "${var.name}-${each.key}-final" : null
  enabled_cloudwatch_logs_exports = ["postgresql"]
  dynamic "serverlessv2_scaling_configuration" {
    for_each = each.value.serverless_max_acu == null ? [] : [1]
    content {
      min_capacity = each.value.serverless_min_acu
      max_capacity = each.value.serverless_max_acu
    }
  }
  tags = merge(var.tags, { Tier = each.key })
}

locals {
  instances = merge([
    for k, c in var.clusters : {
      for i in range(c.instance_count) : "${k}-${i}" => { cluster = k, class = c.instance_class, idx = i }
    }
  ]...)
}
resource "aws_rds_cluster_instance" "this" {
  for_each                     = local.instances
  identifier                   = "${var.name}-${each.key}"
  cluster_identifier           = aws_rds_cluster.this[each.value.cluster].id
  instance_class               = each.value.class
  engine                       = "aurora-postgresql"
  engine_version               = var.engine_version
  performance_insights_enabled = true
  performance_insights_kms_key_id = var.kms_key_arn
  monitoring_interval          = 30
  monitoring_role_arn          = aws_iam_role.monitoring.arn
  auto_minor_version_upgrade   = true
  promotion_tier               = each.value.idx
  tags                         = var.tags
}

# ---- RDS Proxy (connection multiplexing for ~100s of pods)
resource "aws_iam_role" "proxy" {
  name_prefix = "${var.name}-rdsproxy-"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "sts:AssumeRole", Principal = { Service = "rds.amazonaws.com" } }]
  })
}
resource "aws_iam_role_policy" "proxy" {
  role = aws_iam_role.proxy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["secretsmanager:GetSecretValue"], Resource = [for c in aws_rds_cluster.this : c.master_user_secret[0].secret_arn] },
      { Effect = "Allow", Action = ["kms:Decrypt"], Resource = var.secrets_kms_key_arn }
    ]
  })
}
resource "aws_db_proxy" "this" {
  for_each               = var.enable_rds_proxy ? var.clusters : {}
  name                   = "${var.name}-${each.key}"
  engine_family          = "POSTGRESQL"
  role_arn               = aws_iam_role.proxy.arn
  vpc_subnet_ids         = var.data_subnet_ids
  vpc_security_group_ids = [aws_security_group.db.id]
  require_tls            = true
  idle_client_timeout    = 1800
  auth {
    auth_scheme = "SECRETS"
    iam_auth    = "REQUIRED"
    secret_arn  = aws_rds_cluster.this[each.key].master_user_secret[0].secret_arn
  }
}
resource "aws_db_proxy_default_target_group" "this" {
  for_each      = aws_db_proxy.this
  db_proxy_name = each.value.name
  connection_pool_config {
    max_connections_percent      = 80
    max_idle_connections_percent = 40
    connection_borrow_timeout    = 30
  }
}
resource "aws_db_proxy_target" "this" {
  for_each              = aws_db_proxy.this
  db_proxy_name         = each.value.name
  target_group_name     = aws_db_proxy_default_target_group.this[each.key].name
  db_cluster_identifier = aws_rds_cluster.this[each.key].cluster_identifier
}
resource "aws_db_proxy_endpoint" "ro" {
  for_each               = aws_db_proxy.this
  db_proxy_name          = each.value.name
  db_proxy_endpoint_name = "${each.value.name}-ro"
  vpc_subnet_ids         = var.data_subnet_ids
  vpc_security_group_ids = [aws_security_group.db.id]
  target_role            = "READ_ONLY"
}

output "writer_endpoints" { value = { for k, c in aws_rds_cluster.this : k => c.endpoint } }
output "reader_endpoints" { value = { for k, c in aws_rds_cluster.this : k => c.reader_endpoint } }
output "proxy_endpoints" { value = { for k, p in aws_db_proxy.this : k => p.endpoint } }
output "proxy_ro_endpoints" { value = { for k, p in aws_db_proxy_endpoint.ro : k => p.endpoint } }
output "master_secret_arns" { value = { for k, c in aws_rds_cluster.this : k => c.master_user_secret[0].secret_arn } }
output "security_group_id" { value = aws_security_group.db.id }
