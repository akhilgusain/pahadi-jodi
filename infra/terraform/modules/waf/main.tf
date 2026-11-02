variable "name" { type = string }
variable "scope" { default = "REGIONAL" } # CLOUDFRONT requires us-east-1 provider
variable "rate_limit_5min" { default = 2000 }
variable "login_rate_limit_5min" { default = 100 }
variable "blocked_countries" {
  type    = list(string)
  default = []
}
variable "tags" {
  type    = map(string)
  default = {}
}

locals {
  managed = [
    { n = "AWSManagedRulesCommonRuleSet", p = 10, o = [] },
    { n = "AWSManagedRulesKnownBadInputsRuleSet", p = 20, o = [] },
    { n = "AWSManagedRulesSQLiRuleSet", p = 30, o = [] },
    { n = "AWSManagedRulesAmazonIpReputationList", p = 40, o = [] },
    { n = "AWSManagedRulesAnonymousIpList", p = 50, o = ["HostingProviderIPList"] },
    { n = "AWSManagedRulesLinuxRuleSet", p = 60, o = [] },
  ]
}
resource "aws_wafv2_web_acl" "this" {
  name  = var.name
  scope = var.scope
  default_action {
    allow {}
  }
  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = var.name
    sampled_requests_enabled   = true
  }

  dynamic "rule" {
    for_each = local.managed
    content {
      name     = rule.value.n
      priority = rule.value.p
      override_action {
        none {}
      }
      statement {
        managed_rule_group_statement {
          name        = rule.value.n
          vendor_name = "AWS"
          dynamic "rule_action_override" {
            for_each = rule.value.o
            content {
              name = rule_action_override.value
              action_to_use {
                count {}
              }
            }
          }
        }
      }
      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = rule.value.n
        sampled_requests_enabled   = true
      }
    }
  }

  rule {
    name     = "BotControl"
    priority = 70
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesBotControlRuleSet"
        vendor_name = "AWS"
        managed_rule_group_configs {
          aws_managed_rules_bot_control_rule_set { inspection_level = "COMMON" }
        }
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "BotControl"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "GlobalRateLimit"
    priority = 80
    action {
      block {}
    }
    statement {
      rate_based_statement {
        limit              = var.rate_limit_5min
        aggregate_key_type = "IP"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "GlobalRateLimit"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "AuthEndpointRateLimit"
    priority = 85
    action {
      block {}
    }
    statement {
      rate_based_statement {
        limit              = var.login_rate_limit_5min
        aggregate_key_type = "IP"
        scope_down_statement {
          byte_match_statement {
            search_string         = "/api/v1/auth/"
            positional_constraint = "STARTS_WITH"
            field_to_match {
              uri_path {}
            }
            text_transformation {
              priority = 0
              type     = "LOWERCASE"
            }
          }
        }
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "AuthRateLimit"
      sampled_requests_enabled   = true
    }
  }

  dynamic "rule" {
    for_each = length(var.blocked_countries) > 0 ? [1] : []
    content {
      name     = "GeoBlock"
      priority = 90
      action {
        block {}
      }
      statement {
        geo_match_statement { country_codes = var.blocked_countries }
      }
      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = "GeoBlock"
        sampled_requests_enabled   = true
      }
    }
  }
  tags = var.tags
}
output "acl_arn" { value = aws_wafv2_web_acl.this.arn }
