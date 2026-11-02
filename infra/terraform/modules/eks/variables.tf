variable "cluster_name" { type = string }
variable "cluster_version" {
  type    = string
  default = "1.31"
}
variable "vpc_id" { type = string }
variable "private_subnet_ids" { type = list(string) }
variable "kms_key_arn" { type = string }
variable "public_endpoint_cidrs" {
  type    = list(string)
  default = []
}
variable "system_node_group" {
  type = object({
    instance_types = list(string)
    min            = number
    max            = number
    desired        = number
  })
  default = {
    instance_types = ["m7g.large"]
    min            = 3
    max            = 6
    desired        = 3
  }
}
variable "karpenter_version" {
  type    = string
  default = "1.0.8"
}
variable "tags" {
  type    = map(string)
  default = {}
}
