variable "env" { type = string }
variable "region" { default = "ap-south-1" }
variable "vpc_cidr" { type = string }
variable "single_nat_gateway" { default = false }
variable "domain" { type = string }
variable "media_domain" { type = string }
variable "media_acm_certificate_arn" { type = string }
variable "cloudfront_public_key_pem" { type = string }
variable "eks_version" { default = "1.31" }
variable "system_node_group" {
  type = object({ instance_types = list(string), min = number, max = number, desired = number })
}
variable "aurora_clusters" {
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
variable "redis_node_type" { type = string }
variable "redis_shards" { type = number }
variable "redis_replicas" { type = number }
variable "msk_instance_type" { type = string }
variable "msk_brokers_per_az" { type = number }
variable "msk_ebs_gb" { type = number }
variable "waf_rate_limit" { type = number }
variable "blocked_countries" {
  type    = list(string)
  default = []
}
variable "eks_public_cidrs" {
  type    = list(string)
  default = []
}
