data "aws_iam_policy_document" "eks_cluster_assume_role" {
  count = var.enable_eks ? 1 : 0

  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["eks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "eks_cluster" {
  count = var.enable_eks ? 1 : 0

  name               = "${local.name_prefix}-eks-cluster-role"
  assume_role_policy = data.aws_iam_policy_document.eks_cluster_assume_role[0].json

  tags = {
    Name = "${local.name_prefix}-eks-cluster-role"
    Role = "eks-cluster"
  }
}

resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  count = var.enable_eks ? 1 : 0

  role       = aws_iam_role.eks_cluster[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

data "aws_iam_policy_document" "eks_node_assume_role" {
  count = var.enable_eks ? 1 : 0

  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "eks_node" {
  count = var.enable_eks ? 1 : 0

  name               = "${local.name_prefix}-eks-node-role"
  assume_role_policy = data.aws_iam_policy_document.eks_node_assume_role[0].json

  tags = {
    Name = "${local.name_prefix}-eks-node-role"
    Role = "eks-node"
  }
}

resource "aws_iam_role_policy_attachment" "eks_node_worker_policy" {
  count = var.enable_eks ? 1 : 0

  role       = aws_iam_role.eks_node[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

resource "aws_iam_role_policy_attachment" "eks_node_cni_policy" {
  count = var.enable_eks ? 1 : 0

  role       = aws_iam_role.eks_node[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}

resource "aws_iam_role_policy_attachment" "eks_node_ecr_readonly_policy" {
  count = var.enable_eks ? 1 : 0

  role       = aws_iam_role.eks_node[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_eks_cluster" "main" {
  count = var.enable_eks ? 1 : 0

  name     = "${local.name_prefix}-eks"
  role_arn = aws_iam_role.eks_cluster[0].arn
  version  = var.eks_cluster_version

  access_config {
    authentication_mode                         = "API_AND_CONFIG_MAP"
    bootstrap_cluster_creator_admin_permissions = true
  }

  vpc_config {
    subnet_ids = concat(
      values(aws_subnet.public)[*].id,
      values(aws_subnet.private)[*].id
    )

    endpoint_private_access = true
    endpoint_public_access  = true
    public_access_cidrs     = var.admin_cidr_blocks
  }

  tags = {
    Name = "${local.name_prefix}-eks"
    Role = "eks"
  }

  depends_on = [
    aws_iam_role_policy_attachment.eks_cluster_policy
  ]
}

resource "aws_eks_node_group" "main" {
  count = var.enable_eks ? 1 : 0

  cluster_name    = aws_eks_cluster.main[0].name
  node_group_name = "${local.name_prefix}-eks-node-group"
  node_role_arn   = aws_iam_role.eks_node[0].arn
  subnet_ids      = values(aws_subnet.public)[*].id

  ami_type       = var.eks_node_ami_type
  capacity_type  = "ON_DEMAND"
  instance_types = var.eks_node_instance_types
  disk_size      = 20

  scaling_config {
    min_size     = var.eks_node_min_size
    desired_size = var.eks_node_desired_size
    max_size     = var.eks_node_max_size
  }

  update_config {
    max_unavailable = 1
  }

  tags = {
    Name = "${local.name_prefix}-eks-node-group"
    Role = "eks-node"
  }

  depends_on = [
    aws_iam_role_policy_attachment.eks_node_worker_policy,
    aws_iam_role_policy_attachment.eks_node_cni_policy,
    aws_iam_role_policy_attachment.eks_node_ecr_readonly_policy
  ]

  lifecycle {
    ignore_changes = [
      scaling_config[0].desired_size
    ]
  }
}

resource "aws_eks_addon" "main" {
  for_each = var.enable_eks ? toset([
    "vpc-cni",
    "kube-proxy",
    "coredns"
  ]) : toset([])

  cluster_name = aws_eks_cluster.main[0].name
  addon_name   = each.key

  resolve_conflicts_on_create = "OVERWRITE"
  resolve_conflicts_on_update = "OVERWRITE"

  tags = {
    Name = "${local.name_prefix}-${each.key}"
    Role = "eks-addon"
  }

  depends_on = [
    aws_eks_node_group.main
  ]
}
