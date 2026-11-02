variable "name" { type = string }
variable "vpc_id" { type = string }
variable "data_subnet_ids" { type = list(string) }
variable "allowed_security_group_ids" { type = list(string) }
variable "kms_key_arn" { type = string }
variable "kafka_version" { default = "3.9.x.kraft" } # KRaft; set "3.6.0" for ZooKeeper-mode where KRaft unavailable
variable "broker_instance_type" { default = "kafka.m7g.xlarge" }
variable "brokers_per_az" { default = 2 }
variable "ebs_gb" { default = 1000 }
variable "tiered_storage" { default = true }
variable "log_retention_days" { default = 30 }
variable "tags" {
  type    = map(string)
  default = {}
}

resource "aws_security_group" "msk" {
  name_prefix = "${var.name}-msk-"
  vpc_id      = var.vpc_id
  ingress {
    from_port       = 9098
    to_port         = 9098
    protocol        = "tcp"
    security_groups = var.allowed_security_group_ids
  }
  lifecycle { create_before_destroy = true }
  tags = merge(var.tags, { Name = "${var.name}-msk" })
}
resource "aws_msk_configuration" "this" {
  name           = "${var.name}-cfg"
  kafka_versions = [var.kafka_version]
  server_properties = <<-P
    auto.create.topics.enable=false
    default.replication.factor=3
    min.insync.replicas=2
    num.partitions=12
    unclean.leader.election.enable=false
    log.retention.hours=168
    compression.type=producer
    allow.everyone.if.no.acl.found=false
  P
}
resource "aws_cloudwatch_log_group" "msk" {
  name              = "/pj/${var.name}/msk"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.kms_key_arn
}
resource "aws_msk_cluster" "this" {
  cluster_name           = var.name
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.brokers_per_az * length(var.data_subnet_ids)
  broker_node_group_info {
    instance_type   = var.broker_instance_type
    client_subnets  = var.data_subnet_ids
    security_groups = [aws_security_group.msk.id]
    storage_info {
      ebs_storage_info {
        volume_size = var.ebs_gb
        provisioned_throughput {
          enabled           = true
          volume_throughput = 250
        }
      }
    }
  }
  storage_mode = var.tiered_storage ? "TIERED" : "LOCAL"
  configuration_info {
    arn      = aws_msk_configuration.this.arn
    revision = aws_msk_configuration.this.latest_revision
  }
  client_authentication {
    unauthenticated = false
    sasl { iam = true }
  }
  encryption_info {
    encryption_at_rest_kms_key_arn = var.kms_key_arn
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }
  enhanced_monitoring = "PER_TOPIC_PER_BROKER"
  open_monitoring {
    prometheus {
      jmx_exporter { enabled_in_broker = true }
      node_exporter { enabled_in_broker = true }
    }
  }
  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk.name
      }
    }
  }
  tags = var.tags
}
output "bootstrap_brokers_iam" { value = aws_msk_cluster.this.bootstrap_brokers_sasl_iam }
output "cluster_arn" { value = aws_msk_cluster.this.arn }
