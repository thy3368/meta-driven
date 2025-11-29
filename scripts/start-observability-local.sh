#!/bin/bash
# ============================================
# 本地开发环境可观测性启动脚本
# ============================================
#
# 在 macOS 上，由于 Docker Desktop 网络限制，
# 推荐 Prometheus 直接在宿主机运行
#

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo -e "${BLUE}============================================${NC}"
echo -e "${BLUE}  Meta-Driven 本地可观测性环境${NC}"
echo -e "${BLUE}============================================${NC}"
echo ""

# 检查依赖
check_dependency() {
    if ! command -v "$1" &> /dev/null; then
        echo -e "${RED}错误: $1 未安装${NC}"
        echo "请运行: brew install $1"
        return 1
    fi
    echo -e "${GREEN}✓ $1 已安装${NC}"
    return 0
}

echo "检查依赖..."
DEPS_OK=true
check_dependency prometheus || DEPS_OK=false
check_dependency docker || DEPS_OK=false
check_dependency docker-compose || DEPS_OK=false

if [ "$DEPS_OK" = "false" ]; then
    echo ""
    echo -e "${YELLOW}安装缺失的依赖后重新运行此脚本${NC}"
    exit 1
fi

echo ""

# 1. 启动 Prometheus（本地）
start_prometheus() {
    echo -e "${BLUE}启动 Prometheus (本地)...${NC}"

    # 创建本地 Prometheus 配置
    cat > /tmp/prometheus-local.yml << 'EOF'
global:
  scrape_interval: 5s
  evaluation_interval: 5s

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'meta-driven'
    scrape_interval: 5s
    scrape_timeout: 3s
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
        labels:
          application: 'meta-driven'
          environment: 'local'
EOF

    # 检查是否已运行
    if pgrep -f "prometheus.*config" > /dev/null; then
        echo -e "${YELLOW}Prometheus 已在运行${NC}"
    else
        prometheus --config.file=/tmp/prometheus-local.yml \
            --storage.tsdb.path=/tmp/prometheus-data \
            --web.enable-lifecycle &
        echo $! > /tmp/prometheus.pid
        sleep 2
        echo -e "${GREEN}Prometheus 启动成功 - http://localhost:9090${NC}"
    fi
}

# 2. 启动 Grafana 等（Docker）
start_grafana_stack() {
    echo -e "${BLUE}启动 Grafana 生态系统 (Docker)...${NC}"

    cd "$PROJECT_DIR"

    # 只启动 Grafana, Loki, Tempo
    docker-compose -f docker-compose-observability.yml up -d \
        grafana loki tempo alertmanager promtail 2>&1 | tail -5

    echo -e "${GREEN}Grafana 启动成功 - http://localhost:3000${NC}"
    echo -e "${GREEN}  用户名: admin${NC}"
    echo -e "${GREEN}  密码: admin${NC}"
}

# 3. 配置 Grafana 数据源（使用宿主机 Prometheus）
configure_grafana_datasource() {
    echo -e "${BLUE}配置 Grafana 数据源...${NC}"

    # 等待 Grafana 启动
    sleep 5

    # 更新数据源配置（指向宿主机 Prometheus）
    cat > "$PROJECT_DIR/config/grafana/provisioning/datasources/datasources.yml" << 'EOF'
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    # macOS Docker Desktop: host.docker.internal 指向宿主机
    url: http://host.docker.internal:9090
    isDefault: true
    editable: true

  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    editable: true

  - name: Tempo
    type: tempo
    access: proxy
    url: http://tempo:3200
    editable: true
    jsonData:
      tracesToLogs:
        datasourceUid: loki
        tags: ['job', 'instance', 'pod', 'namespace']
        mappedTags: [{ key: 'service.name', value: 'service' }]
        mapTagNamesEnabled: false
        spanStartTimeShift: '1h'
        spanEndTimeShift: '1h'
        filterByTraceID: false
        filterBySpanID: false
      tracesToMetrics:
        datasourceUid: prometheus
        spanStartTimeShift: '1h'
        spanEndTimeShift: '-1h'
        tags: [{ key: 'service.name', value: 'service' }]
EOF

    # 重启 Grafana 加载新配置
    docker restart grafana 2>/dev/null || true

    echo -e "${GREEN}数据源配置完成${NC}"
}

# 显示状态
show_status() {
    echo ""
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}  服务状态${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""

    # Prometheus
    if curl -s http://localhost:9090/-/healthy > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Prometheus:   http://localhost:9090${NC}"
    else
        echo -e "${RED}✗ Prometheus:   未运行${NC}"
    fi

    # Grafana
    if curl -s http://localhost:3000/api/health > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Grafana:      http://localhost:3000  (admin/admin)${NC}"
    else
        echo -e "${YELLOW}⏳ Grafana:     启动中...${NC}"
    fi

    # Loki
    if curl -s http://localhost:3100/ready > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Loki:         http://localhost:3100${NC}"
    else
        echo -e "${YELLOW}⏳ Loki:        启动中...${NC}"
    fi

    # Tempo
    if curl -s http://localhost:3200/ready > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Tempo:        http://localhost:3200${NC}"
    else
        echo -e "${YELLOW}⏳ Tempo:       启动中...${NC}"
    fi

    # Application
    if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}✓ Application:  http://localhost:8080${NC}"
    else
        echo -e "${YELLOW}⏳ Application: 未运行 (请启动: ./mvnw spring-boot:run)${NC}"
    fi

    echo ""
    echo -e "${BLUE}============================================${NC}"
    echo -e "${GREEN}  可观测性环境已就绪！${NC}"
    echo -e "${BLUE}============================================${NC}"
    echo ""
    echo "验证步骤:"
    echo "  1. 启动应用: ./mvnw spring-boot:run -Dspring-boot.run.profiles=observability"
    echo "  2. 生成测试数据: curl http://localhost:8080/api/observability/generate?count=100"
    echo "  3. 打开 Grafana: http://localhost:3000"
    echo "  4. 查看 Prometheus 指标: http://localhost:9090/targets"
    echo ""
}

# 停止所有服务
stop_all() {
    echo -e "${BLUE}停止所有服务...${NC}"

    # 停止本地 Prometheus
    if [ -f /tmp/prometheus.pid ]; then
        kill $(cat /tmp/prometheus.pid) 2>/dev/null || true
        rm /tmp/prometheus.pid
    fi
    pkill -f "prometheus.*config" 2>/dev/null || true

    # 停止 Docker 服务
    cd "$PROJECT_DIR"
    docker-compose -f docker-compose-observability.yml down 2>/dev/null || true

    echo -e "${GREEN}所有服务已停止${NC}"
}

# 主函数
main() {
    case "${1:-start}" in
        start)
            start_prometheus
            start_grafana_stack
            configure_grafana_datasource
            sleep 3
            show_status
            ;;
        stop)
            stop_all
            ;;
        status)
            show_status
            ;;
        *)
            echo "用法: $0 {start|stop|status}"
            exit 1
            ;;
    esac
}

main "$@"
