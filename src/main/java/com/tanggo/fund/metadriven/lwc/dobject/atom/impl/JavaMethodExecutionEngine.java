package com.tanggo.fund.metadriven.lwc.dobject.atom.impl;

import com.tanggo.fund.metadriven.lwc.dobject.atom.ExecutionContext;
import com.tanggo.fund.metadriven.lwc.dobject.atom.ExecutionEngine;

import java.lang.reflect.InvocationTargetException;

/**
 * Java 方法执行引擎 - 基于反射调用
 */
public class JavaMethodExecutionEngine implements ExecutionEngine {

    @Override
    public Object invoke(Object inputs, ExecutionContext context) {
        try {
            var javaMethod = context.getJavaMethod();
            if (javaMethod == null) {
                throw new IllegalStateException("Java方法引用为null");
            }

            boolean isStatic = java.lang.reflect.Modifier.isStatic(javaMethod.getModifiers());
            Object instance = isStatic ? null : createInstance(context.getDeclaringClass());

            if (!javaMethod.canAccess(instance)) {
                javaMethod.setAccessible(true);
            }

            return inputs == null
                ? javaMethod.invoke(instance)
                : javaMethod.invoke(instance, inputs);

        } catch (InvocationTargetException e) {
            throw new RuntimeException(
                "方法执行失败: " + e.getTargetException().getMessage(),
                e.getTargetException()
            );
        } catch (Exception e) {
            throw new RuntimeException("方法调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getType() {
        return "java";
    }

    @Override
    public boolean supports(ExecutionContext context) {
        return context.getJavaMethod() != null;
    }

    private Object createInstance(Class<?> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(
                "类 " + clazz.getName() + " 没有无参构造器", e
            );
        } catch (Exception e) {
            throw new RuntimeException(
                "创建实例失败: " + e.getMessage(), e
            );
        }
    }
}
