package com.tanggo.fund.metadriven.lwc.dobject.atom;

import com.tanggo.fund.metadriven.lwc.dobject.atom.impl.BpmnExecutionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BpmnExecutionEngine 集成测试
 */
@SpringBootTest
class BpmnExecutionEngineTest {

    private BpmnExecutionEngine engine;
    private ExecutionEngineRepo repo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        engine = new BpmnExecutionEngine();
        repo = new ExecutionEngineRepo();
    }

    @Test
    void testSupportsOnlyBpmnFiles() {
        // 支持 .bpmn 文件
        assertTrue(engine.supports(ExecutionContext.builder()
            .scriptFilePath("process.bpmn").build()));

        // 支持 .bpmn20.xml 文件
        assertTrue(engine.supports(ExecutionContext.builder()
            .scriptFilePath("process.bpmn20.xml").build()));

        // 不支持其他类型
        assertFalse(engine.supports(ExecutionContext.builder()
            .scriptFilePath("process.xml").build()));
        assertFalse(engine.supports(ExecutionContext.builder()
            .type("bpmn").build()));  // 没有文件路径
    }

    @Test
    void testExecuteSimpleBpmnProcess() throws Exception {
        String bpmnContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <process id="orderProcess" name="Order Processing">
                <startEvent id="start"/>
                <userTask id="validateOrder" name="Validate Order"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """;

        Path bpmnPath = tempDir.resolve("orderProcess.bpmn");
        Files.writeString(bpmnPath, bpmnContent);

        ExecutionContext context = ExecutionContext.builder()
            .scriptFilePath(bpmnPath.toString())
            .methodName("orderProcess")
            .build();

        Object result = engine.invoke(null, context);

        assertNotNull(result);
        assertInstanceOf(Map.class, result);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("orderProcess", resultMap.get("processId"));
        assertEquals("Order Processing", resultMap.get("processName"));
        assertEquals("completed", resultMap.get("status"));
    }

    @Test
    void testBpmnProcessWithInputData() throws Exception {
        String bpmnContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <process id="paymentProcess" name="Payment Processing">
                <startEvent id="start"/>
                <serviceTask id="calculateAmount" name="Calculate Amount"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """;

        Path bpmnPath = tempDir.resolve("payment.bpmn");
        Files.writeString(bpmnPath, bpmnContent);

        ExecutionContext context = ExecutionContext.builder()
            .scriptFilePath(bpmnPath.toString())
            .methodName("paymentProcess")
            .build();

        Map<String, Object> inputs = Map.of(
            "orderId", "ORDER-123",
            "amount", 1000.0
        );

        Object result = engine.invoke(inputs, context);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;

        assertEquals("paymentProcess", resultMap.get("processId"));
        assertEquals("Payment Processing", resultMap.get("processName"));
        assertEquals(inputs, resultMap.get("inputs"));
    }

    @Test
    void testIntegrationWithExecutionEngineRepo() throws Exception {
        String bpmnContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <process id="approvalProcess" name="Approval Process">
                <startEvent id="start"/>
                <userTask id="approve" name="Approve Request"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """;

        Path bpmnPath = tempDir.resolve("approval.bpmn");
        Files.writeString(bpmnPath, bpmnContent);

        ExecutionContext context = ExecutionContext.builder()
            .scriptFilePath(bpmnPath.toString())
            .build();

        // 通过仓储自动选择引擎
        ExecutionEngine foundEngine = repo.findEngine(context);
        assertEquals("bpmn", foundEngine.getType());

        Object result = foundEngine.invoke(null, context);
        assertNotNull(result);
    }

    @Test
    void testBpmnFileNotFound() {
        ExecutionContext context = ExecutionContext.builder()
            .scriptFilePath("/non/existent/process.bpmn")
            .build();

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> engine.invoke(null, context));
        assertTrue(exception.getMessage().contains("读取 BPMN 流程文件失败"));
    }

    @Test
    void testInvalidBpmnContent() throws Exception {
        // 无效的 BPMN 内容
        String invalidBpmn = "This is not a valid BPMN file";
        Path bpmnPath = tempDir.resolve("invalid.bpmn");
        Files.writeString(bpmnPath, invalidBpmn);

        ExecutionContext context = ExecutionContext.builder()
            .scriptFilePath(bpmnPath.toString())
            .build();

        // 简化版解析器会容忍无效内容，返回空流程定义
        Object result = engine.invoke(null, context);
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = (Map<String, Object>) result;
        assertEquals("completed", resultMap.get("status"));
    }
}