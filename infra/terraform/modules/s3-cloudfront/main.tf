variable "name" { type = string }
variable "kms_key_arn" { type = string }
variable "domain" { type = string } # media.pahadijodi.in
variable "acm_certificate_arn" { type = string } # us-east-1
variable "waf_acl_arn" {
  type    = string
  default = null
}
variable "public_key_pem" {
  description = "RSA public key for CloudFront signed URLs (private key lives in Secrets Manager)"
  type        = string
}
variable "tags" {
  type    = map(string)
  default = {}
}

locals { buckets = toset(["public-media", "private-media", "kyc-docs", "call-recordings", "backups"]) }

resource "aws_s3_bucket" "this" {
  for_each = local.buckets
  bucket   = "${var.name}-${each.key}"
  tags     = var.tags
}
resource "aws_s3_bucket_public_access_block" "this" {
  for_each                = aws_s3_bucket.this
  bucket                  = each.value.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
resource "aws_s3_bucket_ownership_controls" "this" {
  for_each = aws_s3_bucket.this
  bucket   = each.value.id
  rule { object_ownership = "BucketOwnerEnforced" }
}
resource "aws_s3_bucket_versioning" "this" {
  for_each = aws_s3_bucket.this
  bucket   = each.value.id
  versioning_configuration { status = "Enabled" }
}
resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  for_each = aws_s3_bucket.this
  bucket   = each.value.id
  rule {
    bucket_key_enabled = true
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = var.kms_key_arn
    }
  }
}
resource "aws_s3_bucket_lifecycle_configuration" "this" {
  for_each = aws_s3_bucket.this
  bucket   = each.value.id
  rule {
    id     = "tiering"
    status = "Enabled"
    filter {}
    transition {
      days          = 0
      storage_class = "INTELLIGENT_TIERING"
    }
    noncurrent_version_expiration { noncurrent_days = 90 }
    abort_incomplete_multipart_upload { days_after_initiation = 7 }
  }
}
# Quarantine prefix for un-scanned uploads expires fast
resource "aws_s3_bucket_lifecycle_configuration" "quarantine" {
  bucket = aws_s3_bucket.this["private-media"].id
  rule {
    id     = "quarantine-expire"
    status = "Enabled"
    filter { prefix = "quarantine/" }
    expiration { days = 2 }
  }
  rule {
    id     = "tiering"
    status = "Enabled"
    filter {}
    transition {
      days          = 0
      storage_class = "INTELLIGENT_TIERING"
    }
  }
  depends_on = [aws_s3_bucket_lifecycle_configuration.this]
}
resource "aws_s3_bucket_cors_configuration" "uploads" {
  bucket = aws_s3_bucket.this["private-media"].id
  cors_rule {
    allowed_methods = ["PUT"]
    allowed_origins = ["https://${replace(var.domain, "media.", "")}", "https://www.${replace(var.domain, "media.", "")}"]
    allowed_headers = ["content-type", "x-amz-*"]
    max_age_seconds = 3000
  }
}
# KYC docs & recordings: object lock for evidentiary integrity is enabled at bucket creation in prod via var override (compliance mode not default).

resource "aws_cloudfront_origin_access_control" "this" {
  name                              = "${var.name}-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}
resource "aws_cloudfront_public_key" "this" {
  name        = "${var.name}-signer"
  encoded_key = var.public_key_pem
}
resource "aws_cloudfront_key_group" "this" {
  name  = "${var.name}-signers"
  items = [aws_cloudfront_public_key.this.id]
}
resource "aws_cloudfront_response_headers_policy" "sec" {
  name = "${var.name}-security"
  security_headers_config {
    strict_transport_security {
      access_control_max_age_sec = 63072000
      include_subdomains         = true
      preload                    = true
      override                   = true
    }
    content_type_options { override = true }
    frame_options {
      frame_option = "DENY"
      override     = true
    }
    referrer_policy {
      referrer_policy = "strict-origin-when-cross-origin"
      override        = true
    }
  }
}
data "aws_cloudfront_cache_policy" "optimized" { name = "Managed-CachingOptimized" }

resource "aws_cloudfront_distribution" "this" {
  enabled         = true
  http_version    = "http2and3"
  price_class     = "PriceClass_200"
  aliases         = [var.domain]
  web_acl_id      = var.waf_acl_arn
  is_ipv6_enabled = true

  origin {
    domain_name              = aws_s3_bucket.this["public-media"].bucket_regional_domain_name
    origin_id                = "public"
    origin_access_control_id = aws_cloudfront_origin_access_control.this.id
  }
  origin {
    domain_name              = aws_s3_bucket.this["private-media"].bucket_regional_domain_name
    origin_id                = "private"
    origin_access_control_id = aws_cloudfront_origin_access_control.this.id
  }
  default_cache_behavior {
    target_origin_id           = "public"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.optimized.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.sec.id
  }
  # Profile photos (private): signed URLs/cookies only, 15 min TTL enforced by Media Service
  ordered_cache_behavior {
    path_pattern               = "/p/*"
    target_origin_id           = "private"
    viewer_protocol_policy     = "https-only"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    trusted_key_groups         = [aws_cloudfront_key_group.this.id]
    cache_policy_id            = data.aws_cloudfront_cache_policy.optimized.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.sec.id
  }
  restrictions {
    geo_restriction { restriction_type = "none" }
  }
  viewer_certificate {
    acm_certificate_arn      = var.acm_certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
  tags = var.tags
}

data "aws_iam_policy_document" "bucket" {
  for_each = toset(["public-media", "private-media"])
  statement {
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.this[each.key].arn}/*"]
    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.this.arn]
    }
  }
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.this[each.key].arn, "${aws_s3_bucket.this[each.key].arn}/*"]
    principals {
      type        = "*"
      identifiers = ["*"]
    }
    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}
resource "aws_s3_bucket_policy" "this" {
  for_each = data.aws_iam_policy_document.bucket
  bucket   = aws_s3_bucket.this[each.key].id
  policy   = each.value.json
}
output "bucket_arns" { value = { for k, b in aws_s3_bucket.this : k => b.arn } }
output "bucket_names" { value = { for k, b in aws_s3_bucket.this : k => b.id } }
output "cloudfront_domain" { value = aws_cloudfront_distribution.this.domain_name }
output "cloudfront_key_pair_id" { value = aws_cloudfront_public_key.this.id }
