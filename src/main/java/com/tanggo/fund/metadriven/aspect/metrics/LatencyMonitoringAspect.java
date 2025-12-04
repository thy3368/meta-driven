package com.tanggo.fund.metadriven.aspect.metrics;

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
 * 时延监控切面 - 自动为所有Controller接口记录时延
 *
 * 功能:
 * 1. 自动监控所有@RestController的接口
 * 2. 为每个接口创建独立的Timer指标
 * 3. 记录P50, P95, P99, P99.9百分位数
 * 4. 支持HTTP方法和路径标签
 *
 * 指标命名规则:
 * - api.{controller}.{method}.latency
 * - 例如: api.hello.test.latency, api.hello.ping.latency
 */
@Aspect
@Component
public class LatencyMonitoringAspect {

    private static final Logger log = LoggerFactory.getLogger(LatencyMonitoringAspect.class);

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    public LatencyMonitoringAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("LatencyMonitoringAspect initialized - 自动监控所有Controller接口时延");
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

        // 构建指标名称: api.controller.method.latency
        String metricName = String.format("api.%s.%s.latency",
                className.toLowerCase().replace("controller", ""),
                methodName).replace("..", ".");

        // 获取或创建Timer
        Timer timer = timerCache.computeIfAbsent(metricName, name ->
                Timer.builder(name)
                        .description(String.format("%s %s - Endpoint latency", httpMethod, path))
                        .tag("controller", className)
                        .tag("method", methodName)
                        .tag("http_method", httpMethod)
                        .tag("path", path)
                        .publishPercentiles(0.5, 0.95, 0.99, 0.999)
                        .publishPercentileHistogram()
                        .register(meterRegistry)
        );

        // 记录时延 重点
        return timer.record(() -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
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
