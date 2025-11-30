package com.tanggo.fund.metadriven.observability;

import io.micrometer.core.instrument.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * 锁竞争监控
 * 用于发现锁争用导致的延迟
 */
@Component
public class LockContentionMetrics {

    private final MeterRegistry registry;
    private final Timer lockAcquisitionTime;
    private final Counter lockContentionCount;
    private final Counter lockTimeoutCount;

    public LockContentionMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.lockAcquisitionTime = Timer.builder("lock.acquisition.time")
                .description("Time to acquire lock")
                .publishPercentiles(0.5, 0.95, 0.99, 0.999)
                .register(registry);

        this.lockContentionCount = Counter.builder("lock.contention.total")
                .description("Lock contention count (failed tryLock)")
                .register(registry);

        this.lockTimeoutCount = Counter.builder("lock.timeout.total")
                .description("Lock acquisition timeout count")
                .register(registry);
    }

    /**
     * 带监控的锁执行
     *
     * @param lock   锁对象
     * @param action 执行逻辑
     * @param <T>    返回类型
     * @return 执行结果
     */
    public <T> T measureLock(Lock lock, Supplier<T> action) {
        long start = System.nanoTime();
        boolean acquired = lock.tryLock();

        if (!acquired) {
            lockContentionCount.increment();
            lock.lock();
        }

        try {
            lockAcquisitionTime.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 带监控的锁执行（无返回值）
     *
     * @param lock   锁对象
     * @param action 执行逻辑
     */
    public void measureLock(Lock lock, Runnable action) {
        long start = System.nanoTime();
        boolean acquired = lock.tryLock();

        if (!acquired) {
            lockContentionCount.increment();
            lock.lock();
        }

        try {
            lockAcquisitionTime.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            action.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 带超时的锁执行
     *
     * @param lock      锁对象
     * @param timeoutMs 超时时间（毫秒）
     * @param action    执行逻辑
     * @param <T>       返回类型
     * @return 执行结果
     * @throws InterruptedException 如果等待被中断
     */
    public <T> T measureLockWithTimeout(Lock lock, long timeoutMs, Supplier<T> action)
            throws InterruptedException {
        long start = System.nanoTime();
        boolean acquired = lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);

        if (!acquired) {
            lockTimeoutCount.increment();
            throw new IllegalStateException("Lock acquisition timeout after " + timeoutMs + "ms");
        }

        try {
            lockAcquisitionTime.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 带名称标签的锁监控
     *
     * @param lockName 锁名称
     * @param lock     锁对象
     * @param action   执行逻辑
     * @param <T>      返回类型
     * @return 执行结果
     */
    public <T> T measureLock(String lockName, Lock lock, Supplier<T> action) {
        long start = System.nanoTime();
        boolean acquired = lock.tryLock();

        if (!acquired) {
            Counter.builder("lock.contention.total")
                    .tag("name", lockName)
                    .register(registry)
                    .increment();
            lock.lock();
        }

        try {
            Timer.builder("lock.acquisition.time")
                    .tag("name", lockName)
                    .register(registry)
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 记录锁竞争事件
     *
     * @param lockName 锁名称
     */
    public void recordContention(String lockName) {
        Counter.builder("lock.contention.total")
                .tag("name", lockName)
                .register(registry)
                .increment();
    }

    /**
     * 记录锁获取时间
     *
     * @param lockName    锁名称
     * @param acquireNanos 获取时间（纳秒）
     */
    public void recordAcquisitionTime(String lockName, long acquireNanos) {
        Timer.builder("lock.acquisition.time")
                .tag("name", lockName)
                .register(registry)
                .record(acquireNanos, TimeUnit.NANOSECONDS);
    }
}