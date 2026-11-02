variable "name" { type = string }
variable "vpc_id" { type = string }
variable "data_subnet_ids" { type = list(string) }
variable "allowed_security_group_ids" { type = list(string) }
variable "kms_key_arn" { type = string }
variable "secrets_kms_key_arn" { type = string }
variable "engine_version" {
  type    = string
  default = "16.4"
}
variable "clusters" {
  description = "Per-criticality clusters. tier: tier0 (identity, payments, subscription), tier1 (profile, matchmaking, chat), tier2 (analytics, admin, moderation)"
  type = map(object({
    instance_class      = string
    instance_count      = number
    serverless_min_acu  = optional(number)
    serverless_max_acu  = optional(number)
    backup_days         = number
    deletion_protection = bool
    databases           = list(string)
  }))
}
variable "enable_rds_proxy" {
  type    = bool
  default = true
}
variable "tags" {
  type    = map(string)
  default = {}
}
