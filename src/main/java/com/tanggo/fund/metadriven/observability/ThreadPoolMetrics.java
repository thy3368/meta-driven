package com.tanggo.fund.metadriven.observability;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池深度监控
 * 用于发现线程竞争和队列积压问题
 */
@Component
public class ThreadPoolMetrics {

    private final MeterRegistry registry;
    private final Timer queueWaitTimer;
    private final Counter rejectedCounter;

    public ThreadPoolMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.queueWaitTimer = Timer.builder("threadpool.queue.wait.time")
                .description("Time spent waiting in queue")
                .publishPercentiles(0.5, 0.95, 0.99, 0.999)
                .register(registry);

        this.rejectedCounter = Counter.builder("threadpool.rejected.total")
                .description("Rejected task count")
                .register(registry);
    }

    /**
     * 注册线程池监控
     *
     * @param name     线程池名称
     * @param executor 线程池执行器
     */
    public void registerExecutor(String name, ThreadPoolExecutor executor) {
        // 队列大小
        Gauge.builder("threadpool.queue.size", executor, e -> e.getQueue().size())
                .description("Current queue size")
                .tag("name", name)
                .register(registry);

        // 活跃线程数
        Gauge.builder("threadpool.active.count", executor, ThreadPoolExecutor::getActiveCount)
                .description("Active thread count")
                .tag("name", name)
                .register(registry);

        // 池大小
        Gauge.builder("threadpool.pool.size", executor, ThreadPoolExecutor::getPoolSize)
                .description("Current pool size")
                .tag("name", name)
                .register(registry);

        // 最大池大小
        Gauge.builder("threadpool.pool.max", executor, ThreadPoolExecutor::getMaximumPoolSize)
                .description("Maximum pool size")
                .tag("name", name)
                .register(registry);

        // 核心池大小
        Gauge.builder("threadpool.pool.core", executor, ThreadPoolExecutor::getCorePoolSize)
                .description("Core pool size")
                .tag("name", name)
                .register(registry);

        // 任务完成数
        FunctionCounter.builder("threadpool.completed.total", executor,
                        ThreadPoolExecutor::getCompletedTaskCount)
                .description("Completed task count")
                .tag("name", name)
                .register(registry);

        // 队列剩余容量
        Gauge.builder("threadpool.queue.remaining", executor,
                        e -> e.getQueue().remainingCapacity())
                .description("Queue remaining capacity")
                .tag("name", name)
                .register(registry);

        // 线程池利用率
        Gauge.builder("threadpool.utilization", executor,
                        e -> e.getPoolSize() > 0 ? (double) e.getActiveCount() / e.getPoolSize() : 0)
                .description("Thread pool utilization (active/pool size)")
                .tag("name", name)
                .register(registry);
    }

    /**
     * 记录队列等待时间
     *
     * @param waitNanos 等待时间（纳秒）
     */
    public void recordQueueWait(long waitNanos) {
        queueWaitTimer.record(waitNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 记录队列等待时间（带线程池名称）
     *
     * @param poolName  线程池名称
     * @param waitNanos 等待时间（纳秒）
     */
    public void recordQueueWait(String poolName, long waitNanos) {
        Timer.builder("threadpool.queue.wait.time")
                .tag("name", poolName)
                .register(registry)
                .record(waitNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * 记录拒绝任务
     */
    public void recordRejection() {
        rejectedCounter.increment();
    }

    /**
     * 记录拒绝任务（带线程池名称）
     *
     * @param poolName 线程池名称
     */
    public void recordRejection(String poolName) {
        Counter.builder("threadpool.rejected.total")
                .tag("name", poolName)
                .register(registry)
                .increment();
    }
}