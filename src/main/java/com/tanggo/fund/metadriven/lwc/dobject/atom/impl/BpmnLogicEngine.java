package com.tanggo.fund.metadriven.lwc.dobject.atom.impl;

import com.tanggo.fund.metadriven.lwc.dobject.atom.LogicContext;
import com.tanggo.fund.metadriven.lwc.dobject.atom.LogicEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * BPMN 流程执行引擎
 * 专注于 BPMN 流程文件的动态加载和执行
 */
public class BpmnLogicEngine implements LogicEngine {

    @Override
    public Object invoke(Object inputs, LogicContext context) {
        try {
            // 读取 BPMN 流程定义文件
            String bpmnContent = readBpmnFile(context.getScriptFilePath());

            // 解析并执行 BPMN 流程
            return executeBpmnProcess(bpmnContent, inputs, context);

        } catch (IOException e) {
            throw new RuntimeException("读取 BPMN 流程文件失败: " + context.getScriptFilePath(), e);
        } catch (Exception e) {
            throw new RuntimeException("BPMN 流程执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getType() {
        return "bpmn";
    }

    @Override
    public boolean supports(LogicContext context) {
        // 检查是否有流程文件路径且为 .bpmn 或 .bpmn20.xml 文件
        if (context.getScriptFilePath() == null || context.getScriptFilePath().isBlank()) {
            return false;
        }

        String filePath = context.getScriptFilePath().toLowerCase();
        return filePath.endsWith(".bpmn") || filePath.endsWith(".bpmn20.xml");
    }

    /**
     * 读取 BPMN 流程文件内容
     */
    private String readBpmnFile(String filePath) throws IOException {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IOException("BPMN 流程文件不存在: " + filePath);
        }
        return Files.readString(path);
    }

    /**
     * 执行 BPMN 流程
     * 简化实现 - 解析 BPMN XML 并执行流程逻辑
     */
    private Object executeBpmnProcess(String bpmnContent, Object inputs, LogicContext context) {
        // 创建流程执行上下文
        BpmnProcessContext processContext = new BpmnProcessContext();
        processContext.setInputs(inputs);
        processContext.setBpmnContent(bpmnContent);
        processContext.setProcessName(context.getMethodName());

        // 解析 BPMN 流程定义
        BpmnProcessDefinition processDefinition = parseBpmnDefinition(bpmnContent);

        // 执行流程
        return executeProcess(processDefinition, processContext);
    }

    /**
     * 解析 BPMN 流程定义（简化版）
     * 实际应用中可集成 Camunda、Activiti 等 BPMN 引擎
     */
    private BpmnProcessDefinition parseBpmnDefinition(String bpmnContent) {
        BpmnProcessDefinition definition = new BpmnProcessDefinition();

        // 简化解析：提取流程 ID 和名称
        if (bpmnContent.contains("process id=")) {
            int start = bpmnContent.indexOf("process id=\"") + 12;
            int end = bpmnContent.indexOf("\"", start);
            if (end > start) {
                definition.setProcessId(bpmnContent.substring(start, end));
            }
        }

        if (bpmnContent.contains("name=")) {
            int start = bpmnContent.indexOf("name=\"") + 6;
            int end = bpmnContent.indexOf("\"", start);
            if (end > start) {
                definition.setProcessName(bpmnContent.substring(start, end));
            }
        }

        return definition;
    }

    /**
     * 执行流程（简化实现）
     */
    private Object executeProcess(BpmnProcessDefinition definition, BpmnProcessContext context) {
        Map<String, Object> result = new HashMap<>();
        result.put("processId", definition.getProcessId());
        result.put("processName", definition.getProcessName());
        result.put("status", "completed");
        result.put("inputs", context.getInputs());

        return result;
    }

    /**
     * BPMN 流程定义（简化版）
     */
    private static class BpmnProcessDefinition {
        private String processId;
        private String processName;

        public String getProcessId() {
            return processId;
        }

        public void setProcessId(String processId) {
            this.processId = processId;
        }

        public String getProcessName() {
            return processName;
        }

        public void setProcessName(String processName) {
            this.processName = processName;
        }
    }

    /**
     * BPMN 流程执行上下文
     */
    private static class BpmnProcessContext {
        private Object inputs;
        private String bpmnContent;
        private String processName;

        public Object getInputs() {
            return inputs;
        }

        public void setInputs(Object inputs) {
            this.inputs = inputs;
        }

        public String getBpmnContent() {
            return bpmnContent;
        }

        public void setBpmnContent(String bpmnContent) {
            this.bpmnContent = bpmnContent;
        }

        public String getProcessName() {
            return processName;
        }

        public void setProcessName(String processName) {
            this.processName = processName;
        }
    }
}
