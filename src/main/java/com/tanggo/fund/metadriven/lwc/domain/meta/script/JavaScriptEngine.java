package com.tanggo.fund.metadriven.lwc.domain.meta.script;

import org.codehaus.janino.ScriptEvaluator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java脚本引擎实现（基于Janino）
 * 支持标准Java语法的编译和执行
 *
 * 特性：
 * - 轻量级Java编译器
 * - 支持完整Java语法
 * - 快速编译和执行
 * - 无需JDK，只需JRE
 *
 * 限制：
 * - 不支持Java 17的新特性（如records、sealed classes）
 * - 需要显式导入类（不支持静态导入）
 */
public class JavaScriptEngine implements ScriptEngine {

    // 脚本缓存：scriptCode -> 编译后的ScriptEvaluator
    private final Map<String, ScriptEvaluator> evaluatorCache = new ConcurrentHashMap<>();

    private final boolean cacheEnabled;

    public JavaScriptEngine() {
        this(true);
    }

    public JavaScriptEngine(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    @Override
    public String getName() {
        return "java";
    }

    @Override
    public boolean supports(String scriptType) {
        return "java".equalsIgnoreCase(scriptType) ||
               "janino".equalsIgnoreCase(scriptType);
    }

    @Override
    public Object execute(String scriptCode, Object inputs) throws ScriptExecutionException {
        try {
            ScriptEvaluator evaluator;

            if (cacheEnabled) {
                evaluator = evaluatorCache.computeIfAbsent(scriptCode, code -> {
                    try {
                        return compileScript(code);
                    } catch (Exception e) {
                        throw new RuntimeException("Java脚本编译失败", e);
                    }
                });
            } else {
                evaluator = compileScript(scriptCode);
            }

            // 执行脚本，传入inputs参数
            return evaluator.evaluate(new Object[]{inputs});

        } catch (Exception e) {
            throw new ScriptExecutionException(
                "Java脚本执行失败: " + e.getMessage(), e
            );
        }
    }

    /**
     * 编译Java脚本
     * @param scriptCode 脚本代码
     * @return 编译后的ScriptEvaluator
     */
    private ScriptEvaluator compileScript(String scriptCode) throws Exception {
        ScriptEvaluator evaluator = new ScriptEvaluator();

        // 设置参数：inputs
        evaluator.setParameters(
            new String[]{"inputs"},           // 参数名
            new Class<?>[]{Object.class}      // 参数类型
        );

        // 设置返回类型为Object（自动推断）
        evaluator.setReturnType(Object.class);

        // 添加常用导入（减少用户编写导入语句）
        evaluator.setDefaultImports(
            "java.util.*",
            "java.math.*",
            "java.time.*",
            "com.google.gson.*"
        );

        // 编译脚本
        evaluator.cook(scriptCode);

        return evaluator;
    }

    @Override
    public Object compile(String scriptCode) throws ScriptExecutionException {
        try {
            return compileScript(scriptCode);
        } catch (Exception e) {
            throw new ScriptExecutionException(
                "Java脚本编译失败: " + e.getMessage(), e
            );
        }
    }

    @Override
    public Object executeCompiled(Object compiledScript, Object inputs) throws ScriptExecutionException {
        if (!(compiledScript instanceof ScriptEvaluator)) {
            throw new ScriptExecutionException(
                "无效的编译脚本对象，期望 ScriptEvaluator 类型"
            );
        }

        try {
            ScriptEvaluator evaluator = (ScriptEvaluator) compiledScript;
            return evaluator.evaluate(new Object[]{inputs});
        } catch (Exception e) {
            throw new ScriptExecutionException(
                "Java预编译脚本执行失败: " + e.getMessage(), e
            );
        }
    }

    /**
     * 清空脚本缓存
     */
    public void clearCache() {
        evaluatorCache.clear();
    }

    /**
     * 获取缓存中的脚本数量
     */
    public int getCacheSize() {
        return evaluatorCache.size();
    }
}
