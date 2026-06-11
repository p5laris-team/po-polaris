variable "aws_region" {
  description = "AWS region for p5laris infrastructure."
  type        = string
  default     = "ap-northeast-2"
}

variable "aws_profile" {
  description = "Local AWS CLI profile name used by Terraform. Set null to use default AWS credential resolution."
  type        = string
  default     = null
}

variable "project" {
  description = "Project name."
  type        = string
  default     = "p5laris"
}

variable "environment" {
  description = "Environment name."
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.50.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones for the dev VPC."
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets."
  type        = list(string)
  default     = ["10.50.0.0/24", "10.50.1.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets."
  type        = list(string)
  default     = ["10.50.10.0/24", "10.50.11.0/24"]
}

variable "admin_cidr_blocks" {
  description = "CIDR blocks allowed to SSH into admin-managed instances."
  type        = list(string)
}

variable "n8n_public_key_path" {
  description = "Local public key path for the n8n EC2 key pair."
  type        = string
}

variable "n8n_instance_type" {
  description = "EC2 instance type for n8n."
  type        = string
  default     = "t4g.small"
}

variable "n8n_root_volume_size" {
  description = "Root EBS volume size in GiB for n8n EC2."
  type        = number
  default     = 20
}

variable "db_instance_class" {
  description = "RDS instance class for p5laris PostgreSQL."
  type        = string
  default     = "db.t4g.micro"
}

variable "db_engine_version" {
  description = "PostgreSQL engine version for RDS."
  type        = string
  default     = "16"
}

variable "db_allocated_storage" {
  description = "Allocated storage in GiB for RDS."
  type        = number
  default     = 20
}

variable "enable_rds" {
  description = "Whether to create disposable RDS PostgreSQL for p5laris dev."
  type        = bool
  default     = false
}

variable "enable_eks" {
  description = "Whether to create disposable EKS cluster for p5laris dev."
  type        = bool
  default     = false
}

variable "eks_cluster_version" {
  description = "EKS Kubernetes version. Null means AWS default."
  type        = string
  default     = null
}

variable "eks_node_instance_types" {
  description = "EKS managed node group instance types."
  type        = list(string)
  default     = ["t4g.small"]
}

variable "eks_node_ami_type" {
  description = "AMI type for EKS managed node group."
  type        = string
  default     = "AL2023_ARM_64_STANDARD"
}

variable "eks_node_min_size" {
  description = "Minimum number of EKS worker nodes."
  type        = number
  default     = 0
}

variable "eks_node_desired_size" {
  description = "Desired number of EKS worker nodes when EKS is enabled."
  type        = number
  default     = 1
}

variable "eks_node_max_size" {
  description = "Maximum number of EKS worker nodes."
  type        = number
  default     = 2
}

variable "enable_redis" {
  description = "Whether to create disposable ElastiCache Redis for p5laris dev."
  type        = bool
  default     = false
}

variable "redis_node_type" {
  description = "ElastiCache Redis node type."
  type        = string
  default     = "cache.t4g.micro"
}

variable "redis_engine_version" {
  description = "ElastiCache Redis engine version."
  type        = string
  default     = "7.1"
}

variable "redis_port" {
  description = "ElastiCache Redis port."
  type        = number
  default     = 6379
}

variable "enable_msk" {
  description = "Whether to create disposable Amazon MSK cluster for p5laris dev."
  type        = bool
  default     = false
}

variable "msk_kafka_version" {
  description = "Apache Kafka version for Amazon MSK."
  type        = string
  default     = "3.6.0"
}

variable "msk_broker_instance_type" {
  description = "MSK broker instance type."
  type        = string
  default     = "kafka.t3.small"
}

variable "msk_broker_count" {
  description = "Total number of MSK broker nodes. Must be a multiple of the number of selected AZs."
  type        = number
  default     = 2
}

variable "msk_ebs_volume_size" {
  description = "EBS volume size per MSK broker in GiB."
  type        = number
  default     = 1
}

variable "msk_client_broker_encryption" {
  description = "MSK client broker encryption mode. For dev minimum-cost/simple access, use PLAINTEXT inside private VPC."
  type        = string
  default     = "PLAINTEXT"
}
