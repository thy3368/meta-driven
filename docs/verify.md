验证 Metrics 的方法

1. 启动应用并访问 Prometheus 端点

# 启动应用
./mvnw spring-boot:run

启动后，访问以下端点查看指标：

- Prometheus 格式指标: http://localhost:8080/actuator/prometheus
- 所有指标列表: http://localhost:8080/actuator/metrics
- 具体指标详情: http://localhost:8080/actuator/metrics/{metric.name}

2. 使用测试端点生成指标数据

项目提供了 ObservabilityTestController，可以快速生成测试数据：

# 基础测试
curl http://localhost:8080/api/observability/test?delayMs=50

# 生成随机延迟数据
curl http://localhost:8080/api/observability/random-latency

# 批量生成测试数据（生成100个数据点）
curl http://localhost:8080/api/observability/generate?count=100

# 健康检查（包含关键指标摘要）
curl http://localhost:8080/api/observability/health

# 测试错误指标
curl "http://localhost:8080/api/observability/error?throwError=true"

# 测试超时指标
curl http://localhost:8080/api/observability/timeout?timeoutMs=3000

3. 验证关键业务指标

根据你的代码，以下是主要的 Metrics：

CQRS 业务指标 (BusinessMetrics.java:22-44)

# 查看命令总数
curl http://localhost:8080/actuator/metrics/cqrs.commands.total

# 查看查询总数
curl http://localhost:8080/actuator/metrics/cqrs.queries.total

# 查看命令延迟（包含 P50/P95/P99/P99.9）
curl http://localhost:8080/actuator/metrics/cqrs.command.latency

# 查看活跃命令数
curl http://localhost:8080/actuator/metrics/cqrs.commands.active

低延迟指标 (LowLatencyMetrics.java:25-66)

# 处理时间（纳秒精度，含 P50/P90/P95/P99/P99.9/P99.99）
curl http://localhost:8080/actuator/metrics/business.processing.time

# 延迟分布（微秒单位）
curl http://localhost:8080/actuator/metrics/business.latency.distribution

# 超时计数
curl http://localhost:8080/actuator/metrics/business.timeout.total

# 最大延迟（当前窗口）
curl http://localhost:8080/actuator/metrics/business.latency.max

# 最小延迟（当前窗口）
curl http://localhost:8080/actuator/metrics/business.latency.min

# 延迟抖动
curl http://localhost:8080/actuator/metrics/business.latency.jitter

4. 使用脚本批量验证

创建一个验证脚本：

#!/bin/bash

echo "=== Metrics 验证脚本 ==="
echo ""

# 1. 生成测试数据
echo "1. 生成测试数据..."
for i in {1..10}; do
curl -s http://localhost:8080/api/observability/random-latency > /dev/null
echo -n "."
done
echo " 完成"

# 2. 验证 Prometheus 端点
echo ""
echo "2. 验证 Prometheus 端点..."
curl -s http://localhost:8080/actuator/prometheus | grep -E "cqrs_|business_" | head -20

# 3. 查看关键指标
echo ""
echo "3. 关键指标摘要..."
curl -s http://localhost:8080/api/observability/health | jq '.'

# 4. 验证百分位数指标
echo ""
echo "4. 延迟百分位数..."
curl -s http://localhost:8080/actuator/metrics/business.processing.time | jq '.measurements[] | select(.statistic | contains("PERCENTILE"))'

echo ""
echo "=== 验证完成 ==="

保存为 verify-metrics.sh，然后运行：

chmod +x verify-metrics.sh
./verify-metrics.sh

5. 使用持续负载测试

# 使用 watch 持续监控健康端点
watch -n 1 'curl -s http://localhost:8080/api/observability/health | jq .'

# 在另一个终端持续生成负载
while true; do
curl -s http://localhost:8080/api/observability/random-latency > /dev/null
sleep 0.1
done

6. 集成 Prometheus + Grafana（推荐）

创建 docker-compose.yml:

version: '3.8'
services:
prometheus:
image: prom/prometheus:latest
ports:
- "9090:9090"
volumes:
- ./prometheus.yml:/etc/prometheus/prometheus.yml
command:
- '--config.file=/etc/prometheus/prometheus.yml'

    grafana:
      image: grafana/grafana:latest
      ports:
        - "3000:3000"
      environment:
        - GF_SECURITY_ADMIN_PASSWORD=admin

创建 prometheus.yml:

global:
scrape_interval: 5s

scrape_configs:
- job_name: 'spring-boot-app'
metrics_path: '/actuator/prometheus'
static_configs:
- targets: ['host.docker.internal:8080']

启动监控栈：

docker-compose up -d

# 访问 Prometheus: http://localhost:9090
# 访问 Grafana: http://localhost:3000 (admin/admin)

7. 验证指标的准确性

# 生成已知延迟的请求并验证指标
echo "发送 100ms 延迟请求..."
curl "http://localhost:8080/api/observability/test?delayMs=100"

# 等待几秒后查看指标
sleep 3
curl -s http://localhost:8080/actuator/metrics/business.processing.time | jq '.measurements[] | select(.statistic == "MAX")'

8. 检查指标完整性

验证所有期望的指标都已注册：

# 列出所有自定义指标
curl -s http://localhost:8080/actuator/metrics | jq '.names[] | select(startswith("cqrs") or startswith("business"))'

# 预期输出:
# "cqrs.commands.total"
# "cqrs.queries.total"
# "cqrs.command.latency"
# "cqrs.commands.active"
# "business.processing.time"
# "business.latency.distribution"
# "business.timeout.total"
# "business.latency.max"
# "business.latency.min"
# "business.latency.jitter"

9. 性能基准验证（符合 CLAUDE.md 标准）

根据你的低延迟要求，验证性能指标：

# 生成 1000 个样本并分析延迟分布
curl "http://localhost:8080/api/observability/generate?count=1000"

# 查看 P99.9 和 P99.99 延迟
curl -s http://localhost:8080/actuator/metrics/business.processing.time | \
jq '.measurements[] | select(.statistic == "0.999 percentile" or .statistic == "0.9999 percentile")'

总结

最快的验证流程：

# 1. 启动应用
./mvnw spring-boot:run

# 2. 等待启动完成，然后生成测试数据
curl "http://localhost:8080/api/observability/generate?count=100"

# 3. 查看健康摘要
curl http://localhost:8080/api/observability/health | jq

# 4. 查看 Prometheus 格式指标
curl http://localhost:8080/actuator/prometheus | grep -E "cqrs_|business_"
