package com.tanggo.fund.metadriven.observability;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务指标注册组件
 * 提供 CQRS 模式下的命令/查询指标监控
 */
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

    /**
     * 记录命令执行
     *
     * @param commandName 命令名称
     * @param execution   命令执行逻辑
     */
    public void recordCommand(String commandName, Runnable execution) {
        activeCommands.incrementAndGet();
        try {
            commandLatency.record(execution);
            commandCounter.increment();
        } finally {
            activeCommands.decrementAndGet();
        }
    }

    /**
     * 记录命令执行（带标签）
     *
     * @param commandName 命令名称
     * @param tags        额外标签
     * @param execution   命令执行逻辑
     */
    public void recordCommand(String commandName, Tags tags, Runnable execution) {
        activeCommands.incrementAndGet();
        try {
            commandLatency.record(execution);
            commandCounter.increment();
        } finally {
            activeCommands.decrementAndGet();
        }
    }

    /**
     * 记录查询执行
     */
    public void recordQuery() {
        queryCounter.increment();
    }

    /**
     * 记录查询执行（带标签）
     *
     * @param queryName 查询名称
     */
    public void recordQuery(String queryName) {
        queryCounter.increment();
    }

    /**
     * 获取当前活跃命令数
     */
    public long getActiveCommands() {
        return activeCommands.get();
    }
}