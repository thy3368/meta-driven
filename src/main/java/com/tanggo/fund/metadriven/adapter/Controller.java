package com.tanggo.fund.metadriven.adapter;

import org.springframework.web.bind.annotation.*;

/**
 * Hello Controller - 演示多个接口的时延监控
 *
 * 所有接口的时延会被LatencyMonitoringAspect自动监控
 *
 * 指标命名规则: api.hello.{methodName}.latency
 * - api.hello.test.latency
 * - api.hello.ping.latency
 * - api.hello.greet.latency
 *
 * 查看指标:
 * - curl http://localhost:8080/actuator/metrics/api.hello.test.latency
 * - curl http://localhost:8080/actuator/prometheus | grep api_hello
 */
@RestController
@RequestMapping("/api/hello")
public class Controller {

    /**
     * 测试端点
     * 指标: api.hello.test.latency
     */
    @GetMapping("/test")
    public String test() {
        return "hello world";
    }

    /**
     * Ping端点 - 健康检查
     * 指标: api.hello.ping.latency
     */
    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

    /**
     * 带参数的问候端点
     * 指标: api.hello.greet.latency
     */
    @GetMapping("/greet")
    public String greet(@RequestParam(defaultValue = "World") String name) {
        return "Hello, " + name + "!";
    }

    /**
     * POST端点示例
     * 指标: api.hello.echo.latency
     */
    @PostMapping("/echo")
    public String echo(@RequestBody String message) {
        return "Echo: " + message;
    }

    /**
     * 模拟慢接口
     * 指标: api.hello.slow.latency
     */
    @GetMapping("/slow")
    public String slow() throws InterruptedException {
        Thread.sleep(100); // 模拟100ms处理时间
        return "slow response";
    }
}
