package com.tanggo.fund.metadriven.lwc.dobject.atom;

import com.tanggo.fund.metadriven.lwc.dobject.atom.impl.BpmnLogicEngine;
import com.tanggo.fund.metadriven.lwc.dobject.atom.impl.GroovyLogicEngine;
import com.tanggo.fund.metadriven.lwc.dobject.atom.impl.JavaMethodLogicEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 执行引擎仓储 - 管理所有执行引擎
 * 使用策略模式，根据类型或上下文选择合适的引擎
 */
public class LogicEngineRepo {

    // 按类型索引的引擎缓存（性能优化）
    private final Map<String, LogicEngine> enginesByType = new ConcurrentHashMap<>();

    // 所有注册的引擎列表
    private final List<LogicEngine> engines = new ArrayList<>();

    /**
     * 默认构造器 - 注册内置引擎
     */
    public LogicEngineRepo() {
        registerDefaultEngines();
    }

    /**
     * 注册默认引擎（优先级从高到低）
     */
    private void registerDefaultEngines() {
        register(new JavaMethodLogicEngine());      // 优先级最高
        register(new GroovyLogicEngine());          // Groovy 脚本执行
        register(new BpmnLogicEngine());            // BPMN 流程执行
    }

    /**
     * 注册新的执行引擎
     */
    public void register(LogicEngine engine) {
        engines.add(engine);
        enginesByType.put(engine.getType(), engine);
    }

    /**
     * 根据类型查询引擎
     */
    public LogicEngine query(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("引擎类型不能为空");
        }

        LogicEngine engine = enginesByType.get(type);
        if (engine == null) {
            throw new IllegalArgumentException("找不到类型为 " + type + " 的执行引擎");
        }

        return engine;
    }

    /**
     * 根据上下文自动选择合适的引擎
     * 按优先级顺序查找第一个支持该上下文的引擎
     */
    public LogicEngine findEngine(LogicContext context) {
        for (LogicEngine engine : engines) {
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

