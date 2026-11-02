variable "name" { type = string }
variable "vpc_id" { type = string }
variable "data_subnet_ids" { type = list(string) }
variable "allowed_security_group_ids" { type = list(string) }
variable "kms_key_arn" { type = string }
variable "node_type" { default = "cache.r7g.large" }
variable "shards" { default = 3 }
variable "replicas_per_shard" { default = 2 }
variable "snapshot_retention" { default = 7 }
variable "tags" {
  type    = map(string)
  default = {}
}

resource "aws_elasticache_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.data_subnet_ids
}
resource "aws_security_group" "redis" {
  name_prefix = "${var.name}-redis-"
  vpc_id      = var.vpc_id
  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = var.allowed_security_group_ids
  }
  lifecycle { create_before_destroy = true }
  tags = merge(var.tags, { Name = "${var.name}-redis" })
}
resource "aws_elasticache_parameter_group" "this" {
  name   = "${var.name}-redis7-cluster"
  family = "redis7"
  parameter {
    name  = "cluster-enabled"
    value = "yes"
  }
  parameter {
    name  = "maxmemory-policy"
    value = "volatile-lru"
  }
}
resource "random_password" "auth" {
  length  = 40
  special = false
}
resource "aws_elasticache_replication_group" "this" {
  replication_group_id       = var.name
  description                = "${var.name} cache/session/rate-limit/presence"
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = var.node_type
  port                       = 6379
  parameter_group_name       = aws_elasticache_parameter_group.this.name
  subnet_group_name          = aws_elasticache_subnet_group.this.name
  security_group_ids         = [aws_security_group.redis.id]
  num_node_groups            = var.shards
  replicas_per_node_group    = var.replicas_per_shard
  automatic_failover_enabled = true
  multi_az_enabled           = true
  at_rest_encryption_enabled = true
  kms_key_id                 = var.kms_key_arn
  transit_encryption_enabled = true
  transit_encryption_mode    = "required"
  auth_token                 = random_password.auth.result
  snapshot_retention_limit   = var.snapshot_retention
  snapshot_window            = "21:30-22:30"
  maintenance_window         = "sun:22:30-sun:23:30"
  apply_immediately          = false
  tags                       = var.tags
}
resource "aws_secretsmanager_secret" "auth" {
  name       = "${var.name}/redis-auth"
  kms_key_id = var.kms_key_arn
}
resource "aws_secretsmanager_secret_version" "auth" {
  secret_id     = aws_secretsmanager_secret.auth.id
  secret_string = jsonencode({ host = aws_elasticache_replication_group.this.configuration_endpoint_address, port = 6379, password = random_password.auth.result })
}
output "configuration_endpoint" { value = aws_elasticache_replication_group.this.configuration_endpoint_address }
output "auth_secret_arn" { value = aws_secretsmanager_secret.auth.arn }
