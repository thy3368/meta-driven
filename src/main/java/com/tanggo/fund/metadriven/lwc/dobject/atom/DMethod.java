package com.tanggo.fund.metadriven.lwc.dobject.atom;

import lombok.Data;

import java.lang.reflect.Method;
import java.util.List;

@Data
public class DMethod {
    private List<DAnnotation> methodAnnList;
    private Class input;
    //仅为动态对象有效
    private DClass mInput;
    private Class output;
    //仅为动态对象有效
    private DClass mOutput;

    private String name;

    // 方式1: 原始Java方法引用（推荐，性能最好）
    private transient Method javaMethod; // Java反射Method对象
    private Class<?> declaringClass; // 声明该方法的类

    // 方式2: 预编译脚本类（推荐，性能好）
    private String scriptClassName; // 实现MethodScript接口的类名

    // 方式3: 脚本文件路径（推荐，灵活性好）
    private String scriptFilePath; // 例如: "scripts/abc.groovy", 会自动检测脚本类型
    private String scriptType; // java, groovy, dsl等

    // 执行引擎仓储 - 延迟初始化
    private transient LogicEngineRepo logicEngineRepo;

    public boolean inputIsDynamic() {
        // 修复：判断input是否是DynamicObject类型，而不是判断Class对象本身
        return input != null && input.equals(DObject.class);
    }

    public boolean outputIsDynamic() {
        return output != null && output.equals(DObject.class);
    }

    /**
     * 是否有Java方法实现
     */
    public boolean hasJavaMethod() {
        return javaMethod != null;
    }

    /**
     * 执行方法调用 - 使用 ExecutionEngine 策略模式
     * @param inputs 输入参数
     * @return 执行结果
     */
    public Object invoke(Object inputs) {
        // 1. 验证输入参数
        validateInput(inputs);

        // 2. 构建执行上下文
        LogicContext context = buildExecutionContext();

        // 3. 获取或初始化引擎仓储
        LogicEngineRepo repo = getOrCreateEngineRepo();

        // 4. 自动选择合适的执行引擎
        LogicEngine engine = repo.findEngine(context);

        // 5. 执行调用
        return engine.invoke(inputs, context);
    }

    /**
     * 验证输入参数类型
     */
    private void validateInput(Object inputs) {
        if (inputs instanceof DObject dynInput) {
            if (mInput != null) {
                String expectedType = mInput.getName();
                String actualType = dynInput.getDclass().getName();
                if (!expectedType.equals(actualType)) {
                    throw new IllegalArgumentException(
                        String.format("输入类型不匹配: 期望 %s，实际 %s", expectedType, actualType)
                    );
                }
            }
        } else if (input != null && inputs != null) {
            if (!input.isInstance(inputs)) {
                throw new IllegalArgumentException(
                    String.format("输入类型不匹配: 期望 %s，实际 %s",
                        input.getName(), inputs.getClass().getName())
                );
            }
        }
    }

    /**
     * 构建执行上下文
     */
    private LogicContext buildExecutionContext() {
        return LogicContext.builder()
            .type(scriptType)
            .javaMethod(javaMethod)
            .declaringClass(declaringClass)
            .scriptClassName(scriptClassName)
            .scriptFilePath(scriptFilePath)
            .methodName(name)
            .inputType(input)
            .outputType(output)
            .dynamicInputType(mInput)
            .dynamicOutputType(mOutput)
            .build();
    }

    /**
     * 获取或创建引擎仓储（延迟初始化）
     */
    private LogicEngineRepo getOrCreateEngineRepo() {
        if (logicEngineRepo == null) {
            logicEngineRepo = new LogicEngineRepo();
        }
        return logicEngineRepo;
    }
}
