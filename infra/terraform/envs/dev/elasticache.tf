resource "aws_elasticache_subnet_group" "redis" {
  count = var.enable_redis ? 1 : 0

  name       = "${local.name_prefix}-redis-subnet-group"
  subnet_ids = values(aws_subnet.private)[*].id

  tags = {
    Name = "${local.name_prefix}-redis-subnet-group"
    Role = "redis"
  }
}

resource "aws_security_group" "redis" {
  count = var.enable_redis ? 1 : 0

  name        = "${local.name_prefix}-redis-sg"
  description = "Security group for p5laris disposable ElastiCache Redis."
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-redis-sg"
    Role = "redis"
  }
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_vpc" {
  count = var.enable_redis ? 1 : 0

  security_group_id = aws_security_group.redis[0].id
  description       = "Redis from p5laris VPC"
  cidr_ipv4         = var.vpc_cidr
  from_port         = var.redis_port
  to_port           = var.redis_port
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "redis_all_egress" {
  count = var.enable_redis ? 1 : 0

  security_group_id = aws_security_group.redis[0].id
  description       = "All outbound traffic"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_elasticache_cluster" "redis" {
  count = var.enable_redis ? 1 : 0

  cluster_id           = "${local.name_prefix}-redis"
  engine               = "redis"
  engine_version       = var.redis_engine_version
  node_type            = var.redis_node_type
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  port                 = var.redis_port

  subnet_group_name  = aws_elasticache_subnet_group.redis[0].name
  security_group_ids = [aws_security_group.redis[0].id]

  apply_immediately = true

  tags = {
    Name = "${local.name_prefix}-redis"
    Role = "redis"
  }
}
