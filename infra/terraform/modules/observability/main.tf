variable "name" { type = string }
variable "oidc_provider_arn" { type = string }
variable "oidc_host" { type = string }
variable "kms_key_arn" { type = string }
variable "enable_managed_grafana" {
  type    = bool
  default = true
}
variable "grafana_admin_group_ids" {
  type    = list(string)
  default = []
}

resource "aws_prometheus_workspace" "this" {
  alias               = var.name
  kms_key_arn         = var.kms_key_arn
  logging_configuration {
    log_group_arn = "${aws_cloudwatch_log_group.amp.arn}:*"
  }
}
resource "aws_cloudwatch_log_group" "amp" {
  name              = "/pj/${var.name}/amp"
  retention_in_days = 30
}
resource "aws_iam_role" "prom_write" {
  name = "${var.name}-prometheus-remote-write"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRoleWithWebIdentity"
      Principal = { Federated = var.oidc_provider_arn }
      Condition = { StringEquals = { "${var.oidc_host}:sub" = "system:serviceaccount:observability:prometheus", "${var.oidc_host}:aud" = "sts.amazonaws.com" } }
    }]
  })
}
resource "aws_iam_role_policy" "prom_write" {
  role = aws_iam_role.prom_write.id
  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = ["aps:RemoteWrite", "aps:GetSeries", "aps:GetLabels", "aps:GetMetricMetadata"], Resource = aws_prometheus_workspace.this.arn }]
  })
}

resource "aws_iam_role" "grafana" {
  count = var.enable_managed_grafana ? 1 : 0
  name  = "${var.name}-grafana"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "sts:AssumeRole", Principal = { Service = "grafana.amazonaws.com" } }]
  })
}
resource "aws_iam_role_policy" "grafana" {
  count = var.enable_managed_grafana ? 1 : 0
  role  = aws_iam_role.grafana[0].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = ["aps:QueryMetrics", "aps:GetSeries", "aps:GetLabels", "aps:GetMetricMetadata", "aps:ListWorkspaces", "aps:DescribeWorkspace", "cloudwatch:GetMetricData", "cloudwatch:ListMetrics", "logs:StartQuery", "logs:GetQueryResults", "logs:DescribeLogGroups", "xray:GetTraceSummaries", "xray:BatchGetTraces"], Resource = "*" }]
  })
}
resource "aws_grafana_workspace" "this" {
  count                    = var.enable_managed_grafana ? 1 : 0
  name                     = var.name
  account_access_type      = "CURRENT_ACCOUNT"
  authentication_providers = ["AWS_SSO"]
  permission_type          = "SERVICE_MANAGED"
  role_arn                 = aws_iam_role.grafana[0].arn
  data_sources             = ["PROMETHEUS", "CLOUDWATCH", "XRAY"]
}

# ECK (Elasticsearch/Kibana/Fluent Bit) is deployed via ArgoCD (infra/argocd/apps/platform); the IRSA role for ES snapshots to S3:
resource "aws_s3_bucket" "es_snapshots" { bucket = "${var.name}-es-snapshots" }
resource "aws_s3_bucket_server_side_encryption_configuration" "es" {
  bucket = aws_s3_bucket.es_snapshots.id
  rule { apply_server_side_encryption_by_default { sse_algorithm = "aws:kms", kms_master_key_id = var.kms_key_arn } }
}
resource "aws_iam_role" "es_snapshot" {
  name = "${var.name}-es-snapshot"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRoleWithWebIdentity"
      Principal = { Federated = var.oidc_provider_arn }
      Condition = { StringEquals = { "${var.oidc_host}:sub" = "system:serviceaccount:logging:elasticsearch", "${var.oidc_host}:aud" = "sts.amazonaws.com" } }
    }]
  })
}
resource "aws_iam_role_policy" "es_snapshot" {
  role = aws_iam_role.es_snapshot.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["s3:ListBucket", "s3:GetBucketLocation", "s3:ListBucketMultipartUploads"], Resource = aws_s3_bucket.es_snapshots.arn },
      { Effect = "Allow", Action = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:AbortMultipartUpload"], Resource = "${aws_s3_bucket.es_snapshots.arn}/*" },
      { Effect = "Allow", Action = ["kms:Decrypt", "kms:GenerateDataKey"], Resource = var.kms_key_arn }
    ]
  })
}
output "amp_remote_write_url" { value = "${aws_prometheus_workspace.this.prometheus_endpoint}api/v1/remote_write" }
output "prometheus_role_arn" { value = aws_iam_role.prom_write.arn }
output "es_snapshot_role_arn" { value = aws_iam_role.es_snapshot.arn }
output "grafana_endpoint" { value = try(aws_grafana_workspace.this[0].endpoint, null) }
