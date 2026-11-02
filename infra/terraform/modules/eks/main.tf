data "aws_partition" "current" {}
data "aws_caller_identity" "current" {}

data "aws_iam_policy_document" "cluster_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["eks.amazonaws.com"]
    }
  }
}
resource "aws_iam_role" "cluster" {
  name               = "${var.cluster_name}-cluster"
  assume_role_policy = data.aws_iam_policy_document.cluster_assume.json
}
resource "aws_iam_role_policy_attachment" "cluster" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEKSClusterPolicy"
}

resource "aws_security_group" "node" {
  name_prefix = "${var.cluster_name}-node-"
  vpc_id      = var.vpc_id
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = merge(var.tags, {
    Name                     = "${var.cluster_name}-node"
    "karpenter.sh/discovery" = var.cluster_name
  })
  lifecycle { create_before_destroy = true }
}
resource "aws_security_group_rule" "node_self" {
  type              = "ingress"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  self              = true
  security_group_id = aws_security_group.node.id
}

resource "aws_eks_cluster" "this" {
  name     = var.cluster_name
  version  = var.cluster_version
  role_arn = aws_iam_role.cluster.arn
  vpc_config {
    subnet_ids              = var.private_subnet_ids
    endpoint_private_access = true
    endpoint_public_access  = length(var.public_endpoint_cidrs) > 0
    public_access_cidrs     = length(var.public_endpoint_cidrs) > 0 ? var.public_endpoint_cidrs : null
    security_group_ids      = [aws_security_group.node.id]
  }
  access_config {
    authentication_mode                         = "API"
    bootstrap_cluster_creator_admin_permissions = true
  }
  encryption_config {
    resources = ["secrets"]
    provider { key_arn = var.kms_key_arn }
  }
  enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]
  tags                      = var.tags
  depends_on                = [aws_iam_role_policy_attachment.cluster]
}

# ---- IRSA OIDC provider
data "tls_certificate" "oidc" { url = aws_eks_cluster.this.identity[0].oidc[0].issuer }
resource "aws_iam_openid_connect_provider" "this" {
  url             = aws_eks_cluster.this.identity[0].oidc[0].issuer
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.oidc.certificates[0].sha1_fingerprint]
}
locals { oidc_host = replace(aws_iam_openid_connect_provider.this.url, "https://", "") }

# ---- System managed node group (runs Karpenter, CoreDNS, ArgoCD; tainted)
data "aws_iam_policy_document" "node_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}
resource "aws_iam_role" "node" {
  name               = "${var.cluster_name}-node"
  assume_role_policy = data.aws_iam_policy_document.node_assume.json
}
resource "aws_iam_role_policy_attachment" "node" {
  for_each = toset([
    "AmazonEKSWorkerNodePolicy", "AmazonEKS_CNI_Policy",
    "AmazonEC2ContainerRegistryReadOnly", "AmazonSSMManagedInstanceCore"
  ])
  role       = aws_iam_role.node.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/${each.key}"
}

resource "aws_eks_node_group" "system" {
  cluster_name    = aws_eks_cluster.this.name
  node_group_name = "system"
  node_role_arn   = aws_iam_role.node.arn
  subnet_ids      = var.private_subnet_ids
  instance_types  = var.system_node_group.instance_types
  ami_type        = "BOTTLEROCKET_ARM_64"
  capacity_type   = "ON_DEMAND"
  scaling_config {
    min_size     = var.system_node_group.min
    max_size     = var.system_node_group.max
    desired_size = var.system_node_group.desired
  }
  update_config { max_unavailable = 1 }
  labels = { "pj/pool" = "system" }
  taint {
    key    = "CriticalAddonsOnly"
    value  = "true"
    effect = "NO_SCHEDULE"
  }
  lifecycle { ignore_changes = [scaling_config[0].desired_size] }
  depends_on = [aws_iam_role_policy_attachment.node]
}

# ---- Add-ons
resource "aws_iam_role" "vpc_cni" {
  name = "${var.cluster_name}-vpc-cni"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRoleWithWebIdentity"
      Principal = { Federated = aws_iam_openid_connect_provider.this.arn }
      Condition = { StringEquals = { "${local.oidc_host}:sub" = "system:serviceaccount:kube-system:aws-node", "${local.oidc_host}:aud" = "sts.amazonaws.com" } }
    }]
  })
}
resource "aws_iam_role_policy_attachment" "vpc_cni" {
  role       = aws_iam_role.vpc_cni.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/AmazonEKS_CNI_Policy"
}
resource "aws_iam_role" "ebs_csi" {
  name = "${var.cluster_name}-ebs-csi"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRoleWithWebIdentity"
      Principal = { Federated = aws_iam_openid_connect_provider.this.arn }
      Condition = { StringEquals = { "${local.oidc_host}:sub" = "system:serviceaccount:kube-system:ebs-csi-controller-sa", "${local.oidc_host}:aud" = "sts.amazonaws.com" } }
    }]
  })
}
resource "aws_iam_role_policy_attachment" "ebs_csi" {
  role       = aws_iam_role.ebs_csi.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy"
}

resource "aws_eks_addon" "this" {
  for_each = {
    "vpc-cni"                = { role = aws_iam_role.vpc_cni.arn, config = jsonencode({ env = { ENABLE_PREFIX_DELEGATION = "true", WARM_PREFIX_TARGET = "1" }, enableNetworkPolicy = "true" }) }
    "coredns"                = { role = null, config = jsonencode({ tolerations = [{ key = "CriticalAddonsOnly", operator = "Exists" }] }) }
    "kube-proxy"             = { role = null, config = null }
    "aws-ebs-csi-driver"     = { role = aws_iam_role.ebs_csi.arn, config = null }
    "eks-pod-identity-agent" = { role = null, config = null }
  }
  cluster_name                = aws_eks_cluster.this.name
  addon_name                  = each.key
  service_account_role_arn    = each.value.role
  configuration_values        = each.value.config
  resolve_conflicts_on_update = "OVERWRITE"
  depends_on                  = [aws_eks_node_group.system]
}

# ---- Karpenter (controller IAM, interruption queue, node role access)
resource "aws_sqs_queue" "karpenter" {
  name                      = "${var.cluster_name}-karpenter"
  message_retention_seconds = 300
  sqs_managed_sse_enabled   = true
}
resource "aws_sqs_queue_policy" "karpenter" {
  queue_url = aws_sqs_queue.karpenter.url
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = ["events.amazonaws.com", "sqs.amazonaws.com"] }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.karpenter.arn
    }]
  })
}
resource "aws_cloudwatch_event_rule" "karpenter" {
  for_each = {
    spot   = { source = ["aws.ec2"], type = ["EC2 Spot Instance Interruption Warning"] }
    health = { source = ["aws.health"], type = ["AWS Health Event"] }
    state  = { source = ["aws.ec2"], type = ["EC2 Instance State-change Notification"] }
    rebal  = { source = ["aws.ec2"], type = ["EC2 Instance Rebalance Recommendation"] }
  }
  name          = "${var.cluster_name}-karpenter-${each.key}"
  event_pattern = jsonencode({ source = each.value.source, "detail-type" = each.value.type })
}
resource "aws_cloudwatch_event_target" "karpenter" {
  for_each = aws_cloudwatch_event_rule.karpenter
  rule     = each.value.name
  arn      = aws_sqs_queue.karpenter.arn
}

resource "aws_iam_role" "karpenter" {
  name = "${var.cluster_name}-karpenter-controller"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRoleWithWebIdentity"
      Principal = { Federated = aws_iam_openid_connect_provider.this.arn }
      Condition = { StringEquals = { "${local.oidc_host}:sub" = "system:serviceaccount:karpenter:karpenter", "${local.oidc_host}:aud" = "sts.amazonaws.com" } }
    }]
  })
}
resource "aws_iam_role_policy" "karpenter" {
  role = aws_iam_role.karpenter.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["ec2:RunInstances", "ec2:CreateFleet", "ec2:CreateLaunchTemplate", "ec2:CreateTags", "ec2:TerminateInstances", "ec2:DeleteLaunchTemplate"], Resource = "*",
      Condition = { StringEquals = { "aws:RequestTag/kubernetes.io/cluster/${var.cluster_name}" = "owned" } } },
      { Effect = "Allow", Action = ["ec2:Describe*", "pricing:GetProducts", "ssm:GetParameter", "eks:DescribeCluster"], Resource = "*" },
      { Effect = "Allow", Action = "iam:PassRole", Resource = aws_iam_role.node.arn },
      { Effect = "Allow", Action = ["iam:CreateInstanceProfile", "iam:AddRoleToInstanceProfile", "iam:RemoveRoleFromInstanceProfile", "iam:DeleteInstanceProfile", "iam:GetInstanceProfile", "iam:TagInstanceProfile"], Resource = "*" },
      { Effect = "Allow", Action = ["sqs:DeleteMessage", "sqs:GetQueueUrl", "sqs:ReceiveMessage"], Resource = aws_sqs_queue.karpenter.arn }
    ]
  })
}
resource "aws_eks_access_entry" "karpenter_node" {
  cluster_name  = aws_eks_cluster.this.name
  principal_arn = aws_iam_role.node.arn
  type          = "EC2_LINUX"
}

resource "helm_release" "karpenter" {
  namespace        = "karpenter"
  create_namespace = true
  name             = "karpenter"
  repository       = "oci://public.ecr.aws/karpenter"
  chart            = "karpenter"
  version          = var.karpenter_version
  values = [yamlencode({
    settings = { clusterName = aws_eks_cluster.this.name, interruptionQueue = aws_sqs_queue.karpenter.name }
    serviceAccount = { annotations = { "eks.amazonaws.com/role-arn" = aws_iam_role.karpenter.arn } }
    tolerations    = [{ key = "CriticalAddonsOnly", operator = "Exists" }]
    controller     = { resources = { requests = { cpu = "500m", memory = "512Mi" } } }
  })]
  depends_on = [aws_eks_node_group.system, aws_eks_addon.this]
}

# NodePool + EC2NodeClass delivered via GitOps (infra/k8s/base/karpenter); this module only exports names.

# ---- Reusable IRSA role factory output helpers
output "cluster_name" { value = aws_eks_cluster.this.name }
output "cluster_endpoint" { value = aws_eks_cluster.this.endpoint }
output "cluster_ca" { value = aws_eks_cluster.this.certificate_authority[0].data }
output "oidc_provider_arn" { value = aws_iam_openid_connect_provider.this.arn }
output "oidc_host" { value = local.oidc_host }
output "node_role_name" { value = aws_iam_role.node.name }
output "node_security_group_id" { value = aws_security_group.node.id }
