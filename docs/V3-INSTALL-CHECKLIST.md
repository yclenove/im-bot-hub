# V3 安装清单

> 按阶段安装，不需要一次性全部装好

---

## 一、基础依赖（必须）

### 1.1 开发环境

| 工具 | 版本 | 用途 | 安装命令 |
|------|------|------|---------|
| JDK | 17+ | 后端运行 | 已有 |
| Maven | 3.8+ | 后端构建 | 已有 |
| Node.js | 18+ | 前端构建 | 已有 |
| MySQL | 5.7+ | 数据库 | 已有 |
| Git | 2.30+ | 版本控制 | 已有 |

### 1.2 可选工具

| 工具 | 用途 | 安装 |
|------|------|------|
| VS Code / IntelliJ | IDE | 已有 |
| Postman / Apifox | API 测试 | 已有 |
| DBeaver / Navicat | 数据库管理 | 已有 |

---

## 二、V3.1 数据智能（无需额外安装）

查询模板市场、命令统计、Channel 健康检查都是纯代码实现，不需要额外基础设施。

**唯一需求**：确保 MySQL 有足够的存储空间（统计表会增长）

---

## 三、V3.3 运维可观测（需要安装）

### 3.1 Redis（缓存）

**用途**：查询结果缓存、会话存储、限流计数器

**Windows 安装**：
```bash
# 方式1：使用 Chocolatey
choco install redis-64

# 方式2：下载 Windows 版
# https://github.com/microsoftarchive/redis/releases
# 下载 Redis-x64-3.0.504.zip，解压后运行 redis-server.exe

# 方式3：使用 Docker（推荐）
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

**验证**：
```bash
redis-cli ping
# 应返回 PONG
```

**application-local.yml 配置**：
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
```

---

### 3.2 Prometheus（指标采集）

**用途**：采集应用指标（QPS、响应时间、成功率）

**Windows 安装**：
```bash
# 方式1：使用 Chocolatey
choco install prometheus

# 方式2：下载二进制
# https://prometheus.io/download/
# 下载 windows-amd64 版本，解压后运行 prometheus.exe

# 方式3：使用 Docker（推荐）
docker run -d --name prometheus -p 9090:9090 prom/prometheus
```

**配置文件** (`prometheus.yml`)：
```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'im-bot-hub'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:18089']
```

**验证**：
```
http://localhost:9090/targets
# 应看到 im-bot-hub 状态为 UP
```

---

### 3.3 Grafana（指标可视化）

**用途**：可视化 Prometheus 指标，创建仪表盘

**Windows 安装**：
```bash
# 方式1：使用 Chocolatey
choco install grafana

# 方式2：下载 MSI 安装包
# https://grafana.com/grafana/download?platform=windows
# 运行 MSI 安装

# 方式3：使用 Docker（推荐）
docker run -d --name grafana -p 3000:3000 grafana/grafana
```

**默认登录**：
- 地址：http://localhost:3000
- 用户名：admin
- 密码：admin（首次登录后修改）

**配置 Prometheus 数据源**：
1. 左侧菜单 → Configuration → Data Sources
2. 点击 Add data source → Prometheus
3. URL 填写：`http://localhost:9090`
4. 点击 Save & Test

---

### 3.4 一键启动脚本（Docker Compose）

创建 `docker-compose-monitoring.yml`：

```yaml
version: '3.8'

services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

  prometheus:
    image: prom/prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
    extra_hosts:
      - "host.docker.internal:host-gateway"

  grafana:
    image: grafana/grafana
    ports:
      - "3000:3000"
    volumes:
      - grafana-data:/var/lib/grafana
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin123

volumes:
  redis-data:
  grafana-data:
```

**启动**：
```bash
docker-compose -f docker-compose-monitoring.yml up -d
```

**停止**：
```bash
docker-compose -f docker-compose-monitoring.yml down
```

---

## 四、V3.4 平台生态（可选）

### 4.1 RabbitMQ（消息队列）

**用途**：异步任务（告警通知、统计聚合）、事件驱动

**Windows 安装**：
```bash
# 方式1：使用 Chocolatey（需要先装 Erlang）
choco install erlang
choco install rabbitmq

# 方式2：使用 Docker（推荐）
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

**默认登录**：
- 地址：http://localhost:15672
- 用户名：guest
- 密码：guest

**application-local.yml 配置**：
```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

---

### 4.2 Nacos（配置中心）- 可选

**用途**：动态配置、多环境管理、服务发现

**Windows 安装**：
```bash
# 使用 Docker（推荐）
docker run -d --name nacos -p 8848:8848 -p 9848:9848 \
  -e MODE=standalone \
  nacos/nacos-server:v2.2.0
```

**默认登录**：
- 地址：http://localhost:8848/nacos
- 用户名：nacos
- 密码：nacos

---

### 4.3 ELK Stack（日志）- 可选

**用途**：集中日志、全文检索、日志分析

**使用 Docker Compose**：
```yaml
# docker-compose-elk.yml
version: '3.8'

services:
  elasticsearch:
    image: elasticsearch:8.7.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
    ports:
      - "9200:9200"
    volumes:
      - es-data:/usr/share/elasticsearch/data

  logstash:
    image: logstash:8.7.0
    volumes:
      - ./logstash/pipeline:/usr/share/logstash/pipeline
    depends_on:
      - elasticsearch

  kibana:
    image: kibana:8.7.0
    ports:
      - "5601:5601"
    depends_on:
      - elasticsearch

volumes:
  es-data:
```

---

## 五、Maven 依赖（自动管理）

以下依赖会在实现功能时自动添加到 `pom.xml`，无需手动安装：

### 5.1 V3.1 依赖

```xml
<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Caffeine（已有，保留） -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>

<!-- Prometheus 指标 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- Actuator（已有，启用 Prometheus 端点） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 5.2 V3.2 依赖

```xml
<!-- i18n（Spring 内置，无需额外依赖） -->

<!-- Security（角色权限） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
</dependency>
```

### 5.3 V3.4 依赖

```xml
<!-- RabbitMQ -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>

<!-- Nacos Config（可选） -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

---

## 六、npm 依赖（自动管理）

以下依赖会在实现功能时自动添加到 `package.json`：

```json
{
  "dependencies": {
    "vue-i18n": "^9.0.0",          // 多语言
    "echarts": "^5.4.0",           // 图表（已有）
    "pinia": "^2.0.0",             // 状态管理（可选）
    "@vueuse/core": "^10.0.0"      // 工具函数
  },
  "devDependencies": {
    "@types/node": "^20.0.0"       // 类型定义
  }
}
```

---

## 七、快速检查清单

### 安装前检查

- [ ] Docker 已安装（推荐，简化所有依赖安装）
- [ ] MySQL 5.7+ 已运行
- [ ] JDK 17+ 已安装
- [ ] Node.js 18+ 已安装
- [ ] 磁盘空间 > 10GB（Docker 镜像）

### V3.1 开始前

- [ ] MySQL 有足够空间（统计表）
- [ ] 无其他依赖

### V3.3 开始前

- [ ] Redis 已启动（端口 6379）
- [ ] Prometheus 已启动（端口 9090）
- [ ] Grafana 已启动（端口 3000）
- [ ] 或者运行 `docker-compose-monitoring.yml`

### V3.4 开始前（可选）

- [ ] RabbitMQ 已启动（端口 5672/15672）
- [ ] Nacos 已启动（端口 8848）- 可选
- [ ] ELK 已启动（端口 9200/5601）- 可选

---

## 八、推荐安装顺序

### 方案 A：最小安装（只做 P0-P2）

```bash
# 1. 启动 Redis
docker run -d --name redis -p 6379:6379 redis:7-alpine

# 2. 启动 Prometheus + Grafana
docker-compose -f docker-compose-monitoring.yml up -d

# 3. 验证
docker ps
# 应看到 3 个容器运行中
```

**所需资源**：2GB 内存，2GB 磁盘

### 方案 B：完整安装（做 P0-P3）

```bash
# 1. 启动所有服务
docker-compose -f docker-compose-monitoring.yml up -d
docker-compose -f docker-compose-elk.yml up -d
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management

# 2. 验证
docker ps
# 应看到 7 个容器运行中
```

**所需资源**：8GB 内存，10GB 磁盘

---

## 九、端口规划

| 服务 | 端口 | 用途 |
|------|------|------|
| im-bot-hub 后端 | 18089 | Spring Boot 应用 |
| im-bot-hub 前端 | 5173 (dev) | Vue 开发服务器 |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |
| Prometheus | 9090 | 指标采集 |
| Grafana | 3000 | 指标可视化 |
| RabbitMQ | 5672/15672 | 消息队列 |
| Nacos | 8848 | 配置中心 |
| Elasticsearch | 9200 | 日志存储 |
| Kibana | 5601 | 日志可视化 |

---

## 十、故障排查

### Docker 相关

```bash
# 查看容器状态
docker ps -a

# 查看容器日志
docker logs redis
docker logs prometheus

# 重启容器
docker restart redis

# 进入容器
docker exec -it redis redis-cli
```

### 端口冲突

```bash
# Windows 查看端口占用
netstat -ano | findstr ":6379"

# 杀掉进程
taskkill /PID <进程ID> /F
```

### 连接失败

```bash
# 测试 Redis 连接
redis-cli ping

# 测试 Prometheus
curl http://localhost:9090/-/healthy

# 测试 Grafana
curl http://localhost:3000/api/health
```

---

*本文件最后更新：2026-05-03*
*维护者：im-bot-hub 运维团队*
