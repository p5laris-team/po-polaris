output "vpc_id" {
  description = "VPC ID."
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs."
  value       = values(aws_subnet.public)[*].id
}

output "private_subnet_ids" {
  description = "Private subnet IDs."
  value       = values(aws_subnet.private)[*].id
}

output "public_route_table_id" {
  description = "Public route table ID."
  value       = aws_route_table.public.id
}

output "private_route_table_id" {
  description = "Private route table ID."
  value       = aws_route_table.private.id
}

output "n8n_instance_id" {
  description = "n8n EC2 instance ID."
  value       = aws_instance.n8n.id
}

output "n8n_public_ip" {
  description = "n8n Elastic public IPv4 address."
  value       = aws_eip.n8n.public_ip
}

output "n8n_ssh_command" {
  description = "SSH command for n8n EC2."
  value       = "ssh -i ~/.ssh/p5laris-n8n ubuntu@${aws_eip.n8n.public_ip}"
}

output "rds_endpoint" {
  description = "RDS PostgreSQL endpoint. Null when RDS is disabled."
  value       = var.enable_rds ? aws_db_instance.postgres[0].address : null
}

output "rds_port" {
  description = "RDS PostgreSQL port. Null when RDS is disabled."
  value       = var.enable_rds ? aws_db_instance.postgres[0].port : null
}

output "rds_master_user_secret_arn" {
  description = "Secrets Manager ARN for the RDS managed master user password. Null when RDS is disabled."
  value       = var.enable_rds ? aws_db_instance.postgres[0].master_user_secret[0].secret_arn : null
}

output "eks_cluster_name" {
  description = "EKS cluster name. Null when EKS is disabled."
  value       = var.enable_eks ? aws_eks_cluster.main[0].name : null
}

output "eks_cluster_endpoint" {
  description = "EKS cluster endpoint. Null when EKS is disabled."
  value       = var.enable_eks ? aws_eks_cluster.main[0].endpoint : null
}

output "eks_update_kubeconfig_command" {
  description = "Command to configure kubectl for EKS. Null when EKS is disabled."
  value       = var.enable_eks ? "AWS_PROFILE=${var.aws_profile} aws eks update-kubeconfig --region ${var.aws_region} --name ${aws_eks_cluster.main[0].name}" : null
}

output "eks_node_group_name" {
  description = "EKS managed node group name. Null when EKS is disabled."
  value       = var.enable_eks ? aws_eks_node_group.main[0].node_group_name : null
}

output "redis_endpoint" {
  description = "ElastiCache Redis endpoint. Null when Redis is disabled."
  value       = var.enable_redis ? aws_elasticache_cluster.redis[0].cache_nodes[0].address : null
}

output "redis_port" {
  description = "ElastiCache Redis port. Null when Redis is disabled."
  value       = var.enable_redis ? aws_elasticache_cluster.redis[0].port : null
}

output "msk_cluster_arn" {
  description = "Amazon MSK cluster ARN. Null when MSK is disabled."
  value       = var.enable_msk ? aws_msk_cluster.main[0].arn : null
}

output "msk_cluster_name" {
  description = "Amazon MSK cluster name. Null when MSK is disabled."
  value       = var.enable_msk ? aws_msk_cluster.main[0].cluster_name : null
}

output "msk_bootstrap_brokers" {
  description = "Amazon MSK plaintext bootstrap brokers. Null when MSK is disabled or plaintext is not enabled."
  value       = var.enable_msk ? aws_msk_cluster.main[0].bootstrap_brokers : null
}

output "msk_bootstrap_brokers_tls" {
  description = "Amazon MSK TLS bootstrap brokers. Null when MSK is disabled or TLS is not enabled."
  value       = var.enable_msk ? aws_msk_cluster.main[0].bootstrap_brokers_tls : null
}

output "msk_security_group_id" {
  description = "Amazon MSK security group ID. Null when MSK is disabled."
  value       = var.enable_msk ? aws_security_group.msk[0].id : null
}
