package com.tanggo.fund.metadriven.observability;

import io.micrometer.core.instrument.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 低延迟场景专用指标
 * 提供纳秒级精度的延迟监控，支持 P99.9/P99.99 等尾延迟指标
 */
@Component
public class LowLatencyMetrics {

    private final Timer processingTimer;
    private final DistributionSummary latencyDistribution;
    private final Counter timeoutCounter;
    private final AtomicLong maxLatencyNanos;
    private final AtomicLong minLatencyNanos;
    private final AtomicLong sampleCount;

    public LowLatencyMetrics(MeterRegistry registry) {
        // 处理时间 Timer (纳秒精度, 扩展到 P99.99)
        this.processingTimer = Timer.builder("business.processing.time")
                .description("Business processing time with nanosecond precision")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofNanos(100))
                .maximumExpectedValue(Duration.ofMillis(100))
                .register(registry);

        // 延迟分布 (微秒单位，用于分析)
        this.latencyDistribution = DistributionSummary.builder("business.latency.distribution")
                .description("Latency distribution in microseconds")
                .baseUnit("microseconds")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999)
                .register(registry);

        // 超时计数
        this.timeoutCounter = Counter.builder("business.timeout.total")
                .description("Number of timeouts")
                .register(registry);

        // 最大延迟 Gauge (滑动窗口)
        this.maxLatencyNanos = new AtomicLong(0);
        Gauge.builder("business.latency.max", maxLatencyNanos, AtomicLong::get)
                .description("Maximum latency in current window")
                .baseUnit("nanoseconds")
                .register(registry);

        // 最小延迟 Gauge
        this.minLatencyNanos = new AtomicLong(Long.MAX_VALUE);
        Gauge.builder("business.latency.min", minLatencyNanos, AtomicLong::get)
                .description("Minimum latency in current window")
                .baseUnit("nanoseconds")
                .register(registry);

        // 延迟抖动 Gauge
        this.sampleCount = new AtomicLong(0);
        Gauge.builder("business.latency.jitter", this, LowLatencyMetrics::getLatencyJitter)
                .description("Latency jitter (max - min) / 2")
                .baseUnit("nanoseconds")
                .register(registry);
    }

    /**
     * 记录延迟 (纳秒级)
     *
     * @param startNanos 开始时间 (System.nanoTime())
     */
    public void recordLatency(long startNanos) {
        long durationNanos = System.nanoTime() - startNanos;
        processingTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        latencyDistribution.record(durationNanos / 1000.0); // 转微秒

        // 更新最大/最小延迟
        maxLatencyNanos.updateAndGet(current -> Math.max(current, durationNanos));
        minLatencyNanos.updateAndGet(current -> Math.min(current, durationNanos));
        sampleCount.incrementAndGet();
    }

    /**
     * 记录延迟（直接传入纳秒值）
     *
     * @param durationNanos 延迟时间（纳秒）
     */
    public void recordLatencyNanos(long durationNanos) {
        processingTimer.record(durationNanos, TimeUnit.NANOSECONDS);
        latencyDistribution.record(durationNanos / 1000.0);

        maxLatencyNanos.updateAndGet(current -> Math.max(current, durationNanos));
        minLatencyNanos.updateAndGet(current -> Math.min(current, durationNanos));
        sampleCount.incrementAndGet();
    }

    /**
     * 记录超时
     */
    public void recordTimeout() {
        timeoutCounter.increment();
    }

    /**
     * 重置滑动窗口统计 (每分钟自动调用)
     */
    @Scheduled(fixedRate = 60000)
    public void resetWindow() {
        maxLatencyNanos.set(0);
        minLatencyNanos.set(Long.MAX_VALUE);
        sampleCount.set(0);
    }

    /**
     * 计算延迟抖动 (max - min) / 2
     */
    public double getLatencyJitter() {
        long max = maxLatencyNanos.get();
        long min = minLatencyNanos.get();
        if (min == Long.MAX_VALUE || sampleCount.get() < 2) {
            return 0;
        }
        return (max - min) / 2.0;
    }

    /**
     * 获取当前窗口内的最大延迟
     */
    public long getMaxLatencyNanos() {
        return maxLatencyNanos.get();
    }

    /**
     * 获取当前窗口内的最小延迟
     */
    public long getMinLatencyNanos() {
        long min = minLatencyNanos.get();
        return min == Long.MAX_VALUE ? 0 : min;
    }

    /**
     * 获取当前窗口内的样本数
     */
    public long getSampleCount() {
        return sampleCount.get();
    }
}