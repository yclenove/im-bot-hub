#!/bin/bash
# 在 WSL 中逐行执行这些命令

# 1. 更新系统
sudo apt-get update
sudo apt-get upgrade -y

# 2. 安装基础工具
sudo apt-get install -y curl wget gnupg2 software-properties-common

# 3. 安装 Redis
sudo apt-get install -y redis-server

# 4. 配置 Redis 允许外部访问
sudo sed -i 's/bind 127.0.0.1 ::1/bind 0.0.0.0/' /etc/redis/redis.conf
sudo sed -i 's/protected-mode yes/protected-mode no/' /etc/redis/redis.conf

# 5. 启动 Redis
sudo systemctl enable redis-server
sudo systemctl start redis-server

# 6. 验证 Redis
redis-cli ping

# 7. 安装 Prometheus
PROMETHEUS_VERSION="2.52.0"
sudo useradd --no-create-home --shell /bin/false prometheus 2>/dev/null || true
sudo mkdir -p /opt/prometheus /etc/prometheus /var/lib/prometheus

cd /tmp
wget -q https://github.com/prometheus/prometheus/releases/download/v${PROMETHEUS_VERSION}/prometheus-${PROMETHEUS_VERSION}.linux-amd64.tar.gz
tar xzf prometheus-${PROMETHEUS_VERSION}.linux-amd64.tar.gz

sudo cp prometheus-${PROMETHEUS_VERSION}.linux-amd64/prometheus /opt/prometheus/
sudo cp prometheus-${PROMETHEUS_VERSION}.linux-amd64/promtool /opt/prometheus/
sudo cp -r prometheus-${PROMETHEUS_VERSION}.linux-amd64/consoles /opt/prometheus/
sudo cp -r prometheus-${PROMETHEUS_VERSION}.linux-amd64/console_libraries /opt/prometheus/

sudo chown -R prometheus:prometheus /opt/prometheus /etc/prometheus /var/lib/prometheus

# 8. 创建 Prometheus 配置
sudo tee /etc/prometheus/prometheus.yml > /dev/null << 'EOF'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'im-bot-hub'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:18089']
        labels:
          app: 'im-bot-hub'
EOF

# 9. 创建 Prometheus systemd 服务
sudo tee /etc/systemd/system/prometheus.service > /dev/null << EOF
[Unit]
Description=Prometheus
Wants=network-online.target
After=network-online.target

[Service]
User=prometheus
Group=prometheus
Type=simple
ExecStart=/opt/prometheus/prometheus \\
    --config.file /etc/prometheus/prometheus.yml \\
    --storage.tsdb.path /var/lib/prometheus/ \\
    --web.console.templates=/opt/prometheus/consoles \\
    --web.console.libraries=/opt/prometheus/console_libraries \\
    --web.listen-address=0.0.0.0:9090 \\
    --web.enable-lifecycle
Restart=always

[Install]
WantedBy=multi-user.target
EOF

# 10. 启动 Prometheus
sudo systemctl daemon-reload
sudo systemctl enable prometheus
sudo systemctl start prometheus

# 11. 安装 Grafana
sudo apt-get install -y adduser libfontconfig1 musl
wget -q -O /usr/share/keyrings/grafana.key https://apt.grafana.com/gpg.key
echo "deb [signed-by=/usr/share/keyrings/grafana.key] https://apt.grafana.com stable main" | sudo tee /etc/apt/sources.list.d/grafana.list > /dev/null
sudo apt-get update
sudo apt-get install -y grafana

# 12. 启动 Grafana
sudo systemctl enable grafana-server
sudo systemctl start grafana-server

# 13. 安装 RabbitMQ
sudo apt-get install -y erlang-base erlang-nox erlang-dev
curl -1sLf 'https://dl.cloudsmith.io/public/rabbitmq/rabbitmq-erlang/setup.deb.sh' | sudo bash
curl -1sLf 'https://dl.cloudsmith.io/public/rabbitmq/rabbitmq-server/setup.deb.sh' | sudo bash
sudo apt-get update
sudo apt-get install -y rabbitmq-server
sudo rabbitmq-plugins enable rabbitmq_management
sudo systemctl enable rabbitmq-server
sudo systemctl start rabbitmq-server

# 14. 获取 WSL IP
WSL_IP=$(hostname -I | awk '{print $1}')
echo ""
echo "=========================================="
echo "安装完成！"
echo "=========================================="
echo ""
echo "WSL IP: $WSL_IP"
echo ""
echo "服务访问地址："
echo "Redis:        $WSL_IP:6379"
echo "Prometheus:   http://$WSL_IP:9090"
echo "Grafana:      http://$WSL_IP:3000"
echo "RabbitMQ:     http://$WSL_IP:15672"
echo ""
echo "默认登录："
echo "Grafana:      admin / admin"
echo "RabbitMQ:     guest / guest"
echo ""

# 15. 验证所有服务
echo "验证服务状态："
echo "Redis:        $(redis-cli ping)"
echo "Prometheus:   $(curl -s http://localhost:9090/-/healthy || echo 'FAILED')"
echo "Grafana:      $(curl -s http://localhost:3000/api/health | head -c 50 || echo 'FAILED')"
echo "RabbitMQ:     $(curl -s http://localhost:15672/api/overview | head -c 50 || echo 'FAILED')"
