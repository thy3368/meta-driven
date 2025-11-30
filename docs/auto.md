# 自动化部署方案 - 蓝绿部署与金丝雀发布

## 概述

本文档描述 meta-driven 项目的自动化部署策略，包括蓝绿部署和金丝雀发布两种主要方案，以及相关的 CI/CD 流水线配置。

## 目录

1. [部署策略对比](#部署策略对比)
2. [蓝绿部署方案](#蓝绿部署方案)
3. [金丝雀发布方案](#金丝雀发布方案)
4. [Kubernetes 部署配置](#kubernetes-部署配置)
5. [CI/CD 流水线](#cicd-流水线)
6. [监控与回滚](#监控与回滚)
7. [低时延优化](#低时延优化)

---

## 部署策略对比

| 特性 | 蓝绿部署 | 金丝雀发布 |
|------|---------|-----------|
| 资源需求 | 需要双倍资源 | 渐进式，资源可控 |
| 回滚速度 | 即时（切换流量） | 较快（减少新版本权重） |
| 风险控制 | 全量切换风险较高 | 渐进式风险最低 |
| 适用场景 | 重大版本升级 | 日常迭代发布 |
| 验证时间 | 切换前需充分测试 | 可边发布边观察 |
| 复杂度 | 相对简单 | 需要流量控制能力 |

### 推荐策略

- **生产环境日常发布**: 金丝雀发布（渐进式，风险可控）
- **重大版本升级**: 蓝绿部署（可即时回滚）
- **紧急修复**: 蓝绿部署（快速切换）

---

## 蓝绿部署方案

### 架构图

```
                    ┌─────────────────┐
                    │   Load Balancer │
                    │    (Nginx/K8s)  │
                    └────────┬────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
              ▼                             ▼
     ┌─────────────────┐          ┌─────────────────┐
     │   Blue Env      │          │   Green Env     │
     │   (v1.0.0)      │          │   (v1.1.0)      │
     │   [ACTIVE]      │          │   [STANDBY]     │
     └─────────────────┘          └─────────────────┘
              │                             │
              └──────────────┬──────────────┘
                             ▼
                    ┌─────────────────┐
                    │   Shared        │
                    │   Database      │
                    └─────────────────┘
```

### Docker Compose 蓝绿部署配置

```yaml
# docker-compose-blue-green.yml
version: '3.8'

services:
  # ============================================
  # Blue 环境 - 当前生产版本
  # ============================================
  app-blue:
    image: meta-driven:${BLUE_VERSION:-latest}
    container_name: meta-driven-blue
    deploy:
      replicas: 2
      resources:
        limits:
          memory: 1G
          cpus: '2'
        reservations:
          memory: 512M
          cpus: '1'
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SERVER_PORT=8080
      - JAVA_OPTS=-XX:+UseG1GC -XX:MaxGCPauseMillis=10 -XX:+AlwaysPreTouch
      - DEPLOYMENT_COLOR=blue
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 60s
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.app-blue.rule=Host(`api.example.com`)"
      - "traefik.http.services.app-blue.loadbalancer.server.port=8080"
    networks:
      - app-network

  # ============================================
  # Green 环境 - 新版本待发布
  # ============================================
  app-green:
    image: meta-driven:${GREEN_VERSION:-latest}
    container_name: meta-driven-green
    deploy:
      replicas: 2
      resources:
        limits:
          memory: 1G
          cpus: '2'
        reservations:
          memory: 512M
          cpus: '1'
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SERVER_PORT=8080
      - JAVA_OPTS=-XX:+UseG1GC -XX:MaxGCPauseMillis=10 -XX:+AlwaysPreTouch
      - DEPLOYMENT_COLOR=green
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 60s
    labels:
      - "traefik.enable=false"  # 默认不接收流量
    networks:
      - app-network

  # ============================================
  # Nginx 反向代理（流量切换）
  # ============================================
  nginx:
    image: nginx:alpine
    container_name: nginx-lb
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./config/nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./config/nginx/upstream.conf:/etc/nginx/conf.d/upstream.conf:ro
    depends_on:
      - app-blue
      - app-green
    networks:
      - app-network

networks:
  app-network:
    driver: bridge
```

### Nginx 流量切换配置

```nginx
# config/nginx/upstream.conf
# 蓝绿部署流量切换配置

# 当前活跃环境（切换时修改此文件）
upstream current_backend {
    # Blue 环境 - 当前生产
    server app-blue:8080 weight=100;

    # Green 环境 - 切换时启用
    # server app-green:8080 weight=100;

    keepalive 32;
}

# 健康检查配置
upstream blue_backend {
    server app-blue:8080;
    keepalive 16;
}

upstream green_backend {
    server app-green:8080;
    keepalive 16;
}
```

```nginx
# config/nginx/nginx.conf
worker_processes auto;
worker_cpu_affinity auto;

events {
    worker_connections 65535;
    use epoll;
    multi_accept on;
}

http {
    include /etc/nginx/conf.d/upstream.conf;

    # 低时延优化
    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    keepalive_requests 10000;

    # 连接池优化
    upstream_keepalive_timeout 60s;

    server {
        listen 80;
        server_name api.example.com;

        location / {
            proxy_pass http://current_backend;
            proxy_http_version 1.1;
            proxy_set_header Connection "";
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Deployment-Color $upstream_addr;

            # 低时延配置
            proxy_connect_timeout 5s;
            proxy_send_timeout 30s;
            proxy_read_timeout 30s;
            proxy_buffering off;
        }

        # 健康检查端点
        location /health/blue {
            proxy_pass http://blue_backend/actuator/health;
        }

        location /health/green {
            proxy_pass http://green_backend/actuator/health;
        }
    }
}
```

### 蓝绿切换脚本

```bash
#!/bin/bash
# scripts/blue-green-switch.sh
# 蓝绿部署切换脚本

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
NGINX_CONF="${PROJECT_DIR}/config/nginx/upstream.conf"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 获取当前活跃环境
get_current_env() {
    if grep -q "server app-blue:8080 weight=100" "$NGINX_CONF" 2>/dev/null; then
        echo "blue"
    elif grep -q "server app-green:8080 weight=100" "$NGINX_CONF" 2>/dev/null; then
        echo "green"
    else
        echo "unknown"
    fi
}

# 健康检查
health_check() {
    local env=$1
    local max_retries=30
    local retry=0

    log_info "检查 ${env} 环境健康状态..."

    while [ $retry -lt $max_retries ]; do
        if curl -sf "http://localhost/health/${env}" > /dev/null 2>&1; then
            log_success "${env} 环境健康检查通过"
            return 0
        fi
        retry=$((retry + 1))
        log_info "等待 ${env} 环境就绪... ($retry/$max_retries)"
        sleep 2
    done

    log_error "${env} 环境健康检查失败"
    return 1
}

# 切换流量
switch_traffic() {
    local target_env=$1
    local backup_file="${NGINX_CONF}.backup.$(date +%Y%m%d%H%M%S)"

    log_info "备份当前配置到 ${backup_file}"
    cp "$NGINX_CONF" "$backup_file"

    log_info "切换流量到 ${target_env} 环境..."

    if [ "$target_env" = "blue" ]; then
        cat > "$NGINX_CONF" << 'EOF'
upstream current_backend {
    server app-blue:8080 weight=100;
    keepalive 32;
}

upstream blue_backend {
    server app-blue:8080;
    keepalive 16;
}

upstream green_backend {
    server app-green:8080;
    keepalive 16;
}
EOF
    else
        cat > "$NGINX_CONF" << 'EOF'
upstream current_backend {
    server app-green:8080 weight=100;
    keepalive 32;
}

upstream blue_backend {
    server app-blue:8080;
    keepalive 16;
}

upstream green_backend {
    server app-green:8080;
    keepalive 16;
}
EOF
    fi

    # 重载 Nginx 配置
    docker exec nginx-lb nginx -s reload

    log_success "流量已切换到 ${target_env} 环境"
}

# 主逻辑
main() {
    local action=${1:-"status"}

    case $action in
        status)
            local current=$(get_current_env)
            log_info "当前活跃环境: ${current}"
            ;;
        switch)
            local current=$(get_current_env)
            local target=${2:-""}

            if [ -z "$target" ]; then
                if [ "$current" = "blue" ]; then
                    target="green"
                else
                    target="blue"
                fi
            fi

            log_info "当前环境: ${current}, 目标环境: ${target}"

            # 健康检查
            if ! health_check "$target"; then
                log_error "目标环境不健康，取消切换"
                exit 1
            fi

            # 执行切换
            switch_traffic "$target"

            log_success "蓝绿切换完成: ${current} -> ${target}"
            ;;
        rollback)
            local current=$(get_current_env)
            local target

            if [ "$current" = "blue" ]; then
                target="green"
            else
                target="blue"
            fi

            log_info "回滚: ${current} -> ${target}"
            switch_traffic "$target"
            log_success "回滚完成"
            ;;
        *)
            echo "用法: $0 {status|switch [blue|green]|rollback}"
            exit 1
            ;;
    esac
}

main "$@"
```

---

## 金丝雀发布方案

### 架构图

```
                         ┌─────────────────┐
                         │   Load Balancer │
                         │   (Istio/Nginx) │
                         └────────┬────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    │     Traffic Split         │
                    │  Stable: 90% Canary: 10%  │
                    └─────────────┬─────────────┘
                                  │
           ┌──────────────────────┼──────────────────────┐
           │                      │                      │
           ▼                      ▼                      ▼
  ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
  │  Stable Pod 1   │   │  Stable Pod 2   │   │  Canary Pod     │
  │    (v1.0.0)     │   │    (v1.0.0)     │   │    (v1.1.0)     │
  │   [90% traffic] │   │   [90% traffic] │   │   [10% traffic] │
  └─────────────────┘   └─────────────────┘   └─────────────────┘
```

### 渐进式发布阶段

```
阶段 1: 1% 流量 (烟雾测试)
   │
   ├── 监控: 错误率、延迟 P99
   │
   ▼
阶段 2: 5% 流量 (小范围验证)
   │
   ├── 监控: 业务指标、用户反馈
   │
   ▼
阶段 3: 10% 流量 (扩大验证)
   │
   ├── 监控: 全面指标分析
   │
   ▼
阶段 4: 25% 流量 (信心建立)
   │
   ├── 监控: 性能对比分析
   │
   ▼
阶段 5: 50% 流量 (对等验证)
   │
   ├── 监控: A/B 测试结果
   │
   ▼
阶段 6: 100% 流量 (全量发布)
   │
   └── 清理旧版本资源
```

### Docker Compose 金丝雀配置

```yaml
# docker-compose-canary.yml
version: '3.8'

services:
  # ============================================
  # Stable 版本 - 当前生产版本
  # ============================================
  app-stable:
    image: meta-driven:${STABLE_VERSION:-v1.0.0}
    deploy:
      replicas: 3
      resources:
        limits:
          memory: 1G
          cpus: '2'
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DEPLOYMENT_VERSION=stable
      - CANARY_WEIGHT=90
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 3
    labels:
      - "app.version=stable"
      - "app.canary=false"
    networks:
      - app-network

  # ============================================
  # Canary 版本 - 新版本
  # ============================================
  app-canary:
    image: meta-driven:${CANARY_VERSION:-v1.1.0}
    deploy:
      replicas: 1
      resources:
        limits:
          memory: 1G
          cpus: '2'
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DEPLOYMENT_VERSION=canary
      - CANARY_WEIGHT=10
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 3
    labels:
      - "app.version=canary"
      - "app.canary=true"
    networks:
      - app-network

  # ============================================
  # Nginx 金丝雀路由
  # ============================================
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./config/nginx/canary.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - app-stable
      - app-canary
    networks:
      - app-network

networks:
  app-network:
    driver: bridge
```

### Nginx 金丝雀路由配置

```nginx
# config/nginx/canary.conf
worker_processes auto;

events {
    worker_connections 65535;
    use epoll;
}

http {
    # Stable 后端
    upstream stable_backend {
        server app-stable:8080 weight=90;
        keepalive 32;
    }

    # Canary 后端
    upstream canary_backend {
        server app-canary:8080 weight=10;
        keepalive 16;
    }

    # 基于权重的流量分配
    split_clients "${request_id}" $backend_pool {
        10%     canary_backend;
        *       stable_backend;
    }

    # 基于 Header 的精确控制（测试用）
    map $http_x_canary $target_backend {
        "true"   canary_backend;
        default  $backend_pool;
    }

    server {
        listen 80;

        location / {
            proxy_pass http://$target_backend;
            proxy_http_version 1.1;
            proxy_set_header Connection "";
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Backend-Version $target_backend;

            # 低时延配置
            proxy_buffering off;
            proxy_connect_timeout 5s;
        }

        # Prometheus 指标端点
        location /metrics {
            stub_status on;
        }
    }
}
```

### 金丝雀发布脚本

```bash
#!/bin/bash
# scripts/canary-deploy.sh
# 金丝雀发布脚本

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# 配置
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
CANARY_STAGES=(1 5 10 25 50 100)
STAGE_WAIT_MINUTES=5
ERROR_THRESHOLD=0.01  # 1% 错误率阈值
LATENCY_THRESHOLD_MS=100  # P99 延迟阈值

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 更新 Canary 权重
update_canary_weight() {
    local weight=$1
    local stable_weight=$((100 - weight))

    log_info "更新 Canary 权重: ${weight}% (Stable: ${stable_weight}%)"

    # 生成新的 Nginx 配置
    cat > "${PROJECT_DIR}/config/nginx/canary-weight.conf" << EOF
split_clients "\${request_id}" \$backend_pool {
    ${weight}%     canary_backend;
    *              stable_backend;
}
EOF

    # 重载 Nginx
    docker exec nginx-lb nginx -s reload

    log_success "权重更新完成"
}

# 查询 Prometheus 指标
query_prometheus() {
    local query=$1
    curl -s "${PROMETHEUS_URL}/api/v1/query" \
        --data-urlencode "query=${query}" | \
        jq -r '.data.result[0].value[1] // "0"'
}

# 检查错误率
check_error_rate() {
    local version=$1
    local query="sum(rate(http_server_requests_seconds_count{status=~\"5..\",version=\"${version}\"}[5m])) / sum(rate(http_server_requests_seconds_count{version=\"${version}\"}[5m]))"

    local error_rate=$(query_prometheus "$query")

    log_info "Canary 错误率: ${error_rate}"

    if (( $(echo "$error_rate > $ERROR_THRESHOLD" | bc -l) )); then
        log_error "错误率超过阈值: ${error_rate} > ${ERROR_THRESHOLD}"
        return 1
    fi

    return 0
}

# 检查延迟
check_latency() {
    local version=$1
    local query="histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{version=\"${version}\"}[5m])) by (le)) * 1000"

    local latency=$(query_prometheus "$query")

    log_info "Canary P99 延迟: ${latency}ms"

    if (( $(echo "$latency > $LATENCY_THRESHOLD_MS" | bc -l) )); then
        log_error "延迟超过阈值: ${latency}ms > ${LATENCY_THRESHOLD_MS}ms"
        return 1
    fi

    return 0
}

# 执行健康检查
health_check() {
    log_info "执行健康检查..."

    if ! check_error_rate "canary"; then
        return 1
    fi

    if ! check_latency "canary"; then
        return 1
    fi

    log_success "健康检查通过"
    return 0
}

# 回滚
rollback() {
    log_warn "执行回滚..."

    update_canary_weight 0

    # 缩容 Canary
    docker-compose -f docker-compose-canary.yml scale app-canary=0

    log_success "回滚完成，Canary 流量已清零"
}

# 渐进式发布
progressive_rollout() {
    local canary_version=$1

    log_info "开始金丝雀发布: ${canary_version}"

    # 部署 Canary 版本
    CANARY_VERSION=$canary_version docker-compose -f docker-compose-canary.yml up -d app-canary

    # 等待 Canary 就绪
    log_info "等待 Canary 就绪..."
    sleep 30

    for weight in "${CANARY_STAGES[@]}"; do
        log_info "====== 阶段: ${weight}% 流量 ======"

        update_canary_weight "$weight"

        log_info "等待 ${STAGE_WAIT_MINUTES} 分钟收集指标..."
        sleep $((STAGE_WAIT_MINUTES * 60))

        if ! health_check; then
            log_error "健康检查失败，执行回滚"
            rollback
            exit 1
        fi

        if [ "$weight" -eq 100 ]; then
            log_success "金丝雀发布完成，全量切换到新版本"

            # 更新 Stable 版本
            STABLE_VERSION=$canary_version docker-compose -f docker-compose-canary.yml up -d app-stable

            # 缩容旧的 Canary
            docker-compose -f docker-compose-canary.yml scale app-canary=0
        fi
    done

    log_success "发布完成"
}

# 主函数
main() {
    local action=${1:-"help"}

    case $action in
        deploy)
            local version=${2:-""}
            if [ -z "$version" ]; then
                log_error "请指定版本号"
                exit 1
            fi
            progressive_rollout "$version"
            ;;
        rollback)
            rollback
            ;;
        status)
            log_info "当前状态:"
            docker-compose -f docker-compose-canary.yml ps
            ;;
        *)
            echo "用法: $0 {deploy <version>|rollback|status}"
            exit 1
            ;;
    esac
}

main "$@"
```

---

## Kubernetes 部署配置

### 蓝绿部署 - Kubernetes

```yaml
# k8s/blue-green/deployment-blue.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: meta-driven-blue
  labels:
    app: meta-driven
    version: blue
spec:
  replicas: 3
  selector:
    matchLabels:
      app: meta-driven
      version: blue
  template:
    metadata:
      labels:
        app: meta-driven
        version: blue
    spec:
      containers:
      - name: app
        image: meta-driven:v1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: JAVA_OPTS
          value: "-XX:+UseG1GC -XX:MaxGCPauseMillis=10 -XX:+AlwaysPreTouch -Xms512m -Xmx1g"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "2"
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchLabels:
                  app: meta-driven
              topologyKey: kubernetes.io/hostname
---
apiVersion: v1
kind: Service
metadata:
  name: meta-driven-blue
spec:
  selector:
    app: meta-driven
    version: blue
  ports:
  - port: 8080
    targetPort: 8080
```

```yaml
# k8s/blue-green/service-switch.yaml
# 通过修改 selector 切换蓝绿环境
apiVersion: v1
kind: Service
metadata:
  name: meta-driven
spec:
  selector:
    app: meta-driven
    version: blue  # 切换时改为 green
  ports:
  - port: 80
    targetPort: 8080
  type: ClusterIP
```

### 金丝雀发布 - Kubernetes + Istio

```yaml
# k8s/canary/deployment-stable.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: meta-driven-stable
  labels:
    app: meta-driven
    version: stable
spec:
  replicas: 3
  selector:
    matchLabels:
      app: meta-driven
      version: stable
  template:
    metadata:
      labels:
        app: meta-driven
        version: stable
    spec:
      containers:
      - name: app
        image: meta-driven:v1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: DEPLOYMENT_VERSION
          value: "stable"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "2"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: meta-driven-canary
  labels:
    app: meta-driven
    version: canary
spec:
  replicas: 1
  selector:
    matchLabels:
      app: meta-driven
      version: canary
  template:
    metadata:
      labels:
        app: meta-driven
        version: canary
    spec:
      containers:
      - name: app
        image: meta-driven:v1.1.0
        ports:
        - containerPort: 8080
        env:
        - name: DEPLOYMENT_VERSION
          value: "canary"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "2"
```

```yaml
# k8s/canary/istio-virtual-service.yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: meta-driven
spec:
  hosts:
  - meta-driven
  http:
  - match:
    - headers:
        x-canary:
          exact: "true"
    route:
    - destination:
        host: meta-driven
        subset: canary
  - route:
    - destination:
        host: meta-driven
        subset: stable
      weight: 90
    - destination:
        host: meta-driven
        subset: canary
      weight: 10
---
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: meta-driven
spec:
  host: meta-driven
  subsets:
  - name: stable
    labels:
      version: stable
  - name: canary
    labels:
      version: canary
```

### Argo Rollouts 渐进式发布

```yaml
# k8s/argo-rollouts/rollout.yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: meta-driven
spec:
  replicas: 5
  strategy:
    canary:
      # 金丝雀发布步骤
      steps:
      - setWeight: 5
      - pause: {duration: 5m}
      - setWeight: 10
      - pause: {duration: 10m}
      - setWeight: 25
      - pause: {duration: 10m}
      - setWeight: 50
      - pause: {duration: 15m}
      # 自动分析
      analysis:
        templates:
        - templateName: success-rate
        startingStep: 2
        args:
        - name: service-name
          value: meta-driven
      # 流量管理
      trafficRouting:
        istio:
          virtualService:
            name: meta-driven
            routes:
            - primary
      # 反亲和性
      antiAffinity:
        preferredDuringSchedulingIgnoredDuringExecution:
          weight: 100
  selector:
    matchLabels:
      app: meta-driven
  template:
    metadata:
      labels:
        app: meta-driven
    spec:
      containers:
      - name: app
        image: meta-driven:v1.1.0
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "2"
---
# 分析模板
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: success-rate
spec:
  args:
  - name: service-name
  metrics:
  - name: success-rate
    interval: 1m
    successCondition: result[0] >= 0.99
    failureLimit: 3
    provider:
      prometheus:
        address: http://prometheus:9090
        query: |
          sum(rate(
            http_server_requests_seconds_count{
              service="{{args.service-name}}",
              status!~"5.."
            }[5m]
          )) /
          sum(rate(
            http_server_requests_seconds_count{
              service="{{args.service-name}}"
            }[5m]
          ))
  - name: latency-p99
    interval: 1m
    successCondition: result[0] < 100
    failureLimit: 3
    provider:
      prometheus:
        address: http://prometheus:9090
        query: |
          histogram_quantile(0.99,
            sum(rate(
              http_server_requests_seconds_bucket{
                service="{{args.service-name}}"
              }[5m]
            )) by (le)
          ) * 1000
```

---

## CI/CD 流水线

### GitHub Actions 工作流

```yaml
# .github/workflows/deploy.yml
name: Build and Deploy

on:
  push:
    branches: [main]
    tags: ['v*']
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  build:
    runs-on: ubuntu-latest
    outputs:
      version: ${{ steps.version.outputs.version }}
      image: ${{ steps.meta.outputs.tags }}

    steps:
    - uses: actions/checkout@v4

    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
        cache: maven

    - name: Determine version
      id: version
      run: |
        if [[ "${{ github.ref }}" == refs/tags/* ]]; then
          echo "version=${GITHUB_REF#refs/tags/}" >> $GITHUB_OUTPUT
        else
          echo "version=sha-${GITHUB_SHA::8}" >> $GITHUB_OUTPUT
        fi

    - name: Build with Maven
      run: |
        ./mvnw clean package -DskipTests \
          -Dspring.aot.enabled=true

    - name: Run tests
      run: ./mvnw test

    - name: Log in to Container Registry
      uses: docker/login-action@v3
      with:
        registry: ${{ env.REGISTRY }}
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}

    - name: Extract metadata
      id: meta
      uses: docker/metadata-action@v5
      with:
        images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
        tags: |
          type=ref,event=branch
          type=semver,pattern={{version}}
          type=sha,prefix=sha-

    - name: Build and push Docker image
      uses: docker/build-push-action@v5
      with:
        context: .
        push: true
        tags: ${{ steps.meta.outputs.tags }}
        labels: ${{ steps.meta.outputs.labels }}
        cache-from: type=gha
        cache-to: type=gha,mode=max

  deploy-staging:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    environment: staging

    steps:
    - uses: actions/checkout@v4

    - name: Deploy to Staging (Blue-Green)
      run: |
        echo "Deploying ${{ needs.build.outputs.version }} to staging"
        # 部署 Green 环境
        kubectl set image deployment/meta-driven-green \
          app=${{ needs.build.outputs.image }} \
          --namespace staging

        # 等待就绪
        kubectl rollout status deployment/meta-driven-green \
          --namespace staging --timeout=300s

        # 健康检查
        ./scripts/health-check.sh staging green

        # 切换流量
        kubectl patch service meta-driven \
          -p '{"spec":{"selector":{"version":"green"}}}' \
          --namespace staging

  deploy-production:
    needs: [build, deploy-staging]
    runs-on: ubuntu-latest
    if: startsWith(github.ref, 'refs/tags/v')
    environment: production

    steps:
    - uses: actions/checkout@v4

    - name: Deploy Canary to Production
      run: |
        echo "Starting canary deployment: ${{ needs.build.outputs.version }}"

        # 部署 Canary
        kubectl set image deployment/meta-driven-canary \
          app=${{ needs.build.outputs.image }} \
          --namespace production

        # 等待就绪
        kubectl rollout status deployment/meta-driven-canary \
          --namespace production --timeout=300s

    - name: Progressive Rollout
      run: |
        # 使用 Argo Rollouts 或自定义脚本
        kubectl argo rollouts promote meta-driven \
          --namespace production

    - name: Monitor Deployment
      run: |
        # 监控 30 分钟
        for i in {1..30}; do
          echo "Checking metrics (minute $i/30)..."
          ./scripts/check-canary-health.sh production
          sleep 60
        done

    - name: Finalize or Rollback
      if: always()
      run: |
        if [ "${{ job.status }}" == "success" ]; then
          echo "Promoting to full production"
          kubectl argo rollouts promote meta-driven --full \
            --namespace production
        else
          echo "Rolling back"
          kubectl argo rollouts abort meta-driven \
            --namespace production
        fi
```

### Dockerfile 优化

```dockerfile
# Dockerfile
# 多阶段构建优化

# 阶段1: 构建
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# 下载依赖（利用缓存）
RUN ./mvnw dependency:go-offline -B

COPY src src

# 构建应用
RUN ./mvnw clean package -DskipTests \
    -Dspring.aot.enabled=true

# 阶段2: 运行时
FROM eclipse-temurin:21-jre-alpine

# 低时延优化: 创建非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# 复制构建产物
COPY --from=builder /app/target/*.jar app.jar

# 健康检查
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --spider -q http://localhost:8080/actuator/health || exit 1

# 切换到非 root 用户
USER appuser

# 低时延 JVM 配置
ENV JAVA_OPTS="\
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=10 \
    -XX:+AlwaysPreTouch \
    -XX:+UseStringDeduplication \
    -Xms512m \
    -Xmx1g \
    -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## 监控与回滚

### Prometheus 告警规则

```yaml
# config/prometheus/alert.rules.yml
groups:
- name: canary-alerts
  rules:
  # 错误率告警
  - alert: CanaryHighErrorRate
    expr: |
      sum(rate(http_server_requests_seconds_count{version="canary",status=~"5.."}[5m]))
      /
      sum(rate(http_server_requests_seconds_count{version="canary"}[5m]))
      > 0.01
    for: 2m
    labels:
      severity: critical
    annotations:
      summary: "Canary 错误率过高"
      description: "Canary 版本 5xx 错误率超过 1%，当前: {{ $value | humanizePercentage }}"

  # 延迟告警
  - alert: CanaryHighLatency
    expr: |
      histogram_quantile(0.99,
        sum(rate(http_server_requests_seconds_bucket{version="canary"}[5m])) by (le)
      ) * 1000 > 100
    for: 2m
    labels:
      severity: warning
    annotations:
      summary: "Canary 延迟过高"
      description: "Canary 版本 P99 延迟超过 100ms，当前: {{ $value }}ms"

  # 对比告警
  - alert: CanaryPerformanceDegradation
    expr: |
      (
        histogram_quantile(0.99,
          sum(rate(http_server_requests_seconds_bucket{version="canary"}[5m])) by (le)
        )
        /
        histogram_quantile(0.99,
          sum(rate(http_server_requests_seconds_bucket{version="stable"}[5m])) by (le)
        )
      ) > 1.5
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "Canary 性能下降"
      description: "Canary 延迟比 Stable 高 50% 以上"

- name: deployment-alerts
  rules:
  # 部署失败告警
  - alert: DeploymentFailed
    expr: |
      kube_deployment_status_replicas_unavailable{deployment=~"meta-driven.*"} > 0
    for: 5m
    labels:
      severity: critical
    annotations:
      summary: "部署失败"
      description: "{{ $labels.deployment }} 有不可用副本"

  # Pod 重启告警
  - alert: PodRestarting
    expr: |
      increase(kube_pod_container_status_restarts_total{pod=~"meta-driven.*"}[15m]) > 3
    for: 0m
    labels:
      severity: warning
    annotations:
      summary: "Pod 频繁重启"
      description: "{{ $labels.pod }} 在 15 分钟内重启超过 3 次"
```

### Grafana 部署监控 Dashboard

```json
{
  "dashboard": {
    "title": "Deployment Monitoring",
    "panels": [
      {
        "title": "Request Rate by Version",
        "type": "timeseries",
        "targets": [
          {
            "expr": "sum(rate(http_server_requests_seconds_count[1m])) by (version)",
            "legendFormat": "{{version}}"
          }
        ]
      },
      {
        "title": "Error Rate Comparison",
        "type": "timeseries",
        "targets": [
          {
            "expr": "sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[5m])) by (version) / sum(rate(http_server_requests_seconds_count[5m])) by (version)",
            "legendFormat": "{{version}}"
          }
        ]
      },
      {
        "title": "P99 Latency Comparison",
        "type": "timeseries",
        "targets": [
          {
            "expr": "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, version)) * 1000",
            "legendFormat": "{{version}}"
          }
        ]
      },
      {
        "title": "Canary Traffic Weight",
        "type": "gauge",
        "targets": [
          {
            "expr": "sum(rate(http_server_requests_seconds_count{version=\"canary\"}[5m])) / sum(rate(http_server_requests_seconds_count[5m])) * 100"
          }
        ]
      }
    ]
  }
}
```

### 自动回滚脚本

```bash
#!/bin/bash
# scripts/auto-rollback.sh
# 基于指标的自动回滚

set -euo pipefail

PROMETHEUS_URL="${PROMETHEUS_URL:-http://prometheus:9090}"
NAMESPACE="${NAMESPACE:-production}"
ERROR_THRESHOLD=0.01
LATENCY_THRESHOLD_MS=100

log_info() { echo "[INFO] $(date '+%Y-%m-%d %H:%M:%S') $1"; }
log_error() { echo "[ERROR] $(date '+%Y-%m-%d %H:%M:%S') $1"; }

query_prometheus() {
    curl -s "${PROMETHEUS_URL}/api/v1/query" \
        --data-urlencode "query=$1" | \
        jq -r '.data.result[0].value[1] // "0"'
}

check_health() {
    # 检查错误率
    local error_rate=$(query_prometheus 'sum(rate(http_server_requests_seconds_count{version="canary",status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{version="canary"}[5m]))')

    if (( $(echo "$error_rate > $ERROR_THRESHOLD" | bc -l) )); then
        log_error "错误率过高: $error_rate > $ERROR_THRESHOLD"
        return 1
    fi

    # 检查延迟
    local latency=$(query_prometheus 'histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{version="canary"}[5m])) by (le)) * 1000')

    if (( $(echo "$latency > $LATENCY_THRESHOLD_MS" | bc -l) )); then
        log_error "延迟过高: ${latency}ms > ${LATENCY_THRESHOLD_MS}ms"
        return 1
    fi

    log_info "健康检查通过 (错误率: $error_rate, 延迟: ${latency}ms)"
    return 0
}

rollback() {
    log_info "执行回滚..."

    # Kubernetes 回滚
    kubectl rollout undo deployment/meta-driven-canary --namespace "$NAMESPACE"

    # 或使用 Argo Rollouts
    # kubectl argo rollouts abort meta-driven --namespace "$NAMESPACE"

    # 发送告警
    curl -X POST "${ALERTMANAGER_URL}/api/v1/alerts" \
        -H "Content-Type: application/json" \
        -d '[{
            "labels": {"alertname": "DeploymentRolledBack", "severity": "warning"},
            "annotations": {"summary": "Canary deployment rolled back due to health check failure"}
        }]'

    log_info "回滚完成"
}

# 主监控循环
main() {
    log_info "启动自动回滚监控..."

    local consecutive_failures=0
    local max_failures=3

    while true; do
        if ! check_health; then
            consecutive_failures=$((consecutive_failures + 1))
            log_error "健康检查失败 ($consecutive_failures/$max_failures)"

            if [ $consecutive_failures -ge $max_failures ]; then
                rollback
                exit 1
            fi
        else
            consecutive_failures=0
        fi

        sleep 30
    done
}

main
```

---

## 低时延优化

### JVM 部署配置

```bash
# 低时延 JVM 启动参数
JAVA_OPTS="
  # G1GC 低暂停配置
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=10
  -XX:G1HeapRegionSize=4M
  -XX:+ParallelRefProcEnabled

  # 内存优化
  -XX:+AlwaysPreTouch
  -Xms1g
  -Xmx1g

  # JIT 预热
  -XX:+TieredCompilation
  -XX:TieredStopAtLevel=4

  # 大页面支持
  -XX:+UseLargePages
  -XX:LargePageSizeInBytes=2m

  # 减少 Safepoint 开销
  -XX:+UnlockDiagnosticVMOptions
  -XX:GuaranteedSafepointInterval=300000
"
```

### 零停机部署最佳实践

1. **优雅关闭配置**
```yaml
# application.yml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
server:
  shutdown: graceful

management:
  endpoint:
    health:
      probes:
        enabled: true
  health:
    readinessstate:
      enabled: true
    livenessstate:
      enabled: true
```

2. **连接池预热**
```java
@Component
public class WarmupRunner implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        // 预热连接池
        warmupDatabaseConnections();
        // 预热 HTTP 客户端
        warmupHttpClients();
        // 预热缓存
        warmupCaches();
    }
}
```

3. **流量排空**
```yaml
# Kubernetes preStop hook
lifecycle:
  preStop:
    exec:
      command:
      - sh
      - -c
      - |
        # 标记为不健康
        touch /tmp/shutdown
        # 等待流量排空
        sleep 15
```

---

## 总结

### 选择建议

| 场景 | 推荐策略 | 原因 |
|------|---------|------|
| 日常迭代 | 金丝雀发布 | 风险可控，渐进式验证 |
| 重大升级 | 蓝绿部署 | 快速回滚，完整测试 |
| 紧急修复 | 蓝绿部署 | 即时切换，最小停机 |
| 数据库迁移 | 蓝绿 + 功能开关 | 兼容性验证，安全回滚 |

### 检查清单

- [ ] 健康检查端点配置
- [ ] 优雅关闭配置
- [ ] 监控告警规则
- [ ] 自动回滚机制
- [ ] 流量排空策略
- [ ] 数据库兼容性验证
- [ ] 回滚演练测试
