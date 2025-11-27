package com.tanggo.fund.metadriven.lwc.dobject.atom.impl;

import com.tanggo.fund.metadriven.lwc.dobject.atom.LogicContext;
import com.tanggo.fund.metadriven.lwc.dobject.atom.LogicEngine;

import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Groovy 脚本执行引擎
 * 使用 JSR-223 标准脚本引擎接口执行 Groovy 脚本
 */
public class GroovyLogicEngine implements LogicEngine {

    // 脚本引擎管理器（单例模式，提高性能）
    private static final ScriptEngineManager scriptEngineManager = new ScriptEngineManager();

    @Override
    public Object invoke(Object inputs, LogicContext context) {
        try {
            // 1. 读取 Groovy 脚本文件
            String scriptContent = readScriptFile(context.getScriptFilePath());

            // 2. 获取 Groovy 脚本引擎（使用 JSR-223 标准）
            javax.script.ScriptEngine engine = scriptEngineManager.getEngineByName("groovy");
            if (engine == null) {
                throw new IllegalStateException(
                    "找不到 Groovy 脚本引擎，请确保依赖 groovy-jsr223 已添加到 classpath"
                );
            }

            // 3. 将输入参数绑定到脚本上下文
            if (inputs != null) {
                engine.put("inputs", inputs);
                engine.put("input", inputs); // 兼容两种变量名
            }

            // 4. 执行 Groovy 脚本
            return engine.eval(scriptContent);

        } catch (IOException e) {
            throw new RuntimeException(
                "读取 Groovy 脚本文件失败: " + context.getScriptFilePath(), e
            );
        } catch (ScriptException e) {
            throw new RuntimeException(
                "Groovy 脚本执行失败: " + e.getMessage() +
                " (文件: " + context.getScriptFilePath() + ", 行: " + e.getLineNumber() + ")",
                e
            );
        }
    }

    @Override
    public String getType() {
        return "groovy";
    }

    @Override
    public boolean supports(LogicContext context) {
        // 检查是否有脚本文件路径且为 .groovy 或 .gvy 文件
        if (context.getScriptFilePath() == null || context.getScriptFilePath().isBlank()) {
            return false;
        }

        String filePath = context.getScriptFilePath().toLowerCase();
        return filePath.endsWith(".groovy") || filePath.endsWith(".gvy");
    }

    /**
     * 读取脚本文件内容
     */
    private String readScriptFile(String filePath) throws IOException {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IOException("Groovy 脚本文件不存在: " + filePath);
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("不是有效的文件: " + filePath);
        }
        return Files.readString(path);
    }
}
