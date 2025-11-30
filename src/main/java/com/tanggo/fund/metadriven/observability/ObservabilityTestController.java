package com.tanggo.fund.metadriven.observability;

import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 可观测性测试端点
 * 用于验证 Metrics、Logging、Tracing 是否正常工作
 */
@RestController
@RequestMapping("/api/observability")
public class ObservabilityTestController {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityTestController.class);
    private static final Random random = new Random();

    private final BusinessMetrics businessMetrics;
    private final LowLatencyMetrics lowLatencyMetrics;

    public ObservabilityTestController(BusinessMetrics businessMetrics,
                                       LowLatencyMetrics lowLatencyMetrics) {
        this.businessMetrics = businessMetrics;
        this.lowLatencyMetrics = lowLatencyMetrics;
    }

    /**
     * 测试端点 - 生成 Metrics + Logs + Trace
     */
    @GetMapping("/test")
    @Observed(name = "observability.test")
    public Map<String, Object> test(@RequestParam(defaultValue = "100") int delayMs) {
        log.info("Observability test started, delay={}ms", delayMs);

        long startNanos = System.nanoTime();

        // 模拟业务处理
        businessMetrics.recordCommand("test-command", () -> {
            try {
                TimeUnit.MILLISECONDS.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 记录低延迟指标
        lowLatencyMetrics.recordLatency(startNanos);

        log.info("Observability test completed");

        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("delayMs", delayMs);
        result.put("latencyNanos", System.nanoTime() - startNanos);
        return result;
    }

    /**
     * 测试随机延迟 - 用于生成延迟分布
     */
    @GetMapping("/random-latency")
    @Observed(name = "observability.random.latency")
    public Map<String, Object> randomLatency() {
        long startNanos = System.nanoTime();

        // 模拟随机延迟 (1-100ms)
        int delayMs = random.nextInt(100) + 1;

        try {
            TimeUnit.MILLISECONDS.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        lowLatencyMetrics.recordLatency(startNanos);
        businessMetrics.recordQuery();

        log.debug("Random latency: {}ms", delayMs);

        Map<String, Object> result = new HashMap<>();
        result.put("delayMs", delayMs);
        return result;
    }

    /**
     * 测试错误 - 用于验证错误指标
     */
    @GetMapping("/error")
    @Observed(name = "observability.error")
    public Map<String, Object> testError(@RequestParam(defaultValue = "false") boolean throwError) {
        log.warn("Error test endpoint called, throwError={}", throwError);

        if (throwError) {
            throw new RuntimeException("Test error for observability");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", "no-error");
        return result;
    }

    /**
     * 测试超时 - 用于验证超时指标
     */
    @GetMapping("/timeout")
    @Observed(name = "observability.timeout")
    public Map<String, Object> testTimeout(@RequestParam(defaultValue = "5000") int timeoutMs) {
        long startNanos = System.nanoTime();
        log.info("Timeout test started, will wait {}ms", timeoutMs);

        try {
            TimeUnit.MILLISECONDS.sleep(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lowLatencyMetrics.recordTimeout();
        }

        lowLatencyMetrics.recordLatency(startNanos);

        Map<String, Object> result = new HashMap<>();
        result.put("waitedMs", timeoutMs);
        return result;
    }

    /**
     * 健康检查端点
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UP");
        result.put("timestamp", System.currentTimeMillis());
        result.put("activeCommands", businessMetrics.getActiveCommands());
        result.put("maxLatencyNanos", lowLatencyMetrics.getMaxLatencyNanos());
        result.put("minLatencyNanos", lowLatencyMetrics.getMinLatencyNanos());
        result.put("sampleCount", lowLatencyMetrics.getSampleCount());
        result.put("latencyJitter", lowLatencyMetrics.getLatencyJitter());
        return result;
    }

    /**
     * 批量生成测试数据
     */
    @GetMapping("/generate")
    @Observed(name = "observability.generate")
    public Map<String, Object> generateTestData(@RequestParam(defaultValue = "100") int count) {
        log.info("Generating {} test data points", count);

        long startNanos = System.nanoTime();

        for (int i = 0; i < count; i++) {
            long opStart = System.nanoTime();

            // 模拟随机延迟 (0.1-10ms)
            int delayMicros = random.nextInt(9900) + 100;
            try {
                TimeUnit.MICROSECONDS.sleep(delayMicros);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            lowLatencyMetrics.recordLatency(opStart);

            if (random.nextDouble() < 0.5) {
                businessMetrics.recordCommand("generate-command", () -> {});
            } else {
                businessMetrics.recordQuery();
            }
        }

        long totalNanos = System.nanoTime() - startNanos;
        log.info("Generated {} test data points in {}ms", count, totalNanos / 1_000_000);

        Map<String, Object> result = new HashMap<>();
        result.put("generated", count);
        result.put("totalMs", totalNanos / 1_000_000);
        result.put("avgMicros", totalNanos / count / 1000);
        return result;
    }
}