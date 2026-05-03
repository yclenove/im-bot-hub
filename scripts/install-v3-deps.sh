#!/bin/bash
# im-bot-hub V3 依赖安装脚本（WSL Debian）
# 用法：在 WSL 中运行 bash scripts/install-v3-deps.sh

set -e

echo "=========================================="
echo "im-bot-hub V3 依赖安装脚本"
echo "=========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查是否以 root 运行
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}请使用 sudo 运行此脚本${NC}"
    echo "用法: sudo bash scripts/install-v3-deps.sh"
    exit 1
fi

# 更新系统
echo -e "${YELLOW}[1/6] 更新系统包...${NC}"
apt-get update
apt-get upgrade -y

# 安装基础工具
echo -e "${YELLOW}[2/6] 安装基础工具...${NC}"
apt-get install -y curl wget gnupg2 software-properties-common apt-transport-https lsb-release ca-certificates

# ============================================
# 安装 Redis
# ============================================
echo -e "${YELLOW}[3/6] 安装 Redis...${NC}"

# 检查是否已安装
if command -v redis-server &> /dev/null; then
    echo -e "${GREEN}Redis 已安装，跳过${NC}"
else
    apt-get install -y redis-server

    # 配置 Redis 允许外部访问（从 Windows 访问 WSL）
    sed -i 's/bind 127.0.0.1 ::1/bind 0.0.0.0/' /etc/redis/redis.conf
    sed -i 's/protected-mode yes/protected-mode no/' /etc/redis/redis.conf

    # 启动 Redis
    systemctl enable redis-server
    systemctl start redis-server

    echo -e "${GREEN}Redis 安装完成${NC}"
fi

# 验证 Redis
echo "验证 Redis..."
redis-cli ping || echo -e "${RED}Redis 启动失败${NC}"

# ============================================
# 安装 Prometheus
# ============================================
echo -e "${YELLOW}[4/6] 安装 Prometheus...${NC}"

PROMETHEUS_VERSION="2.52.0"
PROMETHEUS_USER="prometheus"
PROMETHEUS_DIR="/opt/prometheus"

# 检查是否已安装
if [ -d "$PROMETHEUS_DIR" ]; then
    echo -e "${GREEN}Prometheus 已安装，跳过${NC}"
else
    # 创建用户
    useradd --no-create-home --shell /bin/false $PROMETHEUS_USER 2>/dev/null || true

    # 创建目录
    mkdir -p $PROMETHEUS_DIR
    mkdir -p /etc/prometheus
    mkdir -p /var/lib/prometheus

    # 下载 Prometheus
    cd /tmp
    wget -q https://github.com/prometheus/prometheus/releases/download/v${PROMETHEUS_VERSION}/prometheus-${PROMETHEUS_VERSION}.linux-amd64.tar.gz
    tar xzf prometheus-${PROMETHEUS_VERSION}.linux-amd64.tar.gz

    # 复制文件
    cp prometheus-${PROMETHEUS_VERSION}.linux-amd64/prometheus $PROMETHEUS_DIR/
    cp prometheus-${PROMETHEUS_VERSION}.linux-amd64/promtool $PROMETHEUS_DIR/
    cp -r prometheus-${PROMETHEUS_VERSION}.linux-amd64/consoles $PROMETHEUS_DIR/
    cp -r prometheus-${PROMETHEUS_VERSION}.linux-amd64/console_libraries $PROMETHEUS_DIR/

    # 设置权限
    chown -R $PROMETHEUS_USER:$PROMETHEUS_USER $PROMETHEUS_DIR
    chown -R $PROMETHEUS_USER:$PROMETHEUS_USER /etc/prometheus
    chown -R $PROMETHEUS_USER:$PROMETHEUS_USER /var/lib/prometheus

    # 创建配置文件
    cat > /etc/prometheus/prometheus.yml << 'EOF'
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

    # 创建 systemd 服务
    cat > /etc/systemd/system/prometheus.service << EOF
[Unit]
Description=Prometheus
Wants=network-online.target
After=network-online.target

[Service]
User=$PROMETHEUS_USER
Group=$PROMETHEUS_USER
Type=simple
ExecStart=$PROMETHEUS_DIR/prometheus \\
    --config.file /etc/prometheus/prometheus.yml \\
    --storage.tsdb.path /var/lib/prometheus/ \\
    --web.console.templates=$PROMETHEUS_DIR/consoles \\
    --web.console.libraries=$PROMETHEUS_DIR/console_libraries \\
    --web.listen-address=0.0.0.0:9090 \\
    --web.enable-lifecycle
Restart=always

[Install]
WantedBy=multi-user.target
EOF

    # 启动服务
    systemctl daemon-reload
    systemctl enable prometheus
    systemctl start prometheus

    # 清理
    rm -rf /tmp/prometheus-${PROMETHEUS_VERSION}.linux-amd64*

    echo -e "${GREEN}Prometheus 安装完成${NC}"
fi

# ============================================
# 安装 Grafana
# ============================================
echo -e "${YELLOW}[5/6] 安装 Grafana...${NC}"

# 检查是否已安装
if command -v grafana-server &> /dev/null; then
    echo -e "${GREEN}Grafana 已安装，跳过${NC}"
else
    # 添加 Grafana APT 源
    apt-get install -y adduser libfontconfig1 musl
    wget -q -O /usr/share/keyrings/grafana.key https://apt.grafana.com/gpg.key
    echo "deb [signed-by=/usr/share/keyrings/grafana.key] https://apt.grafana.com stable main" > /etc/apt/sources.list.d/grafana.list

    apt-get update
    apt-get install -y grafana

    # 启动服务
    systemctl enable grafana-server
    systemctl start grafana-server

    echo -e "${GREEN}Grafana 安装完成${NC}"
fi

# ============================================
# 安装 RabbitMQ（可选）
# ============================================
echo -e "${YELLOW}[6/6] 安装 RabbitMQ...${NC}"

# 检查是否已安装
if command -v rabbitmq-server &> /dev/null; then
    echo -e "${GREEN}RabbitMQ 已安装，跳过${NC}"
else
    # 安装 Erlang
    apt-get install -y erlang-base erlang-nox erlang-dev

    # 添加 RabbitMQ APT 源
    curl -1sLf 'https://dl.cloudsmith.io/public/rabbitmq/rabbitmq-erlang/setup.deb.sh' | bash
    curl -1sLf 'https://dl.cloudsmith.io/public/rabbitmq/rabbitmq-server/setup.deb.sh' | bash

    apt-get update
    apt-get install -y rabbitmq-server

    # 启用管理插件
    rabbitmq-plugins enable rabbitmq_management

    # 启动服务
    systemctl enable rabbitmq-server
    systemctl start rabbitmq-server

    echo -e "${GREEN}RabbitMQ 安装完成${NC}"
fi

# ============================================
# 配置防火墙（如果需要）
# ============================================
echo -e "${YELLOW}配置端口访问...${NC}"

# 获取 WSL IP
WSL_IP=$(hostname -I | awk '{print $1}')
echo ""
echo "=========================================="
echo -e "${GREEN}安装完成！${NC}"
echo "=========================================="
echo ""
echo "服务访问地址（从 Windows 访问）："
echo ""
echo "Redis:        $WSL_IP:6379"
echo "Prometheus:   http://$WSL_IP:9090"
echo "Grafana:      http://$WSL_IP:3000"
echo "RabbitMQ:     http://$WSL_IP:15672"
echo ""
echo "默认登录信息："
echo "Grafana:      admin / admin"
echo "RabbitMQ:     guest / guest"
echo ""
echo "=========================================="
echo "服务管理命令："
echo "=========================================="
echo ""
echo "启动所有服务："
echo "  sudo systemctl start redis-server prometheus grafana-server rabbitmq-server"
echo ""
echo "停止所有服务："
echo "  sudo systemctl stop redis-server prometheus grafana-server rabbitmq-server"
echo ""
echo "查看服务状态："
echo "  sudo systemctl status redis-server"
echo "  sudo systemctl status prometheus"
echo "  sudo systemctl status grafana-server"
echo "  sudo systemctl status rabbitmq-server"
echo ""
echo "查看服务日志："
echo "  sudo journalctl -u redis-server -f"
echo "  sudo journalctl -u prometheus -f"
echo "  sudo journalctl -u grafana-server -f"
echo "  sudo journalctl -u rabbitmq-server -f"
echo ""
echo "=========================================="
echo "application-local.yml 配置："
echo "=========================================="
echo ""
echo "spring:"
echo "  data:"
echo "    redis:"
echo "      host: $WSL_IP"
echo "      port: 6379"
echo "  rabbitmq:"
echo "    host: $WSL_IP"
echo "    port: 5672"
echo "    username: guest"
echo "    password: guest"
echo ""
echo "management:"
echo "  endpoints:"
echo "    web:"
echo "      exposure:"
echo "        include: prometheus,health,info"
echo ""
