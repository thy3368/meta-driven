⏺ 应用已成功运行！可观测性系统已正常工作：

验证结果:

1. Health Check - http://localhost:8080/actuator/health ✅
   - 状态: UP
2. Prometheus Metrics - http://localhost:8080/actuator/prometheus ✅
   - 显示了自定义的低延迟指标:
    - business_latency_distribution_microseconds
    - business_latency_jitter_nanoseconds
    - business_latency_max_nanoseconds
    - business_processing_time_seconds
3. Test Endpoint - http://localhost:8080/api/observability/test ✅
   - 返回: {"delayMs":50,"status":"ok","latencyNanos":54013750}

启动命令总结

# 启动应用（使用可观测性配置）
./mvnw spring-boot:run -Dspring-boot.run.profiles=observability

# 启动可观测性基础设施（可选，需要 Docker）
docker-compose -f docker-compose-observability.yml up -d

访问端点

| 端点                 | URL                                                        |
  |--------------------|------------------------------------------------------------|
| Health             | http://localhost:8080/actuator/health                      |
| Prometheus Metrics | http://localhost:8080/actuator/prometheus                  |
| Test API           | http://localhost:8080/api/observability/test               |
| Random Latency     | http://localhost:8080/api/observability/random-latency     |
| Generate Data      | http://localhost:8080/api/observability/generate?count=100 |


All observability services are now healthy and running:
- Prometheus: http://localhost:9090
- Loki: http://localhost:3100
- Tempo: http://localhost:3200
- Grafana: http://localhost:3000
- Alertmanager: http://localhost:9093
- Promtail: running (log collector)


下一步操作

1. 安装本地 Prometheus (macOS):
   brew install prometheus
   ./scripts/start-observability-local.sh
2. 或者直接在 Grafana 中添加数据源:
   - 打开 http://localhost:3000
   - Configuration → Data Sources → Add data source
   - 选择 Prometheus，URL 填 http://host.docker.internal:8080/actuator/prometheus
   - 直接使用 Prometheus 指标表达式查询
3. 验证端点:
   - 健康检查: curl http://localhost:8080/actuator/health
   - 指标: curl http://localhost:8080/actuator/prometheus
   - 生成测试数据: curl "http://localhost:8080/api/observability/generate?count=100"


