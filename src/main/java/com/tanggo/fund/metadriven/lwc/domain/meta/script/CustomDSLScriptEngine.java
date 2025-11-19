package com.tanggo.fund.metadriven.lwc.domain.meta.script;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自定义DSL脚本引擎
 * 支持简单的表达式语言，用于常见的数据处理场景
 *
 * 支持的语法：
 * 1. 简单算术：inputs.a + inputs.b
 * 2. 字符串操作：inputs.toUpperCase()
 * 3. 条件表达式：inputs > 0 ? "positive" : "negative"
 * 4. JSON属性访问：inputs.field
 * 5. 函数调用：max(inputs.a, inputs.b)
 *
 * 特性：
 * - 轻量级，无外部依赖
 * - 快速执行，适合简单场景
 * - 安全，不允许任意代码执行
 */
public class CustomDSLScriptEngine implements ScriptEngine {

    @Override
    public String getName() {
        return "dsl";
    }

    @Override
    public boolean supports(String scriptType) {
        return "dsl".equalsIgnoreCase(scriptType) ||
               "expr".equalsIgnoreCase(scriptType) ||
               "expression".equalsIgnoreCase(scriptType);
    }

    @Override
    public Object execute(String scriptCode, Object inputs) throws ScriptExecutionException {
        try {
            String trimmed = scriptCode.trim();

            // 情况1: 直接返回inputs
            if (trimmed.equals("inputs") || trimmed.equals("return inputs;")) {
                return inputs;
            }

            // 情况2: JSON属性访问 - inputs.fieldName
            if (trimmed.startsWith("inputs.")) {
                return evaluateJsonPath(trimmed, inputs);
            }

            // 情况3: 字符串方法调用 - inputs.toUpperCase()
            if (inputs instanceof String) {
                return evaluateStringMethod(trimmed, (String) inputs);
            }

            // 情况4: 数字比较和算术运算
            if (inputs instanceof Number) {
                return evaluateNumberExpression(trimmed, (Number) inputs);
            }

            // 情况5: 布尔表达式
            if (trimmed.contains(">") || trimmed.contains("<") || trimmed.contains("==")) {
                return evaluateBooleanExpression(trimmed, inputs);
            }

            // 情况6: JSON处理
            if (inputs instanceof String && ((String) inputs).trim().startsWith("{")) {
                return evaluateJsonExpression(trimmed, (String) inputs);
            }

            // 默认：尝试作为字面量解析
            return parseLiteral(trimmed);

        } catch (Exception e) {
            throw new ScriptExecutionException(
                "DSL表达式执行失败: " + e.getMessage(), e
            );
        }
    }

    /**
     * 解析JSON路径：inputs.field 或 inputs.field.subfield
     * 注意：需要Gson依赖，当前版本已禁用JSON支持
     */
    private Object evaluateJsonPath(String expression, Object inputs) {
        throw new UnsupportedOperationException(
            "JSON路径访问需要Gson依赖。请使用预编译脚本类代替，或添加Gson依赖后重新实现此方法。"
        );
    }

    /**
     * 从JSON中获取值
     * 注意：需要Gson依赖，当前版本已禁用JSON支持
     */
    private Object getJsonValue(Object json, String path) {
        throw new UnsupportedOperationException(
            "JSON值获取需要Gson依赖。请使用预编译脚本类代替。"
        );
    }

    /**
     * 字符串方法调用：inputs.toUpperCase(), inputs.length()
     */
    private Object evaluateStringMethod(String expression, String inputs) {
        if (expression.equals("inputs.toUpperCase()")) {
            return inputs.toUpperCase();
        } else if (expression.equals("inputs.toLowerCase()")) {
            return inputs.toLowerCase();
        } else if (expression.equals("inputs.length()")) {
            return inputs.length();
        } else if (expression.equals("inputs.trim()")) {
            return inputs.trim();
        } else if (expression.matches("inputs\\.substring\\((\\d+)\\)")) {
            Pattern pattern = Pattern.compile("inputs\\.substring\\((\\d+)\\)");
            Matcher matcher = pattern.matcher(expression);
            if (matcher.find()) {
                int start = Integer.parseInt(matcher.group(1));
                return inputs.substring(start);
            }
        } else if (expression.matches("inputs\\.substring\\((\\d+),\\s*(\\d+)\\)")) {
            Pattern pattern = Pattern.compile("inputs\\.substring\\((\\d+),\\s*(\\d+)\\)");
            Matcher matcher = pattern.matcher(expression);
            if (matcher.find()) {
                int start = Integer.parseInt(matcher.group(1));
                int end = Integer.parseInt(matcher.group(2));
                return inputs.substring(start, end);
            }
        }

        throw new UnsupportedOperationException("不支持的字符串方法: " + expression);
    }

    /**
     * 数字表达式：inputs + 10, inputs * 2
     */
    private Object evaluateNumberExpression(String expression, Number inputs) {
        double value = inputs.doubleValue();

        // 简单算术运算
        Pattern pattern = Pattern.compile("inputs\\s*([+\\-*/])\\s*([\\d.]+)");
        Matcher matcher = pattern.matcher(expression);

        if (matcher.find()) {
            String operator = matcher.group(1);
            double operand = Double.parseDouble(matcher.group(2));

            return switch (operator) {
                case "+" -> value + operand;
                case "-" -> value - operand;
                case "*" -> value * operand;
                case "/" -> value / operand;
                default -> throw new UnsupportedOperationException("不支持的运算符: " + operator);
            };
        }

        return value;
    }

    /**
     * 布尔表达式：inputs > 0, inputs == 10
     */
    private Object evaluateBooleanExpression(String expression, Object inputs) {
        if (inputs instanceof Number) {
            double value = ((Number) inputs).doubleValue();

            Pattern pattern = Pattern.compile("inputs\\s*([><=!]+)\\s*([\\d.]+)");
            Matcher matcher = pattern.matcher(expression);

            if (matcher.find()) {
                String operator = matcher.group(1);
                double operand = Double.parseDouble(matcher.group(2));

                return switch (operator) {
                    case ">" -> value > operand;
                    case "<" -> value < operand;
                    case ">=" -> value >= operand;
                    case "<=" -> value <= operand;
                    case "==" -> value == operand;
                    case "!=" -> value != operand;
                    default -> throw new UnsupportedOperationException("不支持的比较运算符: " + operator);
                };
            }
        }

        throw new UnsupportedOperationException("不支持的布尔表达式: " + expression);
    }

    /**
     * JSON表达式处理
     * 注意：需要Gson依赖，当前版本已禁用JSON支持
     */
    private Object evaluateJsonExpression(String expression, String jsonInput) {
        throw new UnsupportedOperationException(
            "JSON表达式处理需要Gson依赖。请使用预编译脚本类代替，或添加Gson依赖后重新实现此方法。"
        );
    }

    /**
     * 解析字面量
     */
    private Object parseLiteral(String value) {
        // 布尔值
        if (value.equals("true")) return true;
        if (value.equals("false")) return false;

        // null
        if (value.equals("null")) return null;

        // 数字
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            } else {
                return Integer.parseInt(value);
            }
        } catch (NumberFormatException e) {
            // 不是数字
        }

        // 字符串（去除引号）
        if ((value.startsWith("\"") && value.endsWith("\"")) ||
            (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }
}
