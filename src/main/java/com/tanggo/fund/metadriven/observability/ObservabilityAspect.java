package com.tanggo.fund.metadriven.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.annotation.Observed;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 可观测性 AOP 切面
 * 自动为标注 @Observed 的方法添加指标和链路追踪
 *
 * 使用示例:
 * <pre>
 * {@code
 * @Observed(name = "process.order")
 * public Order processOrder(OrderCommand cmd) {
 *     // 自动记录指标和链路
 * }
 * }
 * </pre>
 */
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
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        }
                        throw new RuntimeException(e);
                    }
                });
    }
}