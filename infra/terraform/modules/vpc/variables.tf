variable "name" { type = string }
variable "cidr" { type = string }
variable "azs" { type = list(string) }
variable "single_nat_gateway" {
  type    = bool
  default = false
}
variable "flow_log_retention_days" {
  type    = number
  default = 90
}
variable "tags" {
  type    = map(string)
  default = {}
}
