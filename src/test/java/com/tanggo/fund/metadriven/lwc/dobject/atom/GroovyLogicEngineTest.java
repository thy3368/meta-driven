package com.tanggo.fund.metadriven.lwc.dobject.atom;

import com.tanggo.fund.metadriven.lwc.dobject.atom.impl.GroovyLogicEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GroovyExecutionEngine 集成测试
 * 精简版 - 只保留最有代表性的测试用例
 */
@SpringBootTest
class GroovyLogicEngineTest {

    private GroovyLogicEngine engine;
    private LogicEngineRepo repo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        engine = new GroovyLogicEngine();
        repo = new LogicEngineRepo();
    }

    @Test
    void testSupportsOnlyGroovyFiles() {
        // 支持 .groovy 文件
        assertTrue(engine.supports(LogicContext.builder()
            .scriptFilePath("test.groovy").build()));

        // 支持 .gvy 文件
        assertTrue(engine.supports(LogicContext.builder()
            .scriptFilePath("test.gvy").build()));

        // 不支持其他类型
        assertFalse(engine.supports(LogicContext.builder()
            .scriptFilePath("test.java").build()));
        assertFalse(engine.supports(LogicContext.builder()
            .type("groovy").build()));  // 没有文件路径
    }

    @Test
    void testExecuteSimpleGroovyScript() throws Exception {
        String scriptContent = "return inputs * 2";
        Path scriptPath = tempDir.resolve("calculate.groovy");
        Files.writeString(scriptPath, scriptContent);

        LogicContext context = LogicContext.builder()
            .scriptFilePath(scriptPath.toString())
            .build();

        Object result = engine.invoke(10, context);
        assertEquals(20, ((Number) result).intValue());
    }

    @Test
    void testGroovyScriptWithMapInput() throws Exception {
        String scriptContent = "return inputs.price * inputs.quantity";
        Path scriptPath = tempDir.resolve("calculateTotal.groovy");
        Files.writeString(scriptPath, scriptContent);

        LogicContext context = LogicContext.builder()
            .scriptFilePath(scriptPath.toString())
            .build();

        java.util.Map<String, Object> inputs = java.util.Map.of(
            "price", 100.0,
            "quantity", 5
        );

        Object result = engine.invoke(inputs, context);
        assertEquals(500.0, ((Number) result).doubleValue(), 0.001);
    }

    @Test
    void testGroovyClosureAndCollections() throws Exception {
        String scriptContent = """
            def square = { x -> x * x }
            inputs.collect { square(it) }
            """;

        Path scriptPath = tempDir.resolve("transform.groovy");
        Files.writeString(scriptPath, scriptContent);

        LogicContext context = LogicContext.builder()
            .scriptFilePath(scriptPath.toString())
            .build();

        java.util.List<Integer> inputs = java.util.List.of(1, 2, 3, 4);
        Object result = engine.invoke(inputs, context);

        assertInstanceOf(java.util.List.class, result);
        @SuppressWarnings("unchecked")
        java.util.List<Integer> resultList = (java.util.List<Integer>) result;
        assertEquals(java.util.List.of(1, 4, 9, 16), resultList);
    }

    @Test
    void testIntegrationWithExecutionEngineRepo() throws Exception {
        String scriptContent = "inputs * 3";
        Path scriptPath = tempDir.resolve("multiply.groovy");
        Files.writeString(scriptPath, scriptContent);

        LogicContext context = LogicContext.builder()
            .scriptFilePath(scriptPath.toString())
            .build();

        // 通过仓储自动选择引擎
        LogicEngine foundEngine = repo.findEngine(context);
        assertEquals("groovy", foundEngine.getType());

        Object result = foundEngine.invoke(7, context);
        assertEquals(21, ((Number) result).intValue());
    }

    @Test
    void testErrorHandling() throws Exception {
        // 文件不存在
        LogicContext notFoundContext = LogicContext.builder()
            .scriptFilePath("/non/existent/script.groovy")
            .build();

        RuntimeException notFoundEx = assertThrows(RuntimeException.class,
            () -> engine.invoke(null, notFoundContext));
        assertTrue(notFoundEx.getMessage().contains("读取 Groovy 脚本文件失败"));

        // 脚本执行异常
        String errorScript = "throw new RuntimeException('Script error!')";
        Path errorPath = tempDir.resolve("error.groovy");
        Files.writeString(errorPath, errorScript);

        LogicContext errorContext = LogicContext.builder()
            .scriptFilePath(errorPath.toString())
            .build();

        RuntimeException scriptEx = assertThrows(RuntimeException.class,
            () -> engine.invoke(null, errorContext));
        assertTrue(scriptEx.getMessage().contains("Groovy 脚本执行失败"));
    }
}
