resource "aws_db_subnet_group" "main" {
  count = var.enable_rds ? 1 : 0

  name       = "${local.name_prefix}-db-subnet-group"
  subnet_ids = values(aws_subnet.private)[*].id

  tags = {
    Name = "${local.name_prefix}-db-subnet-group"
  }
}

resource "aws_security_group" "rds" {
  count = var.enable_rds ? 1 : 0

  name        = "${local.name_prefix}-rds-sg"
  description = "Security group for p5laris disposable RDS PostgreSQL."
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-rds-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "rds_postgres_from_vpc" {
  count = var.enable_rds ? 1 : 0

  security_group_id = aws_security_group.rds[0].id
  description       = "PostgreSQL from p5laris VPC"
  cidr_ipv4         = var.vpc_cidr
  from_port         = 5432
  to_port           = 5432
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "rds_all_egress" {
  count = var.enable_rds ? 1 : 0

  security_group_id = aws_security_group.rds[0].id
  description       = "All outbound traffic"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_db_instance" "postgres" {
  count = var.enable_rds ? 1 : 0

  identifier = "${local.name_prefix}-postgres"

  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = "postgres"
  username = "p5laris_admin"

  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main[0].name
  vpc_security_group_ids = [aws_security_group.rds[0].id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period  = 0
  deletion_protection      = false
  skip_final_snapshot      = true
  delete_automated_backups = true

  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true

  tags = {
    Name = "${local.name_prefix}-postgres"
    Role = "database"
  }
}
