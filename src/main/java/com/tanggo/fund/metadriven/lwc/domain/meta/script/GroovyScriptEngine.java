package com.tanggo.fund.metadriven.lwc.domain.meta.script;

//import groovy.lang.Binding;
//import groovy.lang.GroovyShell;
//import groovy.lang.Script;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Groovy脚本引擎实现
 * 支持完整的Java语法和Groovy扩展语法
 *
 * 特性：
 * - 完全兼容Java语法
 * - 支持动态类型和闭包
 * - 支持操作符重载
 * - 高性能脚本编译和缓存
 */
public class GroovyScriptEngine implements ScriptEngine {

    private final GroovyShell shell;

    // 脚本缓存：scriptCode -> 编译后的Script对象
    private final Map<String, Script> scriptCache = new ConcurrentHashMap<>();

    // 是否启用脚本缓存（默认启用）
    private final boolean cacheEnabled;

    public GroovyScriptEngine() {
        this(true);
    }

    public GroovyScriptEngine(boolean cacheEnabled) {
        this.shell = new GroovyShell();
        this.cacheEnabled = cacheEnabled;
    }

    @Override
    public String getName() {
        return "groovy";
    }

    @Override
    public boolean supports(String scriptType) {
        return "groovy".equalsIgnoreCase(scriptType) ||
               "gvy".equalsIgnoreCase(scriptType);
    }

    @Override
    public Object execute(String scriptCode, Object inputs) throws ScriptExecutionException {
        try {
            // 尝试从缓存获取编译后的脚本
            Script script;
            if (cacheEnabled) {
                script = scriptCache.computeIfAbsent(scriptCode, code -> {
                    try {
                        return shell.parse(code);
                    } catch (Exception e) {
                        throw new RuntimeException("脚本编译失败", e);
                    }
                });
            } else {
                script = shell.parse(scriptCode);
            }

            // 创建新的绑定以避免线程安全问题
            Binding binding = new Binding();
            binding.setVariable("inputs", inputs);

            // 克隆脚本并设置新绑定
            Script clonedScript = (Script) script.getClass().getDeclaredConstructor().newInstance();
            clonedScript.setBinding(binding);

            // 执行脚本
            return clonedScript.run();

        } catch (Exception e) {
            throw new ScriptExecutionException(
                "Groovy脚本执行失败: " + e.getMessage(), e
            );
        }
    }

    @Override
    public Object compile(String scriptCode) throws ScriptExecutionException {
        try {
            return shell.parse(scriptCode);
        } catch (Exception e) {
            throw new ScriptExecutionException(
                "Groovy脚本编译失败: " + e.getMessage(), e
            );
        }
    }

    @Override
    public Object executeCompiled(Object compiledScript, Object inputs) throws ScriptExecutionException {
        if (!(compiledScript instanceof Script)) {
            throw new ScriptExecutionException(
                "无效的编译脚本对象，期望 groovy.lang.Script 类型"
            );
        }

        try {
            Script script = (Script) compiledScript;

            // 创建新绑定
            Binding binding = new Binding();
            binding.setVariable("inputs", inputs);

            // 克隆并执行
            Script clonedScript = (Script) script.getClass().getDeclaredConstructor().newInstance();
            clonedScript.setBinding(binding);

            return clonedScript.run();

        } catch (Exception e) {
            throw new ScriptExecutionException(
                "Groovy预编译脚本执行失败: " + e.getMessage(), e
            );
        }
    }

    /**
     * 清空脚本缓存
     */
    public void clearCache() {
        scriptCache.clear();
    }

    /**
     * 获取缓存中的脚本数量
     */
    public int getCacheSize() {
        return scriptCache.size();
    }
}
