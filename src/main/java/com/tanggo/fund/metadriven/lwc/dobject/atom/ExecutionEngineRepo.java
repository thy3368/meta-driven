package com.tanggo.fund.metadriven.lwc.dobject.atom;

import com.tanggo.fund.metadriven.lwc.dobject.atom.impl.BpmnExecutionEngine;
import com.tanggo.fund.metadriven.lwc.dobject.atom.impl.GroovyExecutionEngine;
import com.tanggo.fund.metadriven.lwc.dobject.atom.impl.JavaMethodExecutionEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行引擎仓储 - 管理所有执行引擎
 * 使用策略模式，根据类型或上下文选择合适的引擎
 */
public class ExecutionEngineRepo {

    // 按类型索引的引擎缓存（性能优化）
    private final Map<String, ExecutionEngine> enginesByType = new ConcurrentHashMap<>();

    // 所有注册的引擎列表
    private final List<ExecutionEngine> engines = new ArrayList<>();

    /**
     * 默认构造器 - 注册内置引擎
     */
    public ExecutionEngineRepo() {
        registerDefaultEngines();
    }

    /**
     * 注册默认引擎（优先级从高到低）
     */
    private void registerDefaultEngines() {
        register(new JavaMethodExecutionEngine());      // 优先级最高
        register(new GroovyExecutionEngine());          // Groovy 脚本执行
        register(new BpmnExecutionEngine());            // BPMN 流程执行
    }

    /**
     * 注册新的执行引擎
     */
    public void register(ExecutionEngine engine) {
        engines.add(engine);
        enginesByType.put(engine.getType(), engine);
    }

    /**
     * 根据类型查询引擎
     */
    public ExecutionEngine query(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("引擎类型不能为空");
        }

        ExecutionEngine engine = enginesByType.get(type);
        if (engine == null) {
            throw new IllegalArgumentException("找不到类型为 " + type + " 的执行引擎");
        }

        return engine;
    }

    /**
     * 根据上下文自动选择合适的引擎
     * 按优先级顺序查找第一个支持该上下文的引擎
     */
    public ExecutionEngine findEngine(ExecutionContext context) {
        for (ExecutionEngine engine : engines) {
            if (engine.supports(context)) {
                return engine;
            }
        }

        throw new IllegalStateException("找不到支持该执行上下文的引擎，方法名: " + context.getMethodName());
    }

    /**
     * 获取所有注册的引擎类型
     */
    public List<String> getSupportedTypes() {
        return new ArrayList<>(enginesByType.keySet());
    }
}

