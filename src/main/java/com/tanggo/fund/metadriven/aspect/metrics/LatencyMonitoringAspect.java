package com.tanggo.fund.metadriven.aspect.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 时延、QPS和错误率监控切面 - 自动为所有Controller接口记录完整监控指标
 *
 * 功能:
 * 1. 自动监控所有@RestController的接口
 * 2. 为每个接口创建独立的Timer指标（时延）
 * 3. 为每个接口创建独立的Counter指标（QPS）
 * 4. 为每个接口创建成功/失败计数器（错误率）
 * 5. 记录P50, P95, P99, P99.9百分位数
 * 6. 支持HTTP方法和路径标签
 *
 * 指标命名规则:
 * - api.{method}.latency         - 时延指标
 * - api.{method}.requests        - 请求计数指标（用于计算QPS）
 * - api.{method}.requests.total  - 总请求数（按status标签区分成功/失败）
 *
 * QPS计算方式:
 * - 使用Prometheus: rate(api_test_requests_total[1m])
 *
 * 错误率计算方式:
 * - 使用Prometheus:
 *   rate(api_test_requests_total{status="error"}[1m]) /
 *   rate(api_test_requests_total[1m])
 */
@Aspect
@Component
public class LatencyMonitoringAspect {

    private static final Logger log = LoggerFactory.getLogger(LatencyMonitoringAspect.class);

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> successCounterCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> errorCounterCache = new ConcurrentHashMap<>();

    public LatencyMonitoringAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("LatencyMonitoringAspect initialized - 自动监控所有Controller接口时延、QPS和错误率");
    }

    /**
     * 拦截所有@RestController中的公共方法
     */
    @Around("@within(org.springframework.web.bind.annotation.RestController) && execution(public * *(..))")
    public Object monitorLatency(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();

        // 获取HTTP方法和路径
        String httpMethod = getHttpMethod(method);
        String path = getRequestPath(joinPoint.getTarget().getClass(), method);

        // 构建基础指标名称
        String baseMetricName = String.format("api.%s.%s",
                className.toLowerCase().replace("controller", ""),
                methodName).replace("..", ".");

        // 共用的标签
        String[] tags = new String[]{
                "controller", className,
                "method", methodName,
                "http_method", httpMethod,
                "path", path
        };

        // 1. 获取或创建Timer（时延指标）
        String latencyMetricName = baseMetricName + ".latency";
        Timer timer = timerCache.computeIfAbsent(latencyMetricName, name ->
                Timer.builder(name)
                        .description(String.format("%s %s - Endpoint latency", httpMethod, path))
                        .tags(tags)
                        .publishPercentiles(0.5, 0.95, 0.99, 0.999)
                        .publishPercentileHistogram()
                        .register(meterRegistry)
        );

        // 2. 获取或创建Counter（请求计数指标，用于QPS计算）
        String requestsMetricName = baseMetricName + ".requests";
        Counter counter = counterCache.computeIfAbsent(requestsMetricName, name ->
                Counter.builder(name)
                        .description(String.format("%s %s - Request count (for QPS)", httpMethod, path))
                        .tags(tags)
                        .register(meterRegistry)
        );

        // 3. 获取或创建Success Counter（成功计数）
        String successKey = baseMetricName + ".success";
        Counter successCounter = successCounterCache.computeIfAbsent(successKey, key -> {
            String[] successTags = new String[tags.length + 2];
            System.arraycopy(tags, 0, successTags, 0, tags.length);
            successTags[tags.length] = "status";
            successTags[tags.length + 1] = "success";

            return Counter.builder(baseMetricName + ".requests.total")
                    .description(String.format("%s %s - Successful requests", httpMethod, path))
                    .tags(successTags)
                    .register(meterRegistry);
        });

        // 4. 获取或创建Error Counter（失败计数）
        String errorKey = baseMetricName + ".error";
        Counter errorCounter = errorCounterCache.computeIfAbsent(errorKey, key -> {
            String[] errorTags = new String[tags.length + 2];
            System.arraycopy(tags, 0, errorTags, 0, tags.length);
            errorTags[tags.length] = "status";
            errorTags[tags.length + 1] = "error";

            return Counter.builder(baseMetricName + ".requests.total")
                    .description(String.format("%s %s - Failed requests", httpMethod, path))
                    .tags(errorTags)
                    .register(meterRegistry);
        });

        // 5. 计数总请求
        counter.increment();

        // 6. 执行方法并记录结果
        long startTime = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            // 成功
            successCounter.increment();
            long duration = System.nanoTime() - startTime;
            timer.record(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
            return result;
        } catch (Throwable e) {
            // 失败
            errorCounter.increment();
            long duration = System.nanoTime() - startTime;
            timer.record(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
            throw e;
        }
    }

    /**
     * 获取HTTP方法类型
     */
    private String getHttpMethod(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) {
            return "GET";
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            return "POST";
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            return "PUT";
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            return "DELETE";
        } else if (method.isAnnotationPresent(PatchMapping.class)) {
            return "PATCH";
        } else if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping mapping = method.getAnnotation(RequestMapping.class);
            return mapping.method().length > 0 ? mapping.method()[0].name() : "REQUEST";
        }
        return "UNKNOWN";
    }

    /**
     * 获取请求路径
     */
    private String getRequestPath(Class<?> clazz, Method method) {
        StringBuilder path = new StringBuilder();

        // 类级别的RequestMapping
        if (clazz.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping classMapping = clazz.getAnnotation(RequestMapping.class);
            if (classMapping.value().length > 0) {
                path.append(classMapping.value()[0]);
            } else if (classMapping.path().length > 0) {
                path.append(classMapping.path()[0]);
            }
        }

        // 方法级别的Mapping
        String methodPath = getMethodPath(method);
        if (!methodPath.isEmpty()) {
            if (!path.toString().endsWith("/") && !methodPath.startsWith("/")) {
                path.append("/");
            }
            path.append(methodPath);
        }

        return path.toString().isEmpty() ? "/" : path.toString();
    }

    /**
     * 获取方法级别的路径
     */
    private String getMethodPath(Method method) {
        if (method.isAnnotationPresent(GetMapping.class)) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            return mapping.value().length > 0 ? mapping.value()[0] :
                    mapping.path().length > 0 ? mapping.path()[0] : "";
        } else if (method.isAnnotationPresent(PostMapping.class)) {
            PostMapping mapping = method.getAnnotation(PostMapping.class);
            return mapping.value().length > 0 ? mapping.value()[0] :
                    mapping.path().length > 0 ? mapping.path()[0] : "";
        } else if (method.isAnnotationPresent(PutMapping.class)) {
            PutMapping mapping = method.getAnnotation(PutMapping.class);
            return mapping.value().length > 0 ? mapping.value()[0] :
                    mapping.path().length > 0 ? mapping.path()[0] : "";
        } else if (method.isAnnotationPresent(DeleteMapping.class)) {
            DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
            return mapping.value().length > 0 ? mapping.value()[0] :
                    mapping.path().length > 0 ? mapping.path()[0] : "";
        } else if (method.isAnnotationPresent(PatchMapping.class)) {
            PatchMapping mapping = method.getAnnotation(PatchMapping.class);
            return mapping.value().length > 0 ? mapping.value()[0] :
                    mapping.path().length > 0 ? mapping.path()[0] : "";
        } else if (method.isAnnotationPresent(RequestMapping.class)) {
            RequestMapping mapping = method.getAnnotation(RequestMapping.class);
            return mapping.value().length > 0 ? mapping.value()[0] :
                    mapping.path().length > 0 ? mapping.path()[0] : "";
        }
        return "";
    }
}
