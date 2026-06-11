data "aws_ssm_parameter" "ubuntu_2404_arm64_ami" {
  name = "/aws/service/canonical/ubuntu/server/24.04/stable/current/arm64/hvm/ebs-gp3/ami-id"
}

resource "aws_key_pair" "n8n" {
  key_name   = "${local.name_prefix}-n8n-key"
  public_key = file(var.n8n_public_key_path)

  tags = {
    Name = "${local.name_prefix}-n8n-key"
  }
}

resource "aws_security_group" "n8n" {
  name        = "${local.name_prefix}-n8n-sg"
  description = "Security group for n8n EC2."
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-n8n-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "n8n_ssh" {
  for_each = toset(var.admin_cidr_blocks)

  security_group_id = aws_security_group.n8n.id
  description       = "SSH from admin IP"
  cidr_ipv4         = each.value
  from_port         = 22
  to_port           = 22
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "n8n_http" {
  security_group_id = aws_security_group.n8n.id
  description       = "HTTP"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "n8n_https" {
  security_group_id = aws_security_group.n8n.id
  description       = "HTTPS"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "n8n_all_egress" {
  security_group_id = aws_security_group.n8n.id
  description       = "All outbound traffic"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_instance" "n8n" {
  ami                         = data.aws_ssm_parameter.ubuntu_2404_arm64_ami.value
  instance_type               = var.n8n_instance_type
  subnet_id                   = values(aws_subnet.public)[0].id
  vpc_security_group_ids      = [aws_security_group.n8n.id]
  key_name                    = aws_key_pair.n8n.key_name
  associate_public_ip_address = true

  user_data_replace_on_change = true

  user_data = <<-EOF_USER_DATA
    #!/usr/bin/env bash
    set -euxo pipefail

    apt-get update
    apt-get install -y ca-certificates curl gnupg

    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc

    . /etc/os-release
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $${VERSION_CODENAME} stable" > /etc/apt/sources.list.d/docker.list

    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    usermod -aG docker ubuntu
    systemctl enable --now docker

    mkdir -p /opt/n8n
    chown ubuntu:ubuntu /opt/n8n
  EOF_USER_DATA

  lifecycle {
    prevent_destroy = true
    ignore_changes = [
      ami,
      user_data,
      user_data_replace_on_change
    ]
  }

  root_block_device {
    volume_size = var.n8n_root_volume_size
    volume_type = "gp3"
    encrypted   = true
  }

  metadata_options {
    http_tokens = "required"
  }

  tags = {
    Name = "${local.name_prefix}-n8n"
    Role = "n8n"
  }
}

resource "aws_eip" "n8n" {
  domain   = "vpc"
  instance = aws_instance.n8n.id

  tags = {
    Name = "${local.name_prefix}-n8n-eip"
    Role = "n8n"
  }
}
