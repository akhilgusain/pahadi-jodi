env                       = "staging"
vpc_cidr                  = "10.20.0.0/16"
single_nat_gateway        = true
domain                    = "stg.pahadijodi.in"
media_domain              = "media.stg.pahadijodi.in"
media_acm_certificate_arn = "arn:aws:acm:us-east-1:REPLACE:certificate/REPLACE"
cloudfront_public_key_pem = "-----BEGIN PUBLIC KEY-----\nREPLACE\n-----END PUBLIC KEY-----"
system_node_group         = { instance_types = ["m7g.large"], min = 3, max = 4, desired = 3 }
aurora_clusters = {
  tier0 = { instance_class = "db.r7g.large", instance_count = 2, backup_days = 7, deletion_protection = true, databases = ["identity", "subscription", "payment"] }
  tier1 = { instance_class = "db.r7g.large", instance_count = 2, backup_days = 7, deletion_protection = true, databases = ["profile", "user", "match", "chat", "media", "notification"] }
  tier2 = { instance_class = "db.serverless", instance_count = 1, serverless_min_acu = 0.5, serverless_max_acu = 8, backup_days = 3, deletion_protection = false, databases = ["analytics", "admin", "moderation", "fraud", "horoscope"] }
}
redis_node_type    = "cache.r7g.large"
redis_shards       = 2
redis_replicas     = 1
msk_instance_type  = "kafka.m7g.large"
msk_brokers_per_az = 1
msk_ebs_gb         = 200
waf_rate_limit     = 2000
eks_public_cidrs   = []
