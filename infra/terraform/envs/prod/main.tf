provider "aws" {
  region = var.region
  default_tags {
    tags = { Project = "pahadi-jodi", Env = var.env, ManagedBy = "terraform" }
  }
}
data "aws_availability_zones" "az" { state = "available" }
locals {
  name = "pj-${var.env}"
  azs  = slice(data.aws_availability_zones.az.names, 0, 3)
}

module "kms" {
  source  = "../../modules/kms-secrets"
  name    = local.name
  secrets = ["oauth/google", "oauth/facebook", "oauth/apple", "payments/razorpay", "payments/stripe", "kyc/digilocker", "kyc/pan", "notify/twilio", "notify/whatsapp", "ai/azure-openai", "jwt/signing"]
}

module "vpc" {
  source             = "../../modules/vpc"
  name               = local.name
  cidr               = var.vpc_cidr
  azs                = local.azs
  single_nat_gateway = var.single_nat_gateway
}

module "eks" {
  source                = "../../modules/eks"
  cluster_name          = local.name
  cluster_version       = var.eks_version
  vpc_id                = module.vpc.vpc_id
  private_subnet_ids    = module.vpc.private_subnet_ids
  kms_key_arn           = module.kms.key_arns["eks"]
  system_node_group     = var.system_node_group
  public_endpoint_cidrs = var.eks_public_cidrs
}

provider "helm" {
  kubernetes {
    host                   = module.eks.cluster_endpoint
    cluster_ca_certificate = base64decode(module.eks.cluster_ca)
    exec {
      api_version = "client.authentication.k8s.io/v1beta1"
      command     = "aws"
      args        = ["eks", "get-token", "--cluster-name", module.eks.cluster_name]
    }
  }
}

module "aurora" {
  source                     = "../../modules/aurora"
  name                       = local.name
  vpc_id                     = module.vpc.vpc_id
  data_subnet_ids            = module.vpc.data_subnet_ids
  allowed_security_group_ids = [module.eks.node_security_group_id]
  kms_key_arn                = module.kms.key_arns["rds"]
  secrets_kms_key_arn        = module.kms.key_arns["secrets"]
  clusters                   = var.aurora_clusters
}

module "redis" {
  source                     = "../../modules/elasticache"
  name                       = local.name
  vpc_id                     = module.vpc.vpc_id
  data_subnet_ids            = module.vpc.data_subnet_ids
  allowed_security_group_ids = [module.eks.node_security_group_id]
  kms_key_arn                = module.kms.key_arns["redis"]
  node_type                  = var.redis_node_type
  shards                     = var.redis_shards
  replicas_per_shard         = var.redis_replicas
}

module "msk" {
  source                     = "../../modules/msk"
  name                       = local.name
  vpc_id                     = module.vpc.vpc_id
  data_subnet_ids            = module.vpc.data_subnet_ids
  allowed_security_group_ids = [module.eks.node_security_group_id]
  kms_key_arn                = module.kms.key_arns["msk"]
  broker_instance_type       = var.msk_instance_type
  brokers_per_az             = var.msk_brokers_per_az
  ebs_gb                     = var.msk_ebs_gb
  tiered_storage             = var.env == "prod"
}

module "waf_regional" {
  source            = "../../modules/waf"
  name              = "${local.name}-alb"
  rate_limit_5min   = var.waf_rate_limit
  blocked_countries = var.blocked_countries
}

module "media" {
  source              = "../../modules/s3-cloudfront"
  name                = local.name
  kms_key_arn         = module.kms.key_arns["s3"]
  domain              = var.media_domain
  acm_certificate_arn = var.media_acm_certificate_arn
  public_key_pem      = var.cloudfront_public_key_pem
}

module "observability" {
  source                 = "../../modules/observability"
  name                   = local.name
  oidc_provider_arn      = module.eks.oidc_provider_arn
  oidc_host              = module.eks.oidc_host
  kms_key_arn            = module.kms.key_arns["logs"]
  enable_managed_grafana = var.env != "dev"
}

# ---- Per-service IRSA (least privilege)
locals {
  irsa = {
    media-service = jsonencode({ Version = "2012-10-17", Statement = [
      { Effect = "Allow", Action = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"], Resource = [for b in ["private-media", "public-media", "kyc-docs"] : "${module.media.bucket_arns[b]}/*"] },
      { Effect = "Allow", Action = ["rekognition:CompareFaces", "rekognition:DetectFaces", "rekognition:DetectModerationLabels", "rekognition:CreateFaceLivenessSession", "rekognition:GetFaceLivenessSessionResults"], Resource = "*" },
      { Effect = "Allow", Action = ["kms:Decrypt", "kms:GenerateDataKey"], Resource = module.kms.key_arns["s3"] }] })
    chat-service = jsonencode({ Version = "2012-10-17", Statement = [
      { Effect = "Allow", Action = ["s3:PutObject", "s3:GetObject"], Resource = "${module.media.bucket_arns["private-media"]}/chat/*" },
      { Effect = "Allow", Action = ["kafka-cluster:Connect", "kafka-cluster:*Topic*", "kafka-cluster:ReadData", "kafka-cluster:WriteData", "kafka-cluster:AlterGroup", "kafka-cluster:DescribeGroup"], Resource = "*" }] })
    notification-service = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Action = ["ses:SendEmail", "ses:SendRawEmail", "sns:Publish"], Resource = "*" }] })
    default-kafka = jsonencode({ Version = "2012-10-17", Statement = [{ Effect = "Allow", Action = ["kafka-cluster:Connect", "kafka-cluster:*Topic*", "kafka-cluster:ReadData", "kafka-cluster:WriteData", "kafka-cluster:AlterGroup", "kafka-cluster:DescribeGroup"], Resource = "*" }] })
  }
}
module "irsa" {
  for_each          = local.irsa
  source            = "../../modules/irsa"
  name              = "${local.name}-${each.key}"
  namespace         = "pj-${each.key == "default-kafka" ? "core" : "apps"}"
  service_account   = each.key
  oidc_provider_arn = module.eks.oidc_provider_arn
  oidc_host         = module.eks.oidc_host
  policy_json       = each.value
}

output "cluster_name" { value = module.eks.cluster_name }
output "aurora_proxy_endpoints" { value = module.aurora.proxy_endpoints }
output "redis_endpoint" { value = module.redis.configuration_endpoint }
output "kafka_bootstrap" { value = module.msk.bootstrap_brokers_iam }
output "media_cdn" { value = module.media.cloudfront_domain }
output "amp_remote_write" { value = module.observability.amp_remote_write_url }
