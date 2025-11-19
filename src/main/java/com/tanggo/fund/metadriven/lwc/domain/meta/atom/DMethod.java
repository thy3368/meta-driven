package com.tanggo.fund.metadriven.lwc.domain.meta.atom;

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

    public boolean inputIsDynamic() {
        // 修复：判断input是否是DynamicObject类型，而不是判断Class对象本身
        return input != null && input.equals(DynamicObject.class);
    }

    public boolean outputIsDynamic() {
        return output != null && output.equals(DynamicObject.class);
    }

    /**
     * 是否有Java方法实现
     */
    public boolean hasJavaMethod() {
        return javaMethod != null;
    }
}
