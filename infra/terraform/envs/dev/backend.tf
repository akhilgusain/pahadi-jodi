terraform {
  backend "s3" {
    bucket         = "pj-tfstate-REPLACE_ACCOUNT_ID"
    key            = "pahadi-jodi/dev/terraform.tfstate"
    region         = "ap-south-1"
    dynamodb_table = "pj-tflock"
    encrypt        = true
  }
}
