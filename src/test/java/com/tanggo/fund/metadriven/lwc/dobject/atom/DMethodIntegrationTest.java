package com.tanggo.fund.metadriven.lwc.dobject.atom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DMethodIntegrationTest {

    @Test
    void testJavaMethodExecution() throws Exception {
        // 准备测试类和方法
        Method testMethod = TestService.class.getMethod("processOrder", String.class);

        DMethod dMethod = new DMethod();
        dMethod.setName("processOrder");
        dMethod.setJavaMethod(testMethod);
        dMethod.setDeclaringClass(TestService.class);
        dMethod.setInput(String.class);
        dMethod.setOutput(String.class);

        assertTrue(dMethod.hasJavaMethod());
        assertFalse(dMethod.inputIsDynamic());
        assertFalse(dMethod.outputIsDynamic());
        assertEquals("processOrder", dMethod.getName());
    }

    @Test
    void testInvokeJavaMethod() throws Exception {
        Method testMethod = TestService.class.getMethod("processOrder", String.class);

        DMethod dMethod = new DMethod();
        dMethod.setName("processOrder");
        dMethod.setJavaMethod(testMethod);
        dMethod.setDeclaringClass(TestService.class);
        dMethod.setInput(String.class);
        dMethod.setOutput(String.class);

        Object result = dMethod.invoke("ORDER-123");

        assertNotNull(result);
        assertEquals("Processed: ORDER-123", result);
    }

    @Test
    void testInvokeStaticMethod() throws Exception {
        Method staticMethod = TestService.class.getMethod("calculateTotal", Double.class);

        DMethod dMethod = new DMethod();
        dMethod.setName("calculateTotal");
        dMethod.setJavaMethod(staticMethod);
        dMethod.setDeclaringClass(TestService.class);
        dMethod.setInput(Double.class);
        dMethod.setOutput(Double.class);

        Object result = dMethod.invoke(100.0);

        assertNotNull(result);
        assertEquals(110.0, (Double) result, 0.001);
    }

    @Test
    void testDynamicObjectInputOutput() {
        DMethod dMethod = new DMethod();
        dMethod.setInput(DynamicObject.class);
        dMethod.setOutput(DynamicObject.class);

        DClass inputClass = new DClass();
        inputClass.setName("InputDO");
        dMethod.setMInput(inputClass);

        DClass outputClass = new DClass();
        outputClass.setName("OutputDO");
        dMethod.setMOutput(outputClass);

        assertTrue(dMethod.inputIsDynamic());
        assertTrue(dMethod.outputIsDynamic());
        assertEquals("InputDO", dMethod.getMInput().getName());
        assertEquals("OutputDO", dMethod.getMOutput().getName());
    }

    @Test
    void testScriptMethodConfig() {
        DMethod dMethod = new DMethod();
        dMethod.setName("calculatePrice");
        dMethod.setScriptFilePath("scripts/pricing.groovy");
        dMethod.setScriptType("groovy");
        dMethod.setInput(Double.class);
        dMethod.setOutput(Double.class);

        assertFalse(dMethod.hasJavaMethod());
        assertEquals("scripts/pricing.groovy", dMethod.getScriptFilePath());
        assertEquals("groovy", dMethod.getScriptType());
    }

    @Test
    void testMethodWithAnnotations() {
        DAnnotation ann1 = new DAnnotation();
        ann1.setPropertyMap(java.util.Map.of("type", "Transactional"));

        DAnnotation ann2 = new DAnnotation();
        ann2.setPropertyMap(java.util.Map.of("type", "Cacheable"));

        List<DAnnotation> annotations = new ArrayList<>();
        annotations.add(ann1);
        annotations.add(ann2);

        DMethod dMethod = new DMethod();
        dMethod.setName("saveOrder");
        dMethod.setMethodAnnList(annotations);

        assertNotNull(dMethod.getMethodAnnList());
        assertEquals(2, dMethod.getMethodAnnList().size());
        assertEquals("Transactional", dMethod.getMethodAnnList().get(0).getValue("type"));
    }

    @Test
    void testPrecompiledScriptClass() {
        DMethod dMethod = new DMethod();
        dMethod.setScriptClassName("com.tanggo.fund.scripts.OrderValidator");
        dMethod.setInput(Object.class);
        dMethod.setOutput(Boolean.class);

        assertFalse(dMethod.hasJavaMethod());
        assertEquals("com.tanggo.fund.scripts.OrderValidator", dMethod.getScriptClassName());
    }

    // 测试用辅助类
    public static class TestService {
        public String processOrder(String orderId) {
            return "Processed: " + orderId;
        }

        public static Double calculateTotal(Double amount) {
            return amount * 1.1; // 加10%税
        }
    }
}