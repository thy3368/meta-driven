package com.tanggo.fund.metadriven.lwc.dobject.atom;

import lombok.Builder;
import lombok.Data;

import java.lang.reflect.Method;

/**
 * 执行上下文 - 包含执行所需的所有信息
 */
@Data
@Builder
public class LogicContext {

    /**
     * 执行类型: java, groovy, compiled, script
     */
    private String type;

    /**
     * Java 方法引用（用于 Java 方法执行）
     */
    private Method javaMethod;

    /**
     * 声明该方法的类
     */
    private Class<?> declaringClass;

    /**
     * 脚本代码内容
     */
    private String scriptCode;

    /**
     * 脚本文件路径
     */
    private String scriptFilePath;

    /**
     * 预编译脚本类名
     */
    private String scriptClassName;

    /**
     * 方法名称
     */
    private String methodName;

    /**
     * 输入类型
     */
    private Class<?> inputType;

    /**
     * 输出类型
     */
    private Class<?> outputType;

    /**
     * 动态对象输入类型
     */
    private DClass dynamicInputType;

    /**
     * 动态对象输出类型
     */
    private DClass dynamicOutputType;
}
