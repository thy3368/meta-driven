#!/bin/bash
# ============================================
# 可观测性性能基准测试脚本
# 测试可观测性组件的性能开销
# ============================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
APP_URL="${APP_URL:-http://localhost:8080}"
CONCURRENT_USERS="${CONCURRENT_USERS:-10}"
REQUESTS_PER_USER="${REQUESTS_PER_USER:-100}"
WARMUP_REQUESTS="${WARMUP_REQUESTS:-50}"

print_header() {
    echo ""
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}============================================${NC}"
}

# 检查依赖
check_dependencies() {
    print_header "检查依赖"

    # 检查 curl
    if ! command -v curl &> /dev/null; then
        echo -e "${RED}错误: 需要 curl${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ curl 已安装${NC}"

    # 检查 ab (Apache Bench) - 可选
    if command -v ab &> /dev/null; then
        echo -e "${GREEN}✓ Apache Bench 已安装${NC}"
        HAS_AB=true
    else
        echo -e "${YELLOW}⚠ Apache Bench 未安装 (使用基础测试)${NC}"
        HAS_AB=false
    fi

    # 检查 jq - 可选
    if command -v jq &> /dev/null; then
        echo -e "${GREEN}✓ jq 已安装${NC}"
        HAS_JQ=true
    else
        echo -e "${YELLOW}⚠ jq 未安装 (使用基础 JSON 解析)${NC}"
        HAS_JQ=false
    fi
}

# 预热
warmup() {
    print_header "预热阶段 ($WARMUP_REQUESTS 请求)"

    for i in $(seq 1 $WARMUP_REQUESTS); do
        curl -s "$APP_URL/api/observability/test?delayMs=10" > /dev/null 2>&1 || true
    done

    echo "预热完成"
    sleep 2
}

# 基础延迟测试
test_basic_latency() {
    print_header "基础延迟测试 (单线程)"

    local total_time=0
    local count=100
    local min_time=999999
    local max_time=0

    echo "发送 $count 个请求..."

    for i in $(seq 1 $count); do
        # 使用 curl 测量时间 (毫秒)
        response_time=$(curl -s -o /dev/null -w "%{time_total}" "$APP_URL/api/observability/test?delayMs=1")
        response_time_ms=$(echo "$response_time * 1000" | bc)

        total_time=$(echo "$total_time + $response_time_ms" | bc)

        # 更新最小/最大值
        if (( $(echo "$response_time_ms < $min_time" | bc -l) )); then
            min_time=$response_time_ms
        fi
        if (( $(echo "$response_time_ms > $max_time" | bc -l) )); then
            max_time=$response_time_ms
        fi
    done

    avg_time=$(echo "scale=2; $total_time / $count" | bc)

    echo ""
    echo "结果:"
    echo "  请求数: $count"
    echo "  平均延迟: ${avg_time}ms"
    echo "  最小延迟: ${min_time}ms"
    echo "  最大延迟: ${max_time}ms"
}

# 使用 Apache Bench 进行并发测试
test_concurrent_ab() {
    print_header "并发测试 (Apache Bench)"

    if [ "$HAS_AB" != "true" ]; then
        echo -e "${YELLOW}跳过: Apache Bench 未安装${NC}"
        return
    fi

    local total_requests=$((CONCURRENT_USERS * REQUESTS_PER_USER))

    echo "参数:"
    echo "  并发用户: $CONCURRENT_USERS"
    echo "  每用户请求: $REQUESTS_PER_USER"
    echo "  总请求: $total_requests"
    echo ""

    # 运行 Apache Bench
    ab -n $total_requests -c $CONCURRENT_USERS -q "$APP_URL/api/observability/test?delayMs=1" 2>&1 | grep -E "(Requests per second|Time per request|Percentage)"
}

# 并发测试 (不使用 ab)
test_concurrent_basic() {
    print_header "并发测试 (基础)"

    local total_requests=$((CONCURRENT_USERS * REQUESTS_PER_USER))

    echo "参数:"
    echo "  并发用户: $CONCURRENT_USERS"
    echo "  每用户请求: $REQUESTS_PER_USER"
    echo "  总请求: $total_requests"
    echo ""

    local start_time=$(date +%s.%N)

    # 使用后台进程模拟并发
    for user in $(seq 1 $CONCURRENT_USERS); do
        (
            for req in $(seq 1 $REQUESTS_PER_USER); do
                curl -s "$APP_URL/api/observability/random-latency" > /dev/null 2>&1
            done
        ) &
    done

    # 等待所有后台进程完成
    wait

    local end_time=$(date +%s.%N)
    local duration=$(echo "$end_time - $start_time" | bc)
    local rps=$(echo "scale=2; $total_requests / $duration" | bc)

    echo "结果:"
    echo "  总耗时: ${duration}s"
    echo "  吞吐量: ${rps} req/s"
}

# 可观测性开销测试
test_observability_overhead() {
    print_header "可观测性开销测试"

    echo "测试带可观测性的端点..."

    # 测试带完整可观测性的端点
    local obs_times=()
    for i in $(seq 1 50); do
        time=$(curl -s -o /dev/null -w "%{time_total}" "$APP_URL/api/observability/test?delayMs=0")
        obs_times+=($time)
    done

    # 计算平均值
    local obs_sum=0
    for t in "${obs_times[@]}"; do
        obs_sum=$(echo "$obs_sum + $t" | bc)
    done
    local obs_avg=$(echo "scale=4; $obs_sum / 50" | bc)

    echo "测试健康检查端点 (最小开销)..."

    # 测试健康检查端点 (最小开销)
    local health_times=()
    for i in $(seq 1 50); do
        time=$(curl -s -o /dev/null -w "%{time_total}" "$APP_URL/actuator/health")
        health_times+=($time)
    done

    # 计算平均值
    local health_sum=0
    for t in "${health_times[@]}"; do
        health_sum=$(echo "$health_sum + $t" | bc)
    done
    local health_avg=$(echo "scale=4; $health_sum / 50" | bc)

    echo ""
    echo "结果:"
    echo "  带可观测性端点平均延迟: ${obs_avg}s"
    echo "  健康检查端点平均延迟: ${health_avg}s"

    local overhead=$(echo "scale=4; ($obs_avg - $health_avg) * 1000" | bc)
    echo "  可观测性开销估算: ${overhead}ms"
}

# 指标端点性能测试
test_metrics_endpoint() {
    print_header "指标端点性能测试"

    echo "测试 /actuator/prometheus 端点响应时间..."

    local times=()
    for i in $(seq 1 20); do
        time=$(curl -s -o /dev/null -w "%{time_total}" "$APP_URL/actuator/prometheus")
        times+=($time)
    done

    # 计算平均值
    local sum=0
    for t in "${times[@]}"; do
        sum=$(echo "$sum + $t" | bc)
    done
    local avg=$(echo "scale=4; $sum / 20 * 1000" | bc)

    echo "结果:"
    echo "  平均响应时间: ${avg}ms"

    # 获取指标数量
    local metric_count=$(curl -s "$APP_URL/actuator/prometheus" | wc -l)
    echo "  指标行数: $metric_count"
}

# 内存开销检查
check_memory_overhead() {
    print_header "内存开销检查"

    echo "获取 JVM 内存指标..."

    # 获取堆内存使用
    heap_used=$(curl -s "$APP_URL/actuator/metrics/jvm.memory.used?tag=area:heap" 2>/dev/null)

    if [ "$HAS_JQ" = "true" ]; then
        heap_bytes=$(echo "$heap_used" | jq -r '.measurements[0].value // "N/A"')
        heap_mb=$(echo "scale=2; $heap_bytes / 1024 / 1024" | bc 2>/dev/null || echo "N/A")
        echo "  堆内存使用: ${heap_mb}MB"
    else
        echo "  (安装 jq 以显示详细内存信息)"
    fi

    # 获取线程数
    thread_count=$(curl -s "$APP_URL/actuator/metrics/jvm.threads.live" 2>/dev/null)

    if [ "$HAS_JQ" = "true" ]; then
        threads=$(echo "$thread_count" | jq -r '.measurements[0].value // "N/A"')
        echo "  活跃线程数: $threads"
    fi
}

# 主函数
main() {
    echo ""
    echo "============================================"
    echo "  可观测性性能基准测试"
    echo "============================================"
    echo ""
    echo "目标: $APP_URL"
    echo ""

    check_dependencies
    warmup
    test_basic_latency
    test_observability_overhead
    test_metrics_endpoint
    check_memory_overhead

    if [ "$HAS_AB" = "true" ]; then
        test_concurrent_ab
    else
        test_concurrent_basic
    fi

    print_header "测试完成"
    echo -e "${GREEN}所有基准测试已完成${NC}"
}

# 运行
main "$@"
