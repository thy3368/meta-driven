#!/bin/bash
# ============================================
# 可观测性验证脚本
# 验证 Prometheus, Loki, Tempo, Grafana 是否正常工作
# ============================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
APP_URL="${APP_URL:-http://localhost:8080}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
LOKI_URL="${LOKI_URL:-http://localhost:3100}"
TEMPO_URL="${TEMPO_URL:-http://localhost:3200}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3000}"
ALERTMANAGER_URL="${ALERTMANAGER_URL:-http://localhost:9093}"

# 计数器
PASSED=0
FAILED=0
TOTAL=0

# 打印函数
print_header() {
    echo ""
    echo "============================================"
    echo "$1"
    echo "============================================"
}

check_service() {
    local name=$1
    local url=$2
    local endpoint=$3

    TOTAL=$((TOTAL + 1))

    echo -n "检查 $name ($url$endpoint)... "

    if curl -s -f "$url$endpoint" > /dev/null 2>&1; then
        echo -e "${GREEN}✓ 通过${NC}"
        PASSED=$((PASSED + 1))
        return 0
    else
        echo -e "${RED}✗ 失败${NC}"
        FAILED=$((FAILED + 1))
        return 1
    fi
}

check_metric() {
    local metric_name=$1

    TOTAL=$((TOTAL + 1))

    echo -n "检查指标 $metric_name... "

    result=$(curl -s "${PROMETHEUS_URL}/api/v1/query?query=${metric_name}" | grep -o '"status":"success"')

    if [ -n "$result" ]; then
        echo -e "${GREEN}✓ 存在${NC}"
        PASSED=$((PASSED + 1))
        return 0
    else
        echo -e "${YELLOW}⚠ 未找到${NC}"
        FAILED=$((FAILED + 1))
        return 1
    fi
}

# ============================================
# 1. 基础服务检查
# ============================================
print_header "1. 基础服务健康检查"

check_service "应用程序" "$APP_URL" "/actuator/health" || true
check_service "Prometheus" "$PROMETHEUS_URL" "/-/healthy" || true
check_service "Loki" "$LOKI_URL" "/ready" || true
check_service "Tempo" "$TEMPO_URL" "/ready" || true
check_service "Grafana" "$GRAFANA_URL" "/api/health" || true
check_service "Alertmanager" "$ALERTMANAGER_URL" "/-/healthy" || true

# ============================================
# 2. Prometheus 指标端点检查
# ============================================
print_header "2. Prometheus 指标端点检查"

check_service "应用 Prometheus 端点" "$APP_URL" "/actuator/prometheus" || true

# ============================================
# 3. 核心指标存在性检查
# ============================================
print_header "3. 核心指标存在性检查"

echo "等待 10 秒让 Prometheus 抓取指标..."
sleep 10

check_metric "up" || true
check_metric "jvm_memory_used_bytes" || true
check_metric "http_server_requests_seconds_count" || true
check_metric "jvm_gc_pause_seconds_count" || true
check_metric "jvm_threads_live_threads" || true

# ============================================
# 4. Loki 日志检查
# ============================================
print_header "4. Loki 日志检查"

TOTAL=$((TOTAL + 1))
echo -n "检查 Loki 日志查询... "

loki_result=$(curl -s "${LOKI_URL}/loki/api/v1/labels" | grep -o '"status":"success"')

if [ -n "$loki_result" ]; then
    echo -e "${GREEN}✓ Loki 可查询${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${YELLOW}⚠ Loki 查询失败${NC}"
    FAILED=$((FAILED + 1))
fi

# ============================================
# 5. Tempo 链路追踪检查
# ============================================
print_header "5. Tempo 链路追踪检查"

TOTAL=$((TOTAL + 1))
echo -n "检查 Tempo 服务状态... "

tempo_result=$(curl -s "${TEMPO_URL}/status/version" | grep -o '"version"')

if [ -n "$tempo_result" ]; then
    echo -e "${GREEN}✓ Tempo 运行正常${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${YELLOW}⚠ Tempo 状态未知${NC}"
    FAILED=$((FAILED + 1))
fi

# ============================================
# 6. Grafana 数据源检查
# ============================================
print_header "6. Grafana 数据源检查"

TOTAL=$((TOTAL + 1))
echo -n "检查 Grafana 数据源... "

grafana_ds=$(curl -s "${GRAFANA_URL}/api/datasources" -u admin:admin 2>/dev/null | grep -o '"name"')

if [ -n "$grafana_ds" ]; then
    echo -e "${GREEN}✓ 数据源已配置${NC}"
    PASSED=$((PASSED + 1))
else
    echo -e "${YELLOW}⚠ 数据源配置未知${NC}"
    FAILED=$((FAILED + 1))
fi

# ============================================
# 7. 生成测试流量
# ============================================
print_header "7. 生成测试流量"

echo "发送测试请求到应用程序..."

for i in {1..10}; do
    curl -s "$APP_URL/api/observability/test?delayMs=50" > /dev/null 2>&1 || true
    curl -s "$APP_URL/api/observability/random-latency" > /dev/null 2>&1 || true
done

echo "测试流量已发送"

# ============================================
# 8. 生成批量测试数据
# ============================================
print_header "8. 生成批量测试数据"

echo "生成 100 个测试数据点..."
curl -s "$APP_URL/api/observability/generate?count=100" || echo "生成测试数据失败"

# ============================================
# 结果汇总
# ============================================
print_header "验证结果汇总"

echo "总检查项: $TOTAL"
echo -e "通过: ${GREEN}$PASSED${NC}"
echo -e "失败: ${RED}$FAILED${NC}"

if [ $FAILED -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✓ 所有检查通过！可观测性系统运行正常。${NC}"
    exit 0
else
    echo ""
    echo -e "${YELLOW}⚠ 部分检查失败，请检查相关服务。${NC}"
    exit 1
fi
