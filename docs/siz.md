# 容量方案评估 (Sizing Guide)

## 概述

本文档提供 meta-driven 应用的容量规划指南，包括资源估算模型、性能基准、扩容策略和成本优化建议。基于低时延优先的架构原则，确保系统在各种负载下保持稳定的响应时间。

## 目录

1. [容量规划方法论](#容量规划方法论)
2. [资源需求模型](#资源需求模型)
3. [性能基准测试](#性能基准测试)
4. [环境规格建议](#环境规格建议)
5. [扩容策略](#扩容策略)
6. [容量计算公式](#容量计算公式)
7. [监控与预警](#监控与预警)
8. [成本优化](#成本优化)

---

## 容量规划方法论

### 核心原则

```
┌─────────────────────────────────────────────────────────────┐
│                    容量规划金字塔                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                      ┌───────────┐                          │
│                      │  业务目标  │                          │
│                      │   (SLA)   │                          │
│                      └─────┬─────┘                          │
│                            │                                │
│                  ┌─────────▼─────────┐                      │
│                  │   性能指标要求     │                      │
│                  │ (延迟/吞吐/可用性) │                      │
│                  └─────────┬─────────┘                      │
│                            │                                │
│            ┌───────────────▼───────────────┐                │
│            │        资源需求计算            │                │
│            │  (CPU/内存/网络/存储)          │                │
│            └───────────────┬───────────────┘                │
│                            │                                │
│      ┌─────────────────────▼─────────────────────┐          │
│      │              基础设施规划                  │          │
│      │  (实例类型/副本数/地域分布)                │          │
│      └───────────────────────────────────────────┘          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### SLA 目标定义

| 指标 | 目标值 | 临界值 | 测量方法 |
|------|--------|--------|----------|
| **可用性** | 99.95% | 99.9% | 月度统计 |
| **P50 延迟** | < 5ms | < 10ms | 滑动窗口 5min |
| **P99 延迟** | < 50ms | < 100ms | 滑动窗口 5min |
| **P99.9 延迟** | < 200ms | < 500ms | 滑动窗口 5min |
| **错误率** | < 0.1% | < 1% | 5xx/总请求 |
| **吞吐量** | 按需扩展 | 峰值 2x | QPS |

---

## 资源需求模型

### 单实例资源基准

```yaml
# 基于 JVM 的资源消耗模型
single_instance:
  # 最小配置 (开发/测试)
  minimal:
    cpu: 0.5 核
    memory: 512 MB
    heap: 256 MB
    max_qps: 100
    p99_latency: 50ms

  # 标准配置 (生产基准)
  standard:
    cpu: 2 核
    memory: 2 GB
    heap: 1 GB
    max_qps: 1000
    p99_latency: 20ms

  # 高性能配置 (低时延要求)
    performance:
    cpu: 4 核
    memory: 4 GB
    heap: 2 GB
    max_qps: 3000
    p99_latency: 10ms

  # 极致配置 (超低时延)
  ultra:
    cpu: 8 核
    memory: 8 GB
    heap: 4 GB
    max_qps: 5000
    p99_latency: 5ms
```

### JVM 内存模型

```
┌────────────────────────────────────────────────────────────┐
│                     JVM 内存结构                            │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                    Heap (堆内存)                      │  │
│  │  ┌────────────────┐  ┌───────────────────────────┐   │  │
│  │  │  Young Gen     │  │      Old Gen              │   │  │
│  │  │  (新生代)       │  │      (老年代)              │   │  │
│  │  │  ┌────┐┌────┐  │  │                           │   │  │
│  │  │  │Eden││S0/1│  │  │     ~60-70% Heap          │   │  │
│  │  │  └────┘└────┘  │  │                           │   │  │
│  │  │   ~30-40%      │  │                           │   │  │
│  │  └────────────────┘  └───────────────────────────┘   │  │
│  │                    总大小: -Xmx                       │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │                 Non-Heap (非堆内存)                   │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐  │  │
│  │  │Metaspace │ │Code Cache│ │ Thread   │ │ Direct  │  │  │
│  │  │ ~128MB   │ │  ~240MB  │ │ Stacks   │ │ Buffer  │  │  │
│  │  │          │ │          │ │ ~1MB/线程 │ │ ~256MB  │  │  │
│  │  └──────────┘ └──────────┘ └──────────┘ └─────────┘  │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                            │
│  总内存 ≈ Heap + Metaspace + CodeCache + Threads + Buffer  │
│  建议: 容器内存 = Heap × 1.5 ~ 2                           │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

### 内存计算公式

```
容器内存 = Heap + NonHeap + OS保留
         = Heap + (Metaspace + CodeCache + ThreadStacks + DirectBuffer) + OS
         = Heap + (~500MB) + (~200MB)
         ≈ Heap × 1.7

示例:
- Heap = 1GB  → 容器内存 ≈ 1.7GB → 建议 2GB
- Heap = 2GB  → 容器内存 ≈ 3.4GB → 建议 4GB
- Heap = 4GB  → 容器内存 ≈ 6.8GB → 建议 8GB
```

---

## 性能基准测试

### 基准测试配置

```bash
# 使用 wrk 进行压测
# 安装: brew install wrk (macOS) / apt install wrk (Linux)

# 基准测试脚本
#!/bin/bash
# scripts/benchmark.sh

TARGET_URL="${TARGET_URL:-http://localhost:8080/api/observability/test}"
DURATION="${DURATION:-60s}"
THREADS="${THREADS:-4}"
CONNECTIONS="${CONNECTIONS:-100}"

echo "=== 性能基准测试 ==="
echo "目标: $TARGET_URL"
echo "持续时间: $DURATION"
echo "线程数: $THREADS"
echo "连接数: $CONNECTIONS"
echo ""

# 预热
echo ">>> 预热阶段 (10s)..."
wrk -t2 -c10 -d10s "$TARGET_URL" > /dev/null 2>&1

# 正式测试
echo ">>> 正式测试..."
wrk -t$THREADS -c$CONNECTIONS -d$DURATION --latency "$TARGET_URL"
```

### 基准测试结果模板

```yaml
# 测试环境: 标准配置 (2 CPU, 2GB 内存)
benchmark_results:
  environment:
    instance_type: "标准配置"
    cpu: 2
    memory: "2GB"
    heap: "1GB"
    jvm_flags: "-XX:+UseG1GC -XX:MaxGCPauseMillis=10"

  # 低负载 (10% 容量)
  low_load:
    qps: 100
    latency:
      p50: 2ms
      p95: 5ms
      p99: 8ms
      p999: 15ms
    cpu_usage: 5%
    memory_usage: 40%
    gc_pause_avg: 2ms

  # 中等负载 (50% 容量)
  medium_load:
    qps: 500
    latency:
      p50: 3ms
      p95: 8ms
      p99: 15ms
      p999: 30ms
    cpu_usage: 30%
    memory_usage: 55%
    gc_pause_avg: 5ms

  # 高负载 (80% 容量)
  high_load:
    qps: 800
    latency:
      p50: 5ms
      p95: 15ms
      p99: 25ms
      p999: 50ms
    cpu_usage: 60%
    memory_usage: 70%
    gc_pause_avg: 8ms

  # 峰值负载 (100% 容量)
  peak_load:
    qps: 1000
    latency:
      p50: 8ms
      p95: 20ms
      p99: 35ms
      p999: 80ms
    cpu_usage: 80%
    memory_usage: 75%
    gc_pause_avg: 10ms

  # 过载 (超出容量)
  overload:
    qps: 1200
    latency:
      p50: 50ms
      p95: 200ms
      p99: 500ms
      p999: 1000ms
    cpu_usage: 100%
    memory_usage: 85%
    gc_pause_avg: 50ms
    error_rate: 5%
```

### JMH 微基准测试

```java
// 关键路径性能测试
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class CriticalPathBenchmark {

    @Benchmark
    public void measureCommandProcessing(Blackhole bh) {
        // 命令处理基准
        bh.consume(processCommand());
    }

    @Benchmark
    public void measureQueryProcessing(Blackhole bh) {
        // 查询处理基准
        bh.consume(processQuery());
    }

    @Benchmark
    public void measureSerializationJson(Blackhole bh) {
        // JSON 序列化基准
        bh.consume(serializeToJson(testObject));
    }
}
```

---

## 环境规格建议

### 开发环境

```yaml
development:
  description: "本地开发和单元测试"
  instances: 1
  resources:
    cpu: 0.5
    memory: 1GB
    heap: 512MB
  jvm_options: |
    -XX:+UseG1GC
    -Xms256m -Xmx512m
    -XX:+HeapDumpOnOutOfMemoryError
  storage:
    logs: 1GB
    temp: 500MB
```

### 测试环境

```yaml
testing:
  description: "集成测试和 QA 验证"
  instances: 2
  resources:
    cpu: 1
    memory: 2GB
    heap: 1GB
  jvm_options: |
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=20
    -Xms512m -Xmx1g
    -XX:+HeapDumpOnOutOfMemoryError
  load_balancer: nginx
  database:
    type: PostgreSQL
    cpu: 1
    memory: 2GB
    storage: 20GB
```

### Staging 环境

```yaml
staging:
  description: "预发布环境，配置接近生产"
  instances: 3
  resources:
    cpu: 2
    memory: 4GB
    heap: 2GB
  jvm_options: |
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=10
    -XX:+AlwaysPreTouch
    -Xms2g -Xmx2g
    -XX:+ParallelRefProcEnabled
    -XX:+ExitOnOutOfMemoryError
  load_balancer: nginx
  replicas:
    min: 2
    max: 5
  database:
    type: PostgreSQL
    cpu: 2
    memory: 4GB
    storage: 50GB
    replicas: 1
```

### 生产环境

```yaml
production:
  description: "生产环境，高可用配置"

  # 应用层
  application:
    instances:
      min: 3
      max: 10
    resources:
      cpu: 4
      memory: 8GB
      heap: 4GB
    jvm_options: |
      -XX:+UseG1GC
      -XX:MaxGCPauseMillis=10
      -XX:G1HeapRegionSize=4M
      -XX:+AlwaysPreTouch
      -XX:+ParallelRefProcEnabled
      -Xms4g -Xmx4g
      -XX:+UseStringDeduplication
      -XX:+ExitOnOutOfMemoryError
      -XX:+HeapDumpOnOutOfMemoryError
      -XX:HeapDumpPath=/var/log/heapdump/

  # 负载均衡层
  load_balancer:
    type: nginx
    instances: 2
    resources:
      cpu: 2
      memory: 4GB

  # 数据库层
  database:
    type: PostgreSQL
    primary:
      cpu: 4
      memory: 16GB
      storage: 500GB
      iops: 3000
    replicas:
      count: 2
      cpu: 2
      memory: 8GB
      storage: 500GB

  # 缓存层 (可选)
  cache:
    type: Redis
    instances: 3
    resources:
      cpu: 2
      memory: 8GB

  # 消息队列 (可选)
  message_queue:
    type: Kafka
    brokers: 3
    resources:
      cpu: 2
      memory: 8GB
      storage: 100GB
```

### 资源规格对照表

| 环境 | 实例数 | CPU/实例 | 内存/实例 | Heap | 目标 QPS | P99 延迟 |
|------|--------|----------|-----------|------|----------|----------|
| 开发 | 1 | 0.5 | 1GB | 512MB | 100 | 100ms |
| 测试 | 2 | 1 | 2GB | 1GB | 500 | 50ms |
| Staging | 3 | 2 | 4GB | 2GB | 2000 | 30ms |
| 生产 | 3-10 | 4 | 8GB | 4GB | 5000-15000 | 20ms |

---

## 扩容策略

### 水平扩展 (HPA)

```yaml
# Kubernetes HPA 配置
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: meta-driven-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: meta-driven
  minReplicas: 3
  maxReplicas: 10

  metrics:
  # CPU 使用率
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70

  # 内存使用率
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80

  # 自定义指标: 请求延迟
  - type: Pods
    pods:
      metric:
        name: http_server_requests_seconds_p99
      target:
        type: AverageValue
        averageValue: 50m  # 50ms

  # 自定义指标: QPS
  - type: Pods
    pods:
      metric:
        name: http_server_requests_per_second
      target:
        type: AverageValue
        averageValue: "800"

  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300  # 5分钟稳定期
      policies:
      - type: Percent
        value: 10
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0  # 立即扩容
      policies:
      - type: Percent
        value: 100
        periodSeconds: 15
      - type: Pods
        value: 4
        periodSeconds: 15
      selectPolicy: Max
```

### 垂直扩展 (VPA)

```yaml
# Kubernetes VPA 配置
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: meta-driven-vpa
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: meta-driven
  updatePolicy:
    updateMode: "Auto"  # 或 "Initial" 仅初始设置
  resourcePolicy:
    containerPolicies:
    - containerName: app
      minAllowed:
        cpu: 500m
        memory: 1Gi
      maxAllowed:
        cpu: 8
        memory: 16Gi
      controlledResources: ["cpu", "memory"]
```

### 扩容决策流程

```
┌─────────────────────────────────────────────────────────────┐
│                     扩容决策流程图                            │
└─────────────────────────────────────────────────────────────┘

                    ┌─────────────┐
                    │  监控指标    │
                    │  采集 (1s)  │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  指标评估    │
                    │  (滑动窗口)  │
                    └──────┬──────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
    ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
    │ CPU > 70%?  │ │ MEM > 80%?  │ │ P99 > 50ms? │
    └──────┬──────┘ └──────┬──────┘ └──────┬──────┘
           │               │               │
           └───────────────┼───────────────┘
                           │
                    ┌──────▼──────┐
                    │  任一条件    │──No──→ 维持现状
                    │  满足?      │
                    └──────┬──────┘
                           │ Yes
                    ┌──────▼──────┐
                    │  稳定期检查  │
                    │  (5min)     │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  计算目标    │
                    │  副本数      │
                    └──────┬──────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
    ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
    │  扩容 +N    │ │  缩容 -N    │ │  无变化     │
    └─────────────┘ └─────────────┘ └─────────────┘
```

### 扩容阈值建议

| 指标 | 扩容阈值 | 缩容阈值 | 冷却期 |
|------|----------|----------|--------|
| CPU 使用率 | > 70% | < 30% | 5 min |
| 内存使用率 | > 80% | < 40% | 10 min |
| P99 延迟 | > 50ms | < 20ms | 5 min |
| QPS/实例 | > 800 | < 200 | 5 min |
| 连接数 | > 1000 | < 200 | 5 min |

---

## 容量计算公式

### QPS 容量计算

```
单实例最大 QPS = (CPU 核数 × 1000) / (P99 延迟 ms × 并发因子)

并发因子 = 1 + (IO 等待比例)
         = 1 + 0.3 (典型 Web 应用)
         = 1.3

示例:
- 2 核，P99=20ms: QPS = (2 × 1000) / (20 × 1.3) = 77 QPS/核 = 154 QPS
- 4 核，P99=10ms: QPS = (4 × 1000) / (10 × 1.3) = 308 QPS

考虑安全裕度 (70%):
- 2 核实际容量: 154 × 0.7 ≈ 108 QPS
- 4 核实际容量: 308 × 0.7 ≈ 215 QPS
```

### 实例数量计算

```
所需实例数 = ceil(峰值 QPS × (1 + 增长预留) / (单实例容量 × 可用率))

参数:
- 峰值 QPS: 业务预估峰值流量
- 增长预留: 通常 20-30%
- 可用率: 考虑滚动更新，通常 0.8-0.9

示例:
峰值 QPS = 5000
增长预留 = 30%
单实例容量 = 1000 QPS
可用率 = 0.8

所需实例 = ceil(5000 × 1.3 / (1000 × 0.8))
         = ceil(8.125)
         = 9 实例
```

### 内存容量计算

```
单实例内存需求 = Heap + 非堆 + 系统保留

Heap 计算:
- 基础 = 256MB
- 每 100 QPS 追加 = 50MB
- 缓存需求 = 业务数据缓存大小

非堆估算:
- Metaspace: 128-256MB
- CodeCache: 128-256MB
- 线程栈: 线程数 × 1MB
- DirectBuffer: 根据 NIO 使用量

示例 (1000 QPS, 100 线程):
Heap = 256 + (10 × 50) = 756MB → 圆整到 1GB
非堆 = 128 + 128 + 100 + 64 = 420MB
总计 = 1GB + 420MB = 1.42GB → 建议容器 2GB
```

### 存储容量计算

```
日志存储 = 日均请求数 × 单条日志大小 × 保留天数 × 压缩比

示例:
- 日均请求: 1000万
- 单条日志: 500 字节
- 保留天数: 30 天
- 压缩比: 0.3 (gzip)

存储 = 10,000,000 × 500 × 30 × 0.3 / 1024^3
     = 41.9 GB

建议: 50GB (含安全裕度)
```

### 网络带宽计算

```
带宽需求 = (请求大小 + 响应大小) × QPS × 8 / 1,000,000

示例:
- 平均请求: 1KB
- 平均响应: 5KB
- QPS: 1000

带宽 = (1 + 5) × 1000 × 8 / 1,000,000
     = 48 Mbps

考虑峰值 (2x): 96 Mbps
建议: 100 Mbps 独享带宽
```

---

## 监控与预警

### 容量监控指标

```yaml
# Prometheus 容量相关指标
capacity_metrics:
  # CPU 容量
  cpu:
    - name: process_cpu_usage
      query: process_cpu_usage{application="meta-driven"}
      threshold_warn: 0.6
      threshold_critical: 0.8

    - name: system_cpu_usage
      query: system_cpu_usage{application="meta-driven"}
      threshold_warn: 0.7
      threshold_critical: 0.9

  # 内存容量
  memory:
    - name: jvm_memory_used_bytes
      query: sum(jvm_memory_used_bytes{application="meta-driven"}) by (area)
      threshold_warn: 0.7
      threshold_critical: 0.85

    - name: jvm_gc_pause_seconds
      query: rate(jvm_gc_pause_seconds_sum[5m]) / rate(jvm_gc_pause_seconds_count[5m])
      threshold_warn: 0.02  # 20ms
      threshold_critical: 0.05  # 50ms

  # 吞吐量
  throughput:
    - name: http_server_requests_per_second
      query: sum(rate(http_server_requests_seconds_count[1m]))
      # 动态阈值: 当前容量的 80%

  # 延迟
  latency:
    - name: http_server_requests_p99
      query: histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))
      threshold_warn: 0.03  # 30ms
      threshold_critical: 0.05  # 50ms
```

### 告警规则

```yaml
# config/prometheus/capacity-alerts.yml
groups:
- name: capacity-alerts
  rules:
  # CPU 容量告警
  - alert: HighCPUUsage
    expr: |
      avg(process_cpu_usage{application="meta-driven"}) > 0.7
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "CPU 使用率过高"
      description: "CPU 使用率 {{ $value | humanizePercentage }}，考虑扩容"

  - alert: CriticalCPUUsage
    expr: |
      avg(process_cpu_usage{application="meta-driven"}) > 0.85
    for: 2m
    labels:
      severity: critical
    annotations:
      summary: "CPU 使用率严重过高"
      description: "CPU 使用率 {{ $value | humanizePercentage }}，需要立即扩容"

  # 内存容量告警
  - alert: HighMemoryUsage
    expr: |
      sum(jvm_memory_used_bytes{area="heap",application="meta-driven"})
      / sum(jvm_memory_max_bytes{area="heap",application="meta-driven"}) > 0.8
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "堆内存使用率过高"
      description: "堆内存使用率 {{ $value | humanizePercentage }}"

  # GC 压力告警
  - alert: HighGCPressure
    expr: |
      rate(jvm_gc_pause_seconds_sum{application="meta-driven"}[5m])
      / rate(jvm_gc_pause_seconds_count{application="meta-driven"}[5m]) > 0.02
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "GC 暂停时间过长"
      description: "平均 GC 暂停时间 {{ $value | humanizeDuration }}"

  # 延迟容量告警
  - alert: HighLatency
    expr: |
      histogram_quantile(0.99,
        sum(rate(http_server_requests_seconds_bucket{application="meta-driven"}[5m])) by (le)
      ) > 0.05
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "请求延迟过高"
      description: "P99 延迟 {{ $value | humanizeDuration }}，接近容量上限"

  # 副本数告警
  - alert: NearMaxReplicas
    expr: |
      kube_deployment_status_replicas{deployment="meta-driven"}
      / kube_deployment_spec_replicas{deployment="meta-driven"} > 0.9
    for: 10m
    labels:
      severity: warning
    annotations:
      summary: "副本数接近上限"
      description: "当前副本数 {{ $value }}，接近 HPA 上限"
```

### Grafana 容量 Dashboard

```json
{
  "dashboard": {
    "title": "Capacity Planning Dashboard",
    "panels": [
      {
        "title": "Current vs Max Capacity",
        "type": "gauge",
        "targets": [
          {
            "expr": "sum(rate(http_server_requests_seconds_count[1m])) / (count(up{application=\"meta-driven\"}) * 1000)",
            "legendFormat": "Capacity Utilization"
          }
        ],
        "thresholds": {
          "steps": [
            {"color": "green", "value": null},
            {"color": "yellow", "value": 70},
            {"color": "red", "value": 85}
          ]
        }
      },
      {
        "title": "Resource Utilization Trend",
        "type": "timeseries",
        "targets": [
          {
            "expr": "avg(process_cpu_usage{application=\"meta-driven\"}) * 100",
            "legendFormat": "CPU %"
          },
          {
            "expr": "sum(jvm_memory_used_bytes{area=\"heap\"}) / sum(jvm_memory_max_bytes{area=\"heap\"}) * 100",
            "legendFormat": "Heap %"
          }
        ]
      },
      {
        "title": "Scaling Events",
        "type": "timeseries",
        "targets": [
          {
            "expr": "kube_deployment_status_replicas{deployment=\"meta-driven\"}",
            "legendFormat": "Replicas"
          }
        ]
      },
      {
        "title": "Capacity Forecast (7d)",
        "type": "timeseries",
        "targets": [
          {
            "expr": "predict_linear(sum(rate(http_server_requests_seconds_count[1h]))[7d:1h], 86400 * 7)",
            "legendFormat": "Predicted QPS"
          }
        ]
      }
    ]
  }
}
```

---

## 成本优化

### 资源利用率目标

| 资源 | 目标利用率 | 最低利用率 | 说明 |
|------|------------|------------|------|
| CPU | 60-70% | 40% | 留出应对突发流量 |
| 内存 | 70-80% | 50% | 考虑 GC 和缓存 |
| 存储 | 70% | 50% | 预留日志增长空间 |

### 成本优化策略

```yaml
cost_optimization:
  # 1. 按需实例 vs 预留实例
  instance_strategy:
    baseline: reserved  # 基准负载使用预留实例
    variable: spot      # 弹性负载使用 Spot 实例
    ratio: 70:30        # 70% 预留 + 30% 按需

  # 2. 自动缩容
  auto_scaling:
    scale_down:
      enabled: true
      stabilization_window: 10m
      min_replicas: 2
      # 非工作时间缩容
      schedule:
        - cron: "0 22 * * 1-5"  # 工作日晚 10 点
          minReplicas: 2
        - cron: "0 8 * * 1-5"   # 工作日早 8 点
          minReplicas: 3

  # 3. 存储优化
  storage:
    log_retention: 7d       # 缩短日志保留
    compression: gzip       # 启用压缩
    cold_storage: s3        # 冷数据归档

  # 4. 网络优化
  network:
    same_az_traffic: true   # 同可用区通信
    vpc_endpoint: true      # 使用 VPC 端点
```

### 成本估算模板

```
月度成本估算 = 计算成本 + 存储成本 + 网络成本 + 其他

计算成本:
- 应用实例: 3 × $100/月 = $300
- 数据库: 1 × $200/月 = $200
- 缓存: 1 × $100/月 = $100
小计: $600/月

存储成本:
- 数据库存储: 100GB × $0.1 = $10
- 日志存储: 50GB × $0.05 = $2.5
- 备份存储: 200GB × $0.02 = $4
小计: $16.5/月

网络成本:
- 数据传输出站: 100GB × $0.09 = $9
- 负载均衡: $20/月
小计: $29/月

总计: $645.5/月 (不含增长预留)
```

---

## 附录

### A. 快速参考卡片

```
┌─────────────────────────────────────────────────────────────┐
│                    容量规划速查表                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  QPS 估算:                                                  │
│  └─ 单实例 QPS ≈ CPU 核数 × 500 (低延迟场景)                  │
│                                                             │
│  内存估算:                                                   │
│  └─ 容器内存 ≈ JVM Heap × 1.7                               │
│                                                             │
│  实例数估算:                                                 │
│  └─ 实例数 = 峰值 QPS / (单实例 QPS × 0.7)                   │
│                                                             │
│  扩容触发:                                                   │
│  └─ CPU > 70% 或 P99 > 50ms 或 内存 > 80%                   │
│                                                             │
│  关键 JVM 参数:                                              │
│  └─ -XX:+UseG1GC -XX:MaxGCPauseMillis=10                   │
│  └─ -XX:+AlwaysPreTouch -Xms=Xmx                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### B. 常见问题

**Q: 如何确定初始容量?**
A: 建议从 3 个标准实例开始，根据监控数据调整。初期保守预估，后期根据实际负载优化。

**Q: 何时应该垂直扩展 vs 水平扩展?**
A: 优先水平扩展。当单实例 CPU/内存利用率低但延迟高时，考虑垂直扩展。

**Q: 如何处理突发流量?**
A: 配置 HPA 快速扩容策略，保持 20-30% 的容量裕度，考虑使用 Spot 实例处理突发。

**Q: GC 暂停影响延迟怎么办?**
A: 调整 G1GC 参数，增加 Heap 大小，或考虑使用 ZGC (需要 JDK 15+)。

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| 1.0.0 | 2025-01-01 | 初始版本 |

---

## 相关文档

- [可观测性方案](./observability-solution.md) - 监控指标详细说明
- [自动化部署方案](./auto.md) - 蓝绿部署和金丝雀发布
- [启动指南](./start.md) - 快速开始