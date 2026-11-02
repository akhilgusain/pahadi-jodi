env                       = "dev"
vpc_cidr                  = "10.10.0.0/16"
single_nat_gateway        = true
domain                    = "dev.pahadijodi.in"
media_domain              = "media.dev.pahadijodi.in"
media_acm_certificate_arn = "arn:aws:acm:us-east-1:REPLACE:certificate/REPLACE"
cloudfront_public_key_pem = "-----BEGIN PUBLIC KEY-----\nREPLACE\n-----END PUBLIC KEY-----"
system_node_group         = { instance_types = ["t4g.large"], min = 2, max = 3, desired = 2 }
aurora_clusters = {
  core = { instance_class = "db.serverless", instance_count = 1, serverless_min_acu = 0.5, serverless_max_acu = 4, backup_days = 3, deletion_protection = false, databases = ["identity", "user", "profile", "subscription", "payment", "chat", "media", "notification"] }
}
redis_node_type    = "cache.t4g.medium"
redis_shards       = 1
redis_replicas     = 1
msk_instance_type  = "kafka.t3.small"
msk_brokers_per_az = 1
msk_ebs_gb         = 100
waf_rate_limit     = 5000
eks_public_cidrs   = ["0.0.0.0/0"] # restrict to office/VPN CIDRs
