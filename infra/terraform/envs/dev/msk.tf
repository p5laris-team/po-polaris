resource "aws_security_group" "msk" {
  count = var.enable_msk ? 1 : 0

  name        = "${local.name_prefix}-msk-sg"
  description = "Security group for p5laris dev Amazon MSK."
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-msk-sg"
    Role = "msk"
  }
}

resource "aws_vpc_security_group_ingress_rule" "msk_plaintext_from_vpc" {
  count = var.enable_msk && var.msk_client_broker_encryption == "PLAINTEXT" ? 1 : 0

  security_group_id = aws_security_group.msk[0].id
  description       = "Allow Kafka plaintext clients from p5laris dev VPC."
  cidr_ipv4         = var.vpc_cidr
  ip_protocol       = "tcp"
  from_port         = 9092
  to_port           = 9092
}

resource "aws_vpc_security_group_ingress_rule" "msk_tls_from_vpc" {
  count = var.enable_msk && var.msk_client_broker_encryption != "PLAINTEXT" ? 1 : 0

  security_group_id = aws_security_group.msk[0].id
  description       = "Allow Kafka TLS clients from p5laris dev VPC."
  cidr_ipv4         = var.vpc_cidr
  ip_protocol       = "tcp"
  from_port         = 9094
  to_port           = 9094
}

resource "aws_vpc_security_group_egress_rule" "msk_all_egress" {
  count = var.enable_msk ? 1 : 0

  security_group_id = aws_security_group.msk[0].id
  description       = "Allow all egress from MSK security group."
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_msk_cluster" "main" {
  count = var.enable_msk ? 1 : 0

  cluster_name           = "${local.name_prefix}-msk"
  kafka_version          = var.msk_kafka_version
  number_of_broker_nodes = var.msk_broker_count
  enhanced_monitoring    = "DEFAULT"

  broker_node_group_info {
    instance_type   = var.msk_broker_instance_type
    client_subnets  = [for subnet in aws_subnet.private : subnet.id]
    security_groups = [aws_security_group.msk[0].id]

    storage_info {
      ebs_storage_info {
        volume_size = var.msk_ebs_volume_size
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = var.msk_client_broker_encryption
      in_cluster    = true
    }
  }

  tags = {
    Name = "${local.name_prefix}-msk"
    Role = "msk"
  }
}
