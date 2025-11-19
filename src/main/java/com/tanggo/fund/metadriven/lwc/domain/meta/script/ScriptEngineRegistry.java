package com.tanggo.fund.metadriven.lwc.domain.meta.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 脚本引擎注册表
 * 管理所有可用的脚本引擎，提供引擎查找和选择功能
 */
public class ScriptEngineRegistry {

    private static final ScriptEngineRegistry INSTANCE = new ScriptEngineRegistry();

    // 已注册的脚本引擎列表（按优先级排序）
    private final List<ScriptEngine> engines = new ArrayList<>();

    // 缓存：脚本类型 -> 脚本引擎
    private final Map<String, ScriptEngine> engineCache = new ConcurrentHashMap<>();

    private ScriptEngineRegistry() {
        // 默认注册所有内置引擎（按优先级从高到低）
        registerDefaultEngines();
    }

    public static ScriptEngineRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 注册默认的脚本引擎
     */
    private void registerDefaultEngines() {
        // 优先级1: Groovy（功能最强大，支持完整Java语法）
        try {
            Class.forName("groovy.lang.GroovyShell");
            register(new GroovyScriptEngine());
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // Groovy未在classpath中，跳过
        }

        // 优先级2: Janino（轻量级Java编译器）
        try {
            Class.forName("org.codehaus.janino.ScriptEvaluator");
            register(new JavaScriptEngine());
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // Janino未在classpath中，跳过
        }

        // 优先级3: Custom DSL（简单表达式语言）
        // 使用try-catch捕获所有可能的类加载错误
        try {
            register(new CustomDSLScriptEngine());
        } catch (NoClassDefFoundError | Exception e) {
            // CustomDSL依赖的类不可用，跳过
        }
    }

    /**
     * 注册脚本引擎
     * @param engine 脚本引擎实例
     */
    public synchronized void register(ScriptEngine engine) {
        if (engine == null) {
            throw new IllegalArgumentException("脚本引擎不能为null");
        }

        // 检查是否已注册同名引擎
        for (ScriptEngine existing : engines) {
            if (existing.getName().equals(engine.getName())) {
                throw new IllegalArgumentException(
                    "脚本引擎已注册: " + engine.getName()
                );
            }
        }

        engines.add(engine);
        engineCache.clear(); // 清空缓存，重新计算
    }

    /**
     * 注销脚本引擎
     * @param engineName 引擎名称
     */
    public synchronized void unregister(String engineName) {
        engines.removeIf(e -> e.getName().equals(engineName));
        engineCache.clear();
    }

    /**
     * 根据脚本类型查找合适的引擎
     * @param scriptType 脚本类型（如 "java", "groovy", "dsl"）
     * @return 匹配的脚本引擎，如果没有则返回null
     */
    public ScriptEngine getEngine(String scriptType) {
        if (scriptType == null || scriptType.isBlank()) {
            return getDefaultEngine();
        }

        // 缓存查找
        return engineCache.computeIfAbsent(scriptType.toLowerCase(), type -> {
            for (ScriptEngine engine : engines) {
                if (engine.supports(type)) {
                    return engine;
                }
            }
            return null;
        });
    }

    /**
     * 获取默认引擎（优先级最高的引擎）
     */
    public ScriptEngine getDefaultEngine() {
        return engines.isEmpty() ? null : engines.get(0);
    }

    /**
     * 获取所有已注册的引擎
     */
    public List<ScriptEngine> getAllEngines() {
        return new ArrayList<>(engines);
    }

    /**
     * 自动检测脚本类型并获取引擎
     * @param scriptCode 脚本代码
     * @return 推测的脚本引擎
     */
    public ScriptEngine detectEngine(String scriptCode) {
        if (scriptCode == null || scriptCode.isBlank()) {
            return getDefaultEngine();
        }

        // 简单的启发式检测
        String trimmed = scriptCode.trim();

        // 检测 Groovy 特有语法
        if (trimmed.contains("def ") || trimmed.contains("?.") ||
            trimmed.contains("*.") || trimmed.contains("=~")) {
            ScriptEngine groovy = getEngine("groovy");
            if (groovy != null) return groovy;
        }

        // 检测 Java 语法特征
        if (trimmed.contains("import ") || trimmed.contains("class ") ||
            trimmed.contains("public ") || trimmed.contains("private ")) {
            ScriptEngine java = getEngine("java");
            if (java != null) return java;
        }

        // 检测 DSL 简单表达式
        if (trimmed.matches("^[a-zA-Z_][a-zA-Z0-9_]*\\s*[+\\-*/].*")) {
            ScriptEngine dsl = getEngine("dsl");
            if (dsl != null) return dsl;
        }

        // 默认返回优先级最高的引擎
        return getDefaultEngine();
    }
}
