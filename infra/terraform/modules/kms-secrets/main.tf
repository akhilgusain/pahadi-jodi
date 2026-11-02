variable "name" { type = string }
variable "keys" {
  description = "Logical key names, one CMK each (eks, rds, s3, msk, redis, secrets, logs)"
  type        = list(string)
  default     = ["eks", "rds", "s3", "msk", "redis", "secrets", "logs"]
}
variable "secrets" {
  description = "Secret names to create as empty shells (values set out-of-band)"
  type        = list(string)
  default     = []
}
variable "tags" {
  type    = map(string)
  default = {}
}
data "aws_caller_identity" "me" {}

resource "aws_kms_key" "this" {
  for_each                = toset(var.keys)
  description             = "${var.name} ${each.key}"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  tags                    = var.tags
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Sid = "Root", Effect = "Allow", Principal = { AWS = "arn:aws:iam::${data.aws_caller_identity.me.account_id}:root" }, Action = "kms:*", Resource = "*" },
      { Sid = "Logs", Effect = "Allow", Principal = { Service = "logs.amazonaws.com" }, Action = ["kms:Encrypt*", "kms:Decrypt*", "kms:ReEncrypt*", "kms:GenerateDataKey*", "kms:Describe*"], Resource = "*" }
    ]
  })
}
resource "aws_kms_alias" "this" {
  for_each      = aws_kms_key.this
  name          = "alias/${var.name}-${each.key}"
  target_key_id = each.value.key_id
}
resource "aws_secretsmanager_secret" "this" {
  for_each                = toset(var.secrets)
  name                    = "${var.name}/${each.key}"
  kms_key_id              = aws_kms_key.this["secrets"].arn
  recovery_window_in_days = 14
  tags                    = var.tags
}
output "key_arns" { value = { for k, v in aws_kms_key.this : k => v.arn } }
output "secret_arns" { value = { for k, v in aws_secretsmanager_secret.this : k => v.arn } }
