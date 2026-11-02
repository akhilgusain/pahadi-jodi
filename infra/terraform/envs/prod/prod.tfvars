env                       = "prod"
vpc_cidr                  = "10.30.0.0/16"
single_nat_gateway        = false # one NAT per AZ
domain                    = "pahadijodi.in"
media_domain              = "media.pahadijodi.in"
media_acm_certificate_arn = "arn:aws:acm:us-east-1:REPLACE:certificate/REPLACE"
cloudfront_public_key_pem = "-----BEGIN PUBLIC KEY-----\nREPLACE\n-----END PUBLIC KEY-----"
system_node_group         = { instance_types = ["m7g.xlarge"], min = 3, max = 6, desired = 3 }
aurora_clusters = {
  tier0 = { instance_class = "db.r7g.2xlarge", instance_count = 3, backup_days = 35, deletion_protection = true, databases = ["identity", "subscription", "payment"] }
  tier1 = { instance_class = "db.r7g.2xlarge", instance_count = 3, backup_days = 14, deletion_protection = true, databases = ["profile", "user", "match", "chat", "media", "notification"] }
  tier2 = { instance_class = "db.r7g.xlarge", instance_count = 2, backup_days = 7, deletion_protection = true, databases = ["analytics", "admin", "moderation", "fraud", "horoscope"] }
}
redis_node_type    = "cache.r7g.xlarge"
redis_shards       = 4
redis_replicas     = 2
msk_instance_type  = "kafka.m7g.xlarge"
msk_brokers_per_az = 2
msk_ebs_gb         = 1000
waf_rate_limit     = 2000
eks_public_cidrs   = []
