# 完整可观测性方案

## 一、架构总览

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           可观测性平台                                    │
├───────────────────┬───────────────────┬─────────────────────────────────┤
│     Metrics       │      Logging      │          Tracing                │
│    (指标监控)      │     (日志系统)     │         (链路追踪)               │
├───────────────────┼───────────────────┼─────────────────────────────────┤
│   Prometheus      │       Loki        │           Tempo                 │
│       ↓           │         ↓         │             ↓                   │
└───────────────────┴───────────────────┴─────────────────────────────────┘
                              │
                      ┌───────▼───────┐
                      │    Grafana    │  ← 统一可视化面板
                      └───────────────┘
                              ↑
         ┌────────────────────┼────────────────────┐
         │                    │                    │
    /metrics            /logs (JSON)         Trace Context
    (Prometheus)        (Logback)            (OpenTelemetry)
         │                    │                    │
         └────────────────────┼────────────────────┘
                              │
                    ┌─────────▼─────────┐
                    │   Spring Boot 4   │
                    │   meta-driven     │
                    │                   │
                    │ • Micrometer      │
                    │ • OTEL Tracing    │
                    │ • Logback JSON    │
                    └───────────────────┘
```

---

## 二、技术选型

| 支柱 | 采集端 | 存储端 | 可视化 | 选型理由 |
|------|--------|--------|--------|----------|
| **Metrics** | Micrometer | Prometheus | Grafana | 业界标准，与 Spring Boot 深度集成 |
| **Logging** | Logback + JSON | Loki | Grafana | 轻量级，与 Grafana 生态统一 |
| **Tracing** | Micrometer Tracing + OTEL | Tempo | Grafana | 云原生标准，支持 TraceID 关联 |

**备选方案**：
- Logging: ELK Stack (Elasticsearch + Logstash + Kibana) - 功能更强但资源消耗大
- Tracing: Jaeger / Zipkin - 成熟稳定，但 Tempo 与 Grafana 集成更好

---

## 三、依赖清单

```xml
<!-- pom.xml 新增依赖 -->

<!-- 1. Actuator - 暴露管理端点 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- 2. Prometheus Registry - 指标导出 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- 3. Micrometer Tracing + OpenTelemetry -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>

<!-- 4. Logback JSON Encoder -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
</dependency>
```

---

## 四、配置方案

### 4.1 application.yml

```yaml
spring:
  application:
    name: meta-driven

# ============================================
# Actuator & Metrics 配置
# ============================================
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,loggers
      base-path: /actuator
  endpoint:
    health:
      show-details: when_authorized
      probes:
        enabled: true  # Kubernetes 探针支持
    prometheus:
      enabled: true

  metrics:
    distribution:
      percentiles-histogram:
        http.server.requests: true  # 请求延迟直方图
      percentiles:
        http.server.requests: 0.5, 0.95, 0.99, 0.999  # P50/P95/P99/P999
      minimum-expected-value:
        http.server.requests: 1ms
      maximum-expected-value:
        http.server.requests: 10s
    tags:
      application: ${spring.application.name}
      environment: ${ENVIRONMENT:local}

# ============================================
# Tracing 配置 (OpenTelemetry)
# ============================================
  tracing:
    sampling:
      probability: 1.0  # 生产环境建议 0.1 (10% 采样)
    propagation:
      type: w3c  # W3C Trace Context 标准

  otlp:
    tracing:
      endpoint: http://tempo:4318/v1/traces  # Tempo OTLP HTTP 端点

# ============================================
# Logging 配置
# ============================================
logging:
  level:
    root: INFO
    com.tanggo.fund: DEBUG
  pattern:
    correlation: "[${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

### 4.2 logback-spring.xml (结构化日志)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- 属性定义 -->
    <springProperty scope="context" name="APP_NAME" source="spring.application.name"/>
    <property name="LOG_PATH" value="${LOG_PATH:-./logs}"/>

    <!-- ============================================ -->
    <!-- 控制台输出 (开发环境) -->
    <!-- ============================================ -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %highlight(%-5level) [%X{traceId:-},%X{spanId:-}] %cyan(%logger{36}) - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- ============================================ -->
    <!-- JSON 格式输出 (生产环境 - Loki/ELK) -->
    <!-- ============================================ -->
    <appender name="JSON_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/${APP_NAME}.json</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/${APP_NAME}.%d{yyyy-MM-dd}.json.gz</fileNamePattern>
            <maxHistory>7</maxHistory>
            <totalSizeCap>1GB</totalSizeCap>
        </rollingPolicy>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"app":"${APP_NAME}","env":"${ENVIRONMENT:-local}"}</customFields>
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
        </encoder>
    </appender>

    <!-- ============================================ -->
    <!-- 异步日志 (低延迟优化) -->
    <!-- ============================================ -->
    <appender name="ASYNC_JSON" class="ch.qos.logback.classic.AsyncAppender">
        <queueSize>1024</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <neverBlock>true</neverBlock>
        <appender-ref ref="JSON_FILE"/>
    </appender>

    <!-- Profile 配置 -->
    <springProfile name="local,dev">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <springProfile name="prod,staging">
        <root level="INFO">
            <appender-ref ref="ASYNC_JSON"/>
        </root>
    </springProfile>
</configuration>
```

---

## 五、自定义指标组件

### 5.1 业务指标注册

```java
package com.tanggo.fund.metadriven.observability;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class BusinessMetrics {

    private final Counter commandCounter;
    private final Counter queryCounter;
    private final Timer commandLatency;
    private final AtomicLong activeCommands;

    public BusinessMetrics(MeterRegistry registry) {
        // 命令计数器
        this.commandCounter = Counter.builder("cqrs.commands.total")
            .description("Total commands processed")
            .tag("type", "command")
            .register(registry);

        // 查询计数器
        this.queryCounter = Counter.builder("cqrs.queries.total")
            .description("Total queries processed")
            .tag("type", "query")
            .register(registry);

        // 命令延迟 Timer
        this.commandLatency = Timer.builder("cqrs.command.latency")
            .description("Command processing latency")
            .publishPercentiles(0.5, 0.95, 0.99, 0.999)
            .publishPercentileHistogram()
            .register(registry);

        // 活跃命令数 Gauge
        this.activeCommands = new AtomicLong(0);
        Gauge.builder("cqrs.commands.active", activeCommands, AtomicLong::get)
            .description("Currently active commands")
            .register(registry);
    }

    public void recordCommand(String commandName, Runnable execution) {
        activeCommands.incrementAndGet();
        try {
            commandLatency.record(execution);
            commandCounter.increment();
        } finally {
            activeCommands.decrementAndGet();
        }
    }

    public void recordQuery() {
        queryCounter.increment();
    }
}
```

### 5.2 观测注解 AOP

```java
package com.tanggo.fund.metadriven.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ObservabilityAspect {

    private final ObservationRegistry observationRegistry;

    public ObservabilityAspect(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Around("@annotation(observed)")
    public Object observe(ProceedingJoinPoint pjp, Observed observed) throws Throwable {
        String name = observed.name().isEmpty()
            ? pjp.getSignature().getName()
            : observed.name();

        return Observation.createNotStarted(name, observationRegistry)
            .lowCardinalityKeyValue("class", pjp.getTarget().getClass().getSimpleName())
            .lowCardinalityKeyValue("method", pjp.getSignature().getName())
            .observe(() -> {
                try {
                    return pjp.proceed();
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });
    }
}

// 使用示例
// @Observed(name = "process.order")
// public Order processOrder(OrderCommand cmd) {
//     // 自动记录指标和链路
// }
```

---

## 六、基础设施部署

### 6.1 Docker Compose

```yaml
# docker-compose-observability.yml
version: '3.8'

services:
  # ============================================
  # Prometheus - 指标存储
  # ============================================
  prometheus:
    image: prom/prometheus:v2.47.0
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./config/prometheus.yml:/etc/prometheus/prometheus.yml
      - ./config/alert.rules.yml:/etc/prometheus/alert.rules.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=15d'
      - '--web.enable-lifecycle'
    networks:
      - observability

  # ============================================
  # Loki - 日志存储
  # ============================================
  loki:
    image: grafana/loki:2.9.0
    container_name: loki
    ports:
      - "3100:3100"
    volumes:
      - ./config/loki.yml:/etc/loki/local-config.yaml
      - loki_data:/loki
    command: -config.file=/etc/loki/local-config.yaml
    networks:
      - observability

  # ============================================
  # Promtail - 日志采集
  # ============================================
  promtail:
    image: grafana/promtail:2.9.0
    container_name: promtail
    volumes:
      - ./config/promtail.yml:/etc/promtail/config.yml
      - ./logs:/var/log/app  # 挂载应用日志目录
    command: -config.file=/etc/promtail/config.yml
    networks:
      - observability

  # ============================================
  # Tempo - 链路追踪存储
  # ============================================
  tempo:
    image: grafana/tempo:2.2.0
    container_name: tempo
    ports:
      - "3200:3200"   # Tempo Query
      - "4317:4317"   # OTLP gRPC
      - "4318:4318"   # OTLP HTTP
    volumes:
      - ./config/tempo.yml:/etc/tempo/tempo.yml
      - tempo_data:/var/tempo
    command: -config.file=/etc/tempo/tempo.yml
    networks:
      - observability

  # ============================================
  # Grafana - 统一可视化
  # ============================================
  grafana:
    image: grafana/grafana:10.1.0
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_FEATURE_TOGGLES_ENABLE=traceqlEditor
    volumes:
      - ./config/grafana/provisioning:/etc/grafana/provisioning
      - grafana_data:/var/lib/grafana
    depends_on:
      - prometheus
      - loki
      - tempo
    networks:
      - observability

networks:
  observability:
    driver: bridge

volumes:
  prometheus_data:
  loki_data:
  tempo_data:
  grafana_data:
```

### 6.2 Prometheus 配置

```yaml
# config/prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - /etc/prometheus/alert.rules.yml

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'meta-driven'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s
    static_configs:
      - targets: ['host.docker.internal:8080']
        labels:
          application: 'meta-driven'
          environment: 'local'
```

### 6.3 Loki 配置

```yaml
# config/loki.yml
auth_enabled: false

server:
  http_listen_port: 3100

common:
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2020-10-24
      store: boltdb-shipper
      object_store: filesystem
      schema: v11
      index:
        prefix: index_
        period: 24h

limits_config:
  enforce_metric_name: false
  reject_old_samples: true
  reject_old_samples_max_age: 168h
```

### 6.4 Promtail 配置

```yaml
# config/promtail.yml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: meta-driven
    static_configs:
      - targets:
          - localhost
        labels:
          job: meta-driven
          __path__: /var/log/app/*.json
    pipeline_stages:
      - json:
          expressions:
            level: level
            traceId: traceId
            spanId: spanId
            message: message
      - labels:
          level:
          traceId:
```

### 6.5 Tempo 配置

```yaml
# config/tempo.yml
server:
  http_listen_port: 3200

distributor:
  receivers:
    otlp:
      protocols:
        http:
        grpc:

ingester:
  trace_idle_period: 10s
  max_block_bytes: 1_000_000
  max_block_duration: 5m

compactor:
  compaction:
    compaction_window: 1h
    max_block_bytes: 100_000_000
    block_retention: 1h
    compacted_block_retention: 10m

storage:
  trace:
    backend: local
    local:
      path: /var/tempo/traces
    wal:
      path: /var/tempo/wal
```

### 6.6 Grafana 数据源配置

```yaml
# config/grafana/provisioning/datasources/datasources.yml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true

  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    jsonData:
      derivedFields:
        - datasourceUid: tempo
          matcherRegex: '"traceId":"(\w+)"'
          name: TraceID
          url: '$${__value.raw}'

  - name: Tempo
    type: tempo
    access: proxy
    uid: tempo
    url: http://tempo:3200
    jsonData:
      tracesToLogs:
        datasourceUid: loki
        filterByTraceID: true
      serviceMap:
        datasourceUid: prometheus
```

---

## 七、关键指标与告警

### 7.1 核心监控指标

| 类别 | 指标名 | 描述 | 告警阈值 |
|------|--------|------|----------|
| **RED 指标** | | | |
| Rate | `http_server_requests_seconds_count` | 请求速率 | N/A |
| Errors | `http_server_requests_seconds_count{status=~"5.."}` | 错误率 | > 1% |
| Duration | `http_server_requests_seconds{quantile="0.99"}` | P99 延迟 | > 500ms |
| **JVM 指标** | | | |
| Heap | `jvm_memory_used_bytes{area="heap"}` | 堆内存使用 | > 80% |
| GC | `jvm_gc_pause_seconds_max` | GC 暂停时间 | > 100ms |
| Threads | `jvm_threads_live_threads` | 活跃线程数 | > 500 |
| **业务指标** | | | |
| Commands | `cqrs_commands_total` | 命令总数 | N/A |
| Latency | `cqrs_command_latency_seconds{quantile="0.999"}` | P999 命令延迟 | > 10ms |

### 7.2 告警规则

```yaml
# config/alert.rules.yml
groups:
  - name: meta-driven-alerts
    rules:
      - alert: HighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
          / sum(rate(http_server_requests_seconds_count[5m])) > 0.01
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
          description: "Error rate is above 1% for 5 minutes"

      - alert: HighP99Latency
        expr: |
          histogram_quantile(0.99,
            sum(rate(http_server_requests_seconds_bucket[5m])) by (le)
          ) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "P99 latency above 500ms"

      - alert: HighGCPause
        expr: jvm_gc_pause_seconds_max > 0.1
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "GC pause time exceeds 100ms"

      - alert: HighHeapUsage
        expr: |
          jvm_memory_used_bytes{area="heap"}
          / jvm_memory_max_bytes{area="heap"} > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Heap usage above 80%"

      - alert: ServiceDown
        expr: up{job="meta-driven"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service is down"
```

---

## 八、TraceID 关联

实现日志、指标、链路的关联查询：

```
┌─────────────┐     TraceID      ┌─────────────┐
│   Grafana   │ ───────────────→ │    Tempo    │
│   (Logs)    │                  │  (Traces)   │
└──────┬──────┘                  └──────┬──────┘
       │                                │
       │ TraceID in JSON               │ Exemplars
       │                                │
       ▼                                ▼
┌─────────────┐                  ┌─────────────┐
│    Loki     │                  │ Prometheus  │
│ (日志存储)   │                  │ (指标存储)   │
└─────────────┘                  └─────────────┘
```

**日志中自动注入 TraceID**：
```json
{
  "@timestamp": "2025-01-15T10:30:00.123Z",
  "level": "INFO",
  "logger": "com.tanggo.fund.metadriven.OrderService",
  "message": "Order processed successfully",
  "traceId": "abc123def456",
  "spanId": "789xyz",
  "app": "meta-driven",
  "orderId": "ORD-001"
}
```

---

## 九、低延迟优化建议

针对低延迟场景的特殊优化：

| 优化项 | 配置 | 说明 |
|--------|------|------|
| **采样率** | `probability: 0.1` | 生产环境 10% 采样，降低开销 |
| **异步日志** | `AsyncAppender` | 日志异步写入，不阻塞主线程 |
| **批量导出** | OTLP batch exporter | Trace 批量发送，减少网络 IO |
| **指标推送** | Prometheus pull | 避免推送模式的锁竞争 |
| **堆外缓冲** | DirectByteBuffer | 日志/指标使用堆外内存 |

### 低延迟配置示例

```yaml
# 生产环境低延迟配置
management:
  tracing:
    sampling:
      probability: 0.1  # 10% 采样率

  metrics:
    export:
      prometheus:
        step: 30s  # 指标刷新间隔
```

```xml
<!-- logback 低延迟配置 -->
<appender name="ASYNC_JSON" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>2048</queueSize>
    <discardingThreshold>0</discardingThreshold>
    <neverBlock>true</neverBlock>  <!-- 关键：永不阻塞 -->
    <appender-ref ref="JSON_FILE"/>
</appender>
```

---

## 十、快速启动

```bash
# 1. 创建配置目录
mkdir -p config/grafana/provisioning/datasources

# 2. 复制配置文件（按照上述配置创建）
# config/prometheus.yml
# config/loki.yml
# config/promtail.yml
# config/tempo.yml
# config/alert.rules.yml
# config/grafana/provisioning/datasources/datasources.yml

# 3. 启动可观测性基础设施
docker-compose -f docker-compose-observability.yml up -d

# 4. 启动应用
./mvnw spring-boot:run

# 5. 访问面板
# Grafana: http://localhost:3000 (admin/admin)
# Prometheus: http://localhost:9090
# 应用指标: http://localhost:8080/actuator/prometheus
```

---

## 十一、Grafana Dashboard 推荐

| Dashboard | ID | 说明 |
|-----------|-----|------|
| Spring Boot Statistics | 12900 | Spring Boot 应用监控 |
| JVM Micrometer | 4701 | JVM 详细指标 |
| Prometheus Stats | 2 | Prometheus 自身监控 |

导入方式：Grafana → Dashboards → Import → 输入 ID

---

## 十二、Alertmanager 告警通知

### 12.1 Alertmanager 配置

```yaml
# config/alertmanager.yml
global:
  # 全局配置
  resolve_timeout: 5m
  # SMTP 配置（邮件通知）
  smtp_smarthost: 'smtp.example.com:587'
  smtp_from: 'alertmanager@example.com'
  smtp_auth_username: 'alertmanager@example.com'
  smtp_auth_password: 'password'

# 路由规则
route:
  group_by: ['alertname', 'severity']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 1h
  receiver: 'default-receiver'
  routes:
    # Critical 告警立即通知
    - match:
        severity: critical
      receiver: 'critical-receiver'
      group_wait: 0s
    # Warning 告警汇总通知
    - match:
        severity: warning
      receiver: 'warning-receiver'
      group_wait: 5m

# 接收器配置
receivers:
  - name: 'default-receiver'
    webhook_configs:
      - url: 'http://localhost:8080/webhook/alert'
        send_resolved: true

  - name: 'critical-receiver'
    # 钉钉机器人
    webhook_configs:
      - url: 'https://oapi.dingtalk.com/robot/send?access_token=YOUR_TOKEN'
        send_resolved: true
    # 邮件通知
    email_configs:
      - to: 'oncall@example.com'
        send_resolved: true

  - name: 'warning-receiver'
    # Slack 通知
    slack_configs:
      - api_url: 'https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK'
        channel: '#alerts'
        title: '{{ .GroupLabels.alertname }}'
        text: '{{ range .Alerts }}{{ .Annotations.summary }}{{ end }}'
        send_resolved: true

# 抑制规则（Critical 存在时抑制 Warning）
inhibit_rules:
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['alertname']
```

### 12.2 钉钉告警模板

```yaml
# config/dingtalk-template.yml
# 需要配合 prometheus-webhook-dingtalk 使用
templates:
  - name: 'dingtalk.default.message'
    text: |
      {{ if gt (len .Alerts.Firing) 0 }}
      ## 🔥 告警触发 ({{ len .Alerts.Firing }})
      {{ range .Alerts.Firing }}
      **告警名称**: {{ .Labels.alertname }}
      **严重级别**: {{ .Labels.severity }}
      **告警摘要**: {{ .Annotations.summary }}
      **告警详情**: {{ .Annotations.description }}
      **触发时间**: {{ .StartsAt.Format "2006-01-02 15:04:05" }}
      ---
      {{ end }}
      {{ end }}

      {{ if gt (len .Alerts.Resolved) 0 }}
      ## ✅ 告警恢复 ({{ len .Alerts.Resolved }})
      {{ range .Alerts.Resolved }}
      **告警名称**: {{ .Labels.alertname }}
      **恢复时间**: {{ .EndsAt.Format "2006-01-02 15:04:05" }}
      ---
      {{ end }}
      {{ end }}
```

### 12.3 Docker Compose 添加 Alertmanager

```yaml
  # ============================================
  # Alertmanager - 告警通知
  # ============================================
  alertmanager:
    image: prom/alertmanager:v0.26.0
    container_name: alertmanager
    ports:
      - "9093:9093"
    volumes:
      - ./config/alertmanager.yml:/etc/alertmanager/alertmanager.yml
      - alertmanager_data:/alertmanager
    command:
      - '--config.file=/etc/alertmanager/alertmanager.yml'
      - '--storage.path=/alertmanager'
    networks:
      - observability
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:9093/-/healthy"]
      interval: 30s
      timeout: 10s
      retries: 3

  # ============================================
  # 钉钉告警网关（可选）
  # ============================================
  dingtalk:
    image: timonwong/prometheus-webhook-dingtalk:v2.1.0
    container_name: dingtalk
    ports:
      - "8060:8060"
    volumes:
      - ./config/dingtalk.yml:/etc/prometheus-webhook-dingtalk/config.yml
    command:
      - '--config.file=/etc/prometheus-webhook-dingtalk/config.yml'
    networks:
      - observability
```

### 12.4 更新 Prometheus 配置连接 Alertmanager

```yaml
# config/prometheus.yml 添加
alerting:
  alertmanagers:
    - static_configs:
        - targets:
            - alertmanager:9093
```

---

## 十三、方案验证

### 13.1 验证架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                        验证金字塔                                    │
├─────────────────────────────────────────────────────────────────────┤
│  Level 4: 端到端验证 (E2E)                                           │
│    - 模拟故障 → 告警触发 → 通知到达                                    │
├─────────────────────────────────────────────────────────────────────┤
│  Level 3: 关联性验证                                                 │
│    - TraceID 从日志跳转到 Trace                                      │
│    - Trace 关联到 Metrics                                           │
├─────────────────────────────────────────────────────────────────────┤
│  Level 2: 数据流验证                                                 │
│    - App → Prometheus 指标可见                                      │
│    - App → Loki 日志可查                                            │
│    - App → Tempo 链路可追踪                                         │
├─────────────────────────────────────────────────────────────────────┤
│  Level 1: 基础设施验证                                               │
│    - 所有容器正常运行                                                │
│    - 端口可访问                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 13.2 验证脚本

```bash
#!/bin/bash
# scripts/verify-observability.sh

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_status() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}[PASS]${NC} $2"
    else
        echo -e "${RED}[FAIL]${NC} $2"
    fi
}

print_header() {
    echo -e "\n${YELLOW}========== $1 ==========${NC}"
}

# ============================================
# Level 1: 基础设施验证
# ============================================
print_header "Level 1: 基础设施验证"

# 1.1 检查容器状态
echo "检查 Docker 容器状态..."
containers=("prometheus" "loki" "tempo" "grafana" "alertmanager")
for container in "${containers[@]}"; do
    status=$(docker inspect -f '{{.State.Running}}' $container 2>/dev/null || echo "false")
    if [ "$status" = "true" ]; then
        print_status 0 "$container 运行中"
    else
        print_status 1 "$container 未运行"
    fi
done

# 1.2 检查服务健康状态
echo -e "\n检查服务健康状态..."

check_health() {
    local name=$1
    local url=$2
    local code=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
    if [ "$code" = "200" ] || [ "$code" = "204" ]; then
        print_status 0 "$name 健康 (HTTP $code)"
        return 0
    else
        print_status 1 "$name 不健康 (HTTP $code)"
        return 1
    fi
}

check_health "Prometheus" "http://localhost:9090/-/healthy"
check_health "Loki" "http://localhost:3100/ready"
check_health "Tempo" "http://localhost:3200/ready"
check_health "Grafana" "http://localhost:3000/api/health"
check_health "Alertmanager" "http://localhost:9093/-/healthy"

# ============================================
# Level 2: 数据流验证
# ============================================
print_header "Level 2: 数据流验证"

# 2.1 验证 Prometheus 抓取目标
echo "检查 Prometheus 抓取目标..."
targets=$(curl -s "http://localhost:9090/api/v1/targets" | jq -r '.data.activeTargets[] | select(.labels.job=="meta-driven") | .health')
if [ "$targets" = "up" ]; then
    print_status 0 "Prometheus 抓取 meta-driven 正常"
else
    print_status 1 "Prometheus 抓取 meta-driven 失败"
fi

# 2.2 验证应用指标端点
echo "检查应用指标端点..."
metrics=$(curl -s "http://localhost:8080/actuator/prometheus" 2>/dev/null | grep -c "jvm_memory" || echo "0")
if [ "$metrics" -gt 0 ]; then
    print_status 0 "应用指标端点正常 (找到 $metrics 个 JVM 指标)"
else
    print_status 1 "应用指标端点异常"
fi

# 2.3 验证 Loki 日志
echo "检查 Loki 日志..."
log_count=$(curl -s 'http://localhost:3100/loki/api/v1/query?query={job="meta-driven"}' | jq '.data.result | length')
if [ "$log_count" -gt 0 ]; then
    print_status 0 "Loki 日志正常 (找到 $log_count 个日志流)"
else
    print_status 1 "Loki 未收到日志"
fi

# 2.4 验证 Tempo 链路
echo "检查 Tempo 链路..."
trace_count=$(curl -s "http://localhost:3200/api/search?limit=1" | jq '.traces | length' 2>/dev/null || echo "0")
if [ "$trace_count" -gt 0 ]; then
    print_status 0 "Tempo 链路正常"
else
    print_status 1 "Tempo 未收到链路数据"
fi

# ============================================
# Level 3: 关联性验证
# ============================================
print_header "Level 3: 关联性验证"

# 3.1 验证日志中包含 TraceID
echo "检查日志中的 TraceID..."
trace_in_log=$(curl -s 'http://localhost:3100/loki/api/v1/query?query={job="meta-driven"}' | jq -r '.data.result[0].values[0][1]' 2>/dev/null | jq -r '.traceId // empty')
if [ -n "$trace_in_log" ] && [ "$trace_in_log" != "null" ]; then
    print_status 0 "日志包含 TraceID: $trace_in_log"

    # 3.2 验证 TraceID 在 Tempo 中可查
    echo "验证 TraceID 在 Tempo 中..."
    tempo_trace=$(curl -s "http://localhost:3200/api/traces/$trace_in_log" | jq '.batches | length' 2>/dev/null || echo "0")
    if [ "$tempo_trace" -gt 0 ]; then
        print_status 0 "TraceID 关联验证成功"
    else
        print_status 1 "TraceID 在 Tempo 中未找到"
    fi
else
    print_status 1 "日志中未找到 TraceID"
fi

# ============================================
# Level 4: 告警验证
# ============================================
print_header "Level 4: 告警验证"

# 4.1 检查告警规则加载
echo "检查告警规则..."
rules_count=$(curl -s "http://localhost:9090/api/v1/rules" | jq '.data.groups | length')
if [ "$rules_count" -gt 0 ]; then
    print_status 0 "告警规则已加载 ($rules_count 组)"
else
    print_status 1 "未加载告警规则"
fi

# 4.2 检查 Alertmanager 连接
echo "检查 Alertmanager 连接..."
am_status=$(curl -s "http://localhost:9090/api/v1/alertmanagers" | jq '.data.activeAlertmanagers | length')
if [ "$am_status" -gt 0 ]; then
    print_status 0 "Alertmanager 已连接"
else
    print_status 1 "Alertmanager 未连接"
fi

# ============================================
# 汇总
# ============================================
print_header "验证完成"
echo "请检查上述输出，确保所有检查项通过"
```

### 13.3 测试端点 (用于验证)

```java
package com.tanggo.fund.metadriven.observability;

import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 可观测性验证测试端点
 * 用于验证 Metrics/Logging/Tracing 是否正常工作
 */
@RestController
@RequestMapping("/test/observability")
public class ObservabilityTestController {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityTestController.class);
    private final Tracer tracer;

    public ObservabilityTestController(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        log.info("Health check endpoint called");
        return Map.of(
            "status", "UP",
            "traceId", getCurrentTraceId(),
            "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * 获取当前 Trace 信息
     */
    @GetMapping("/trace")
    public Map<String, String> getTraceInfo() {
        String traceId = getCurrentTraceId();
        String spanId = MDC.get("spanId");
        log.info("Trace info endpoint called, traceId={}, spanId={}", traceId, spanId);
        return Map.of(
            "traceId", traceId != null ? traceId : "N/A",
            "spanId", spanId != null ? spanId : "N/A"
        );
    }

    /**
     * 模拟错误（用于测试告警）
     */
    @GetMapping("/error")
    public void triggerError() {
        log.error("Simulated error for observability test");
        throw new RuntimeException("Test error - This is intentional for testing alerts");
    }

    /**
     * 模拟慢请求（用于测试延迟告警）
     */
    @GetMapping("/slow")
    public Map<String, Object> triggerSlow(@RequestParam(defaultValue = "2000") long delayMs)
            throws InterruptedException {
        log.warn("Slow endpoint called, delay={}ms", delayMs);
        Thread.sleep(delayMs);
        return Map.of(
            "message", "slow response",
            "delayMs", delayMs,
            "traceId", getCurrentTraceId()
        );
    }

    /**
     * 模拟随机延迟（用于生成延迟分布数据）
     */
    @GetMapping("/random-latency")
    public Map<String, Object> randomLatency() throws InterruptedException {
        // 90% 请求 10-50ms, 9% 请求 100-300ms, 1% 请求 500-2000ms
        int random = ThreadLocalRandom.current().nextInt(100);
        long delay;
        if (random < 90) {
            delay = ThreadLocalRandom.current().nextLong(10, 50);
        } else if (random < 99) {
            delay = ThreadLocalRandom.current().nextLong(100, 300);
        } else {
            delay = ThreadLocalRandom.current().nextLong(500, 2000);
        }

        Thread.sleep(delay);
        log.debug("Random latency request, delay={}ms", delay);

        return Map.of(
            "delayMs", delay,
            "percentile", random < 90 ? "P90" : (random < 99 ? "P99" : "P999")
        );
    }

    /**
     * 生成测试日志（不同级别）
     */
    @PostMapping("/logs")
    public Map<String, String> generateLogs(@RequestParam(defaultValue = "10") int count) {
        for (int i = 0; i < count; i++) {
            int level = i % 4;
            switch (level) {
                case 0 -> log.debug("Test DEBUG log #{}", i);
                case 1 -> log.info("Test INFO log #{}", i);
                case 2 -> log.warn("Test WARN log #{}", i);
                case 3 -> log.error("Test ERROR log #{}", i);
            }
        }
        return Map.of(
            "message", "Generated " + count + " logs",
            "traceId", getCurrentTraceId()
        );
    }

    /**
     * 模拟内存压力（用于测试内存告警）
     */
    @GetMapping("/memory-pressure")
    public Map<String, Object> memoryPressure(@RequestParam(defaultValue = "100") int sizeMB) {
        log.warn("Memory pressure test, allocating {}MB", sizeMB);

        // 分配内存（注意：这只是测试用，不要在生产环境使用）
        byte[][] arrays = new byte[sizeMB][];
        for (int i = 0; i < sizeMB; i++) {
            arrays[i] = new byte[1024 * 1024]; // 1MB
        }

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);

        // 立即释放
        arrays = null;
        System.gc();

        return Map.of(
            "allocatedMB", sizeMB,
            "usedMemoryMB", usedMemory,
            "maxMemoryMB", maxMemory,
            "usagePercent", (usedMemory * 100.0) / maxMemory
        );
    }

    private String getCurrentTraceId() {
        if (tracer.currentSpan() != null && tracer.currentSpan().context() != null) {
            return tracer.currentSpan().context().traceId();
        }
        return MDC.get("traceId");
    }
}
```

### 13.4 验证检查清单

| 验证项 | 验证方法 | 预期结果 | 通过 |
|--------|----------|----------|------|
| **基础设施** | | | |
| Docker 容器运行 | `docker ps` | 所有容器 Up | [ ] |
| Prometheus 健康 | `curl localhost:9090/-/healthy` | 200 OK | [ ] |
| Loki 健康 | `curl localhost:3100/ready` | 200 OK | [ ] |
| Tempo 健康 | `curl localhost:3200/ready` | 200 OK | [ ] |
| Grafana 健康 | `curl localhost:3000/api/health` | 200 OK | [ ] |
| **数据流** | | | |
| 应用指标暴露 | `curl localhost:8080/actuator/prometheus` | 返回指标 | [ ] |
| Prometheus 抓取 | Prometheus UI → Targets | meta-driven UP | [ ] |
| Loki 收到日志 | Grafana Explore → Loki | 有日志 | [ ] |
| Tempo 收到链路 | Grafana Explore → Tempo | 有 Traces | [ ] |
| **关联性** | | | |
| 日志含 TraceID | 查看 JSON 日志 | traceId 字段非空 | [ ] |
| 日志跳转 Trace | 点击 Loki 日志中的 TraceID | 跳转到 Tempo | [ ] |
| Trace 关联日志 | Tempo 详情页 | 显示关联日志 | [ ] |
| **告警** | | | |
| 告警规则加载 | Prometheus UI → Alerts | 显示规则 | [ ] |
| Alertmanager 连接 | Prometheus UI → Status | AM 已连接 | [ ] |
| 告警触发测试 | `curl localhost:8080/test/observability/error` | 告警触发 | [ ] |
| 告警通知到达 | 检查钉钉/邮件 | 收到通知 | [ ] |
| **性能** | | | |
| P99 延迟基线 | 压测对比 | 增加 < 5% | [ ] |

---

## 十四、性能基准测试

### 14.1 基准测试脚本

```bash
#!/bin/bash
# scripts/benchmark-observability.sh

# 依赖: wrk, jq

BASE_URL="http://localhost:8080"
DURATION="30s"
THREADS=4
CONNECTIONS=100

print_header() {
    echo -e "\n========== $1 =========="
}

# 测试端点
TEST_ENDPOINT="/test/observability/random-latency"

print_header "基准测试配置"
echo "URL: $BASE_URL$TEST_ENDPOINT"
echo "Duration: $DURATION"
echo "Threads: $THREADS"
echo "Connections: $CONNECTIONS"

print_header "场景 1: 基线测试 (采样率 100%)"
echo "确保 management.tracing.sampling.probability=1.0"
read -p "按 Enter 继续..."

wrk -t$THREADS -c$CONNECTIONS -d$DURATION --latency "$BASE_URL$TEST_ENDPOINT" | tee baseline_100.txt

print_header "场景 2: 采样率 10%"
echo "修改 management.tracing.sampling.probability=0.1 并重启应用"
read -p "按 Enter 继续..."

wrk -t$THREADS -c$CONNECTIONS -d$DURATION --latency "$BASE_URL$TEST_ENDPOINT" | tee baseline_10.txt

print_header "场景 3: 采样率 1%"
echo "修改 management.tracing.sampling.probability=0.01 并重启应用"
read -p "按 Enter 继续..."

wrk -t$THREADS -c$CONNECTIONS -d$DURATION --latency "$BASE_URL$TEST_ENDPOINT" | tee baseline_1.txt

print_header "场景 4: 关闭 Tracing"
echo "修改 management.tracing.enabled=false 并重启应用"
read -p "按 Enter 继续..."

wrk -t$THREADS -c$CONNECTIONS -d$DURATION --latency "$BASE_URL$TEST_ENDPOINT" | tee baseline_off.txt

print_header "结果对比"
echo "采样率 100%:"
grep "Latency" baseline_100.txt
grep "Req/Sec" baseline_100.txt

echo -e "\n采样率 10%:"
grep "Latency" baseline_10.txt
grep "Req/Sec" baseline_10.txt

echo -e "\n采样率 1%:"
grep "Latency" baseline_1.txt
grep "Req/Sec" baseline_1.txt

echo -e "\n关闭 Tracing:"
grep "Latency" baseline_off.txt
grep "Req/Sec" baseline_off.txt
```

### 14.2 性能基准数据模板

| 场景 | 采样率 | QPS | P50 | P95 | P99 | P999 | 开销 |
|------|--------|-----|-----|-----|-----|------|------|
| 基线 (无可观测) | N/A | - | - | - | - | - | 0% |
| 仅 Metrics | N/A | - | - | - | - | - | ~1% |
| Metrics + Tracing | 100% | - | - | - | - | - | ~5% |
| Metrics + Tracing | 10% | - | - | - | - | - | ~2% |
| Metrics + Tracing | 1% | - | - | - | - | - | ~1% |
| 完整方案 | 10% | - | - | - | - | - | ~3% |

**性能目标**:
- P99 延迟增加 < 5%
- QPS 下降 < 5%
- 内存增加 < 10%

---

## 十五、更新后的 Docker Compose (完整版)

```yaml
# docker-compose-observability.yml
version: '3.8'

services:
  # ============================================
  # Prometheus - 指标存储
  # ============================================
  prometheus:
    image: prom/prometheus:v2.53.0
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./config/prometheus.yml:/etc/prometheus/prometheus.yml
      - ./config/alert.rules.yml:/etc/prometheus/alert.rules.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--storage.tsdb.retention.time=15d'
      - '--web.enable-lifecycle'
    networks:
      - observability
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:9090/-/healthy"]
      interval: 30s
      timeout: 10s
      retries: 3
    deploy:
      resources:
        limits:
          cpus: '1'
          memory: 2G
        reservations:
          cpus: '0.5'
          memory: 512M

  # ============================================
  # Alertmanager - 告警通知
  # ============================================
  alertmanager:
    image: prom/alertmanager:v0.27.0
    container_name: alertmanager
    ports:
      - "9093:9093"
    volumes:
      - ./config/alertmanager.yml:/etc/alertmanager/alertmanager.yml
      - alertmanager_data:/alertmanager
    command:
      - '--config.file=/etc/alertmanager/alertmanager.yml'
      - '--storage.path=/alertmanager'
    networks:
      - observability
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:9093/-/healthy"]
      interval: 30s
      timeout: 10s
      retries: 3
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 256M

  # ============================================
  # Loki - 日志存储
  # ============================================
  loki:
    image: grafana/loki:3.0.0
    container_name: loki
    ports:
      - "3100:3100"
    volumes:
      - ./config/loki.yml:/etc/loki/local-config.yaml
      - loki_data:/loki
    command: -config.file=/etc/loki/local-config.yaml
    networks:
      - observability
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:3100/ready"]
      interval: 30s
      timeout: 10s
      retries: 3
    deploy:
      resources:
        limits:
          cpus: '1'
          memory: 1G
        reservations:
          cpus: '0.25'
          memory: 256M

  # ============================================
  # Promtail - 日志采集
  # ============================================
  promtail:
    image: grafana/promtail:3.0.0
    container_name: promtail
    volumes:
      - ./config/promtail.yml:/etc/promtail/config.yml
      - ./logs:/var/log/app
      - /var/run/docker.sock:/var/run/docker.sock:ro
    command: -config.file=/etc/promtail/config.yml
    networks:
      - observability
    depends_on:
      loki:
        condition: service_healthy
    deploy:
      resources:
        limits:
          cpus: '0.5'
          memory: 256M

  # ============================================
  # Tempo - 链路追踪存储
  # ============================================
  tempo:
    image: grafana/tempo:2.5.0
    container_name: tempo
    ports:
      - "3200:3200"   # Tempo Query
      - "4317:4317"   # OTLP gRPC
      - "4318:4318"   # OTLP HTTP
    volumes:
      - ./config/tempo.yml:/etc/tempo/tempo.yml
      - tempo_data:/var/tempo
    command: -config.file=/etc/tempo/tempo.yml
    networks:
      - observability
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:3200/ready"]
      interval: 30s
      timeout: 10s
      retries: 3
    deploy:
      resources:
        limits:
          cpus: '1'
          memory: 1G
        reservations:
          cpus: '0.25'
          memory: 256M

  # ============================================
  # Grafana - 统一可视化
  # ============================================
  grafana:
    image: grafana/grafana:11.0.0
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_FEATURE_TOGGLES_ENABLE=traceqlEditor,correlations
      - GF_INSTALL_PLUGINS=grafana-clock-panel,grafana-piechart-panel
    volumes:
      - ./config/grafana/provisioning:/etc/grafana/provisioning
      - ./config/grafana/dashboards:/var/lib/grafana/dashboards
      - grafana_data:/var/lib/grafana
    networks:
      - observability
    depends_on:
      prometheus:
        condition: service_healthy
      loki:
        condition: service_healthy
      tempo:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:3000/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3
    deploy:
      resources:
        limits:
          cpus: '1'
          memory: 512M
        reservations:
          cpus: '0.25'
          memory: 128M

networks:
  observability:
    driver: bridge

volumes:
  prometheus_data:
    driver: local
  alertmanager_data:
    driver: local
  loki_data:
    driver: local
  tempo_data:
    driver: local
  grafana_data:
    driver: local
```

---

## 十六、Grafana Dashboard JSON

### 16.1 Dashboard 配置自动加载

```yaml
# config/grafana/provisioning/dashboards/dashboards.yml
apiVersion: 1
providers:
  - name: 'default'
    orgId: 1
    folder: 'Meta-Driven'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    options:
      path: /var/lib/grafana/dashboards
```

### 16.2 应用概览 Dashboard

将以下 JSON 保存到 `config/grafana/dashboards/meta-driven-overview.json`:

```json
{
  "annotations": {
    "list": []
  },
  "editable": true,
  "fiscalYearStartMonth": 0,
  "graphTooltip": 1,
  "id": null,
  "links": [],
  "liveNow": false,
  "panels": [
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "palette-classic" },
          "mappings": [],
          "thresholds": { "mode": "absolute", "steps": [{ "color": "green", "value": null }] },
          "unit": "short"
        }
      },
      "gridPos": { "h": 4, "w": 6, "x": 0, "y": 0 },
      "id": 1,
      "options": {
        "colorMode": "value",
        "graphMode": "area",
        "justifyMode": "auto",
        "orientation": "auto",
        "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false },
        "textMode": "auto"
      },
      "pluginVersion": "11.0.0",
      "targets": [
        {
          "expr": "sum(rate(http_server_requests_seconds_count{application=\"meta-driven\"}[5m]))",
          "legendFormat": "RPS",
          "refId": "A"
        }
      ],
      "title": "请求速率 (RPS)",
      "type": "stat"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "thresholds" },
          "mappings": [],
          "thresholds": {
            "mode": "absolute",
            "steps": [
              { "color": "green", "value": null },
              { "color": "yellow", "value": 0.01 },
              { "color": "red", "value": 0.05 }
            ]
          },
          "unit": "percentunit"
        }
      },
      "gridPos": { "h": 4, "w": 6, "x": 6, "y": 0 },
      "id": 2,
      "options": {
        "colorMode": "value",
        "graphMode": "area",
        "justifyMode": "auto",
        "orientation": "auto",
        "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false },
        "textMode": "auto"
      },
      "targets": [
        {
          "expr": "sum(rate(http_server_requests_seconds_count{application=\"meta-driven\",status=~\"5..\"}[5m])) / sum(rate(http_server_requests_seconds_count{application=\"meta-driven\"}[5m]))",
          "legendFormat": "Error Rate",
          "refId": "A"
        }
      ],
      "title": "错误率",
      "type": "stat"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "thresholds" },
          "mappings": [],
          "thresholds": {
            "mode": "absolute",
            "steps": [
              { "color": "green", "value": null },
              { "color": "yellow", "value": 0.1 },
              { "color": "red", "value": 0.5 }
            ]
          },
          "unit": "s"
        }
      },
      "gridPos": { "h": 4, "w": 6, "x": 12, "y": 0 },
      "id": 3,
      "options": {
        "colorMode": "value",
        "graphMode": "area",
        "justifyMode": "auto",
        "orientation": "auto",
        "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false },
        "textMode": "auto"
      },
      "targets": [
        {
          "expr": "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application=\"meta-driven\"}[5m])) by (le))",
          "legendFormat": "P99",
          "refId": "A"
        }
      ],
      "title": "P99 延迟",
      "type": "stat"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "thresholds" },
          "mappings": [],
          "thresholds": {
            "mode": "absolute",
            "steps": [
              { "color": "green", "value": null },
              { "color": "yellow", "value": 0.7 },
              { "color": "red", "value": 0.9 }
            ]
          },
          "unit": "percentunit"
        }
      },
      "gridPos": { "h": 4, "w": 6, "x": 18, "y": 0 },
      "id": 4,
      "options": {
        "colorMode": "value",
        "graphMode": "area",
        "justifyMode": "auto",
        "orientation": "auto",
        "reduceOptions": { "calcs": ["lastNotNull"], "fields": "", "values": false },
        "textMode": "auto"
      },
      "targets": [
        {
          "expr": "sum(jvm_memory_used_bytes{application=\"meta-driven\",area=\"heap\"}) / sum(jvm_memory_max_bytes{application=\"meta-driven\",area=\"heap\"})",
          "legendFormat": "Heap Usage",
          "refId": "A"
        }
      ],
      "title": "堆内存使用率",
      "type": "stat"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "palette-classic" },
          "custom": {
            "axisCenteredZero": false,
            "axisColorMode": "text",
            "axisLabel": "",
            "axisPlacement": "auto",
            "barAlignment": 0,
            "drawStyle": "line",
            "fillOpacity": 10,
            "gradientMode": "none",
            "hideFrom": { "legend": false, "tooltip": false, "viz": false },
            "lineInterpolation": "linear",
            "lineWidth": 1,
            "pointSize": 5,
            "scaleDistribution": { "type": "linear" },
            "showPoints": "auto",
            "spanNulls": false,
            "stacking": { "group": "A", "mode": "none" },
            "thresholdsStyle": { "mode": "off" }
          },
          "mappings": [],
          "thresholds": { "mode": "absolute", "steps": [{ "color": "green", "value": null }] },
          "unit": "s"
        }
      },
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 4 },
      "id": 5,
      "options": {
        "legend": { "calcs": ["mean", "max"], "displayMode": "table", "placement": "bottom", "showLegend": true },
        "tooltip": { "mode": "multi", "sort": "desc" }
      },
      "targets": [
        {
          "expr": "histogram_quantile(0.5, sum(rate(http_server_requests_seconds_bucket{application=\"meta-driven\"}[5m])) by (le))",
          "legendFormat": "P50",
          "refId": "A"
        },
        {
          "expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application=\"meta-driven\"}[5m])) by (le))",
          "legendFormat": "P95",
          "refId": "B"
        },
        {
          "expr": "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application=\"meta-driven\"}[5m])) by (le))",
          "legendFormat": "P99",
          "refId": "C"
        },
        {
          "expr": "histogram_quantile(0.999, sum(rate(http_server_requests_seconds_bucket{application=\"meta-driven\"}[5m])) by (le))",
          "legendFormat": "P999",
          "refId": "D"
        }
      ],
      "title": "请求延迟分布",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "prometheus", "uid": "prometheus" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "palette-classic" },
          "custom": {
            "axisCenteredZero": false,
            "axisColorMode": "text",
            "axisLabel": "",
            "axisPlacement": "auto",
            "barAlignment": 0,
            "drawStyle": "line",
            "fillOpacity": 10,
            "gradientMode": "none",
            "hideFrom": { "legend": false, "tooltip": false, "viz": false },
            "lineInterpolation": "linear",
            "lineWidth": 1,
            "pointSize": 5,
            "scaleDistribution": { "type": "linear" },
            "showPoints": "auto",
            "spanNulls": false,
            "stacking": { "group": "A", "mode": "none" },
            "thresholdsStyle": { "mode": "off" }
          },
          "mappings": [],
          "thresholds": { "mode": "absolute", "steps": [{ "color": "green", "value": null }] },
          "unit": "bytes"
        }
      },
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 4 },
      "id": 6,
      "options": {
        "legend": { "calcs": ["mean", "max"], "displayMode": "table", "placement": "bottom", "showLegend": true },
        "tooltip": { "mode": "multi", "sort": "desc" }
      },
      "targets": [
        {
          "expr": "sum(jvm_memory_used_bytes{application=\"meta-driven\",area=\"heap\"}) by (id)",
          "legendFormat": "{{id}}",
          "refId": "A"
        }
      ],
      "title": "JVM 堆内存",
      "type": "timeseries"
    }
  ],
  "refresh": "5s",
  "schemaVersion": 38,
  "style": "dark",
  "tags": ["meta-driven", "spring-boot"],
  "templating": { "list": [] },
  "time": { "from": "now-1h", "to": "now" },
  "timepicker": {},
  "timezone": "",
  "title": "Meta-Driven 应用概览",
  "uid": "meta-driven-overview",
  "version": 1,
  "weekStart": ""
}
```

---

## 十七、实施检查清单 (更新版)

### 阶段 1: 基础设施
- [ ] 创建配置目录结构
- [ ] 编写所有配置文件
- [ ] 启动 Docker Compose
- [ ] 验证所有容器健康

### 阶段 2: 应用集成
- [ ] 添加 Maven 依赖
- [ ] 配置 application.yml
- [ ] 创建 logback-spring.xml
- [ ] 实现自定义业务指标
- [ ] 添加测试端点

### 阶段 3: 验证
- [ ] 运行验证脚本
- [ ] 验证 Prometheus 抓取
- [ ] 验证 Loki 日志采集
- [ ] 验证 Tempo 链路追踪
- [ ] 验证 TraceID 关联

### 阶段 4: 告警
- [ ] 配置 Alertmanager
- [ ] 配置告警通知渠道
- [ ] 测试告警触发
- [ ] 验证告警通知到达

### 阶段 5: 性能验证
- [ ] 运行基准测试
- [ ] 记录性能数据
- [ ] 验证延迟开销 < 5%
- [ ] 确定最优采样率

### 阶段 6: 运维就绪
- [ ] 导入 Grafana Dashboard
- [ ] 配置数据备份策略
- [ ] 编写运维文档
- [ ] 培训团队成员