package com.tanggo.fund.metadriven.observability;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

/**
 * 内存分配监控
 * 用于追踪分配速率和 GC 压力
 */
@Component
public class MemoryAllocationMetrics {

    public MemoryAllocationMetrics(MeterRegistry registry) {
        // 获取所有内存池
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();

        for (MemoryPoolMXBean pool : pools) {
            String poolName = normalizePoolName(pool.getName());

            // 内存池使用量
            Gauge.builder("jvm.memory.pool.used", pool, p -> {
                        MemoryUsage usage = p.getUsage();
                        return usage != null ? usage.getUsed() : 0;
                    })
                    .description("Memory pool used bytes")
                    .tag("pool", poolName)
                    .baseUnit("bytes")
                    .register(registry);

            // 内存池已提交
            Gauge.builder("jvm.memory.pool.committed", pool, p -> {
                        MemoryUsage usage = p.getUsage();
                        return usage != null ? usage.getCommitted() : 0;
                    })
                    .description("Memory pool committed bytes")
                    .tag("pool", poolName)
                    .baseUnit("bytes")
                    .register(registry);

            // 内存池最大值
            Gauge.builder("jvm.memory.pool.max", pool, p -> {
                        MemoryUsage usage = p.getUsage();
                        return usage != null && usage.getMax() != -1 ? usage.getMax() : 0;
                    })
                    .description("Memory pool max bytes")
                    .tag("pool", poolName)
                    .baseUnit("bytes")
                    .register(registry);

            // 内存池峰值
            Gauge.builder("jvm.memory.pool.peak", pool, p -> {
                        MemoryUsage peakUsage = p.getPeakUsage();
                        return peakUsage != null ? peakUsage.getUsed() : 0;
                    })
                    .description("Memory pool peak used bytes")
                    .tag("pool", poolName)
                    .baseUnit("bytes")
                    .register(registry);

            // 内存池利用率
            Gauge.builder("jvm.memory.pool.utilization", pool, p -> {
                        MemoryUsage usage = p.getUsage();
                        if (usage == null || usage.getMax() <= 0) {
                            return 0.0;
                        }
                        return (double) usage.getUsed() / usage.getMax();
                    })
                    .description("Memory pool utilization (used/max)")
                    .tag("pool", poolName)
                    .register(registry);
        }

        // 堆外内存监控
        Gauge.builder("jvm.buffer.memory.used",
                        () -> ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed())
                .description("Non-heap memory used")
                .baseUnit("bytes")
                .register(registry);

        Gauge.builder("jvm.buffer.memory.committed",
                        () -> ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getCommitted())
                .description("Non-heap memory committed")
                .baseUnit("bytes")
                .register(registry);

        // 总堆内存监控
        Gauge.builder("jvm.heap.used",
                        () -> ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed())
                .description("Heap memory used")
                .baseUnit("bytes")
                .register(registry);

        Gauge.builder("jvm.heap.committed",
                        () -> ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getCommitted())
                .description("Heap memory committed")
                .baseUnit("bytes")
                .register(registry);

        Gauge.builder("jvm.heap.max",
                        () -> ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax())
                .description("Heap memory max")
                .baseUnit("bytes")
                .register(registry);

        // 堆利用率
        Gauge.builder("jvm.heap.utilization", () -> {
                    MemoryUsage heapUsage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
                    if (heapUsage.getMax() <= 0) {
                        return 0.0;
                    }
                    return (double) heapUsage.getUsed() / heapUsage.getMax();
                })
                .description("Heap utilization (used/max)")
                .register(registry);
    }

    /**
     * 标准化内存池名称
     * 将空格和特殊字符转换为下划线
     */
    private String normalizePoolName(String name) {
        return name.toLowerCase()
                .replace(" ", "_")
                .replace("-", "_")
                .replace("'", "");
    }
}