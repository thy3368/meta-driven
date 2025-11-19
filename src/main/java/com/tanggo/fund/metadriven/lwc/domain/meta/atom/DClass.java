package com.tanggo.fund.metadriven.lwc.domain.meta.atom;

import com.tanggo.fund.jldp.lwc.domain.meta.script.ScriptEngine;
import com.tanggo.fund.jldp.lwc.domain.meta.script.ScriptEngineRegistry;
import com.tanggo.fund.jldp.lwc.domain.meta.script.ScriptExecutionException;
import lombok.Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
//用来定义动态对象DynamicObject的类型
//定义一个DynamicObject的类型 用标签描述orm类似jpa
public class DClass {
    private String name;
    private List<DAnnotation> classAnnList;
    private List<DProperty> propertyList;
    private List<DMethod> methodList;

    // 性能优化：缓存索引 (transient避免序列化)
    private transient volatile Map<String, DMethod> methodCache;
    private transient volatile Map<String, DProperty> propertyCache;

    public Object callStaticMethod(String funcName, Object inputs) {

        return callStaticMethod2(null, funcName, inputs);

    }

    /**
     * 验证方法调用的参数
     * @param funcName 方法名
     * @param inputs 输入参数
     * @throws IllegalArgumentException 验证失败时抛出
     */
    private void verify(String funcName, Object inputs) {
        // 1. 验证方法名
        if (funcName == null || funcName.isBlank()) {
            throw new IllegalArgumentException("方法名不能为空");
        }

        // 2. 查找方法定义
        DMethod method = getMethod(funcName);
        if (method == null) {
            throw new IllegalArgumentException("方法不存在: " + funcName);
        }

        // 3. 验证输入类型
        if (inputs instanceof DynamicObject) {
            // 动态对象输入
            DynamicObject dynInput = (DynamicObject) inputs;
            if (method.getMInput() != null) {
                // 验证动态对象的类型匹配
                String expectedTypeName = method.getMInput().getName();
                String actualTypeName = dynInput.getDclass().getName();
                if (!expectedTypeName.equals(actualTypeName)) {
                    throw new IllegalArgumentException(
                        String.format("输入类型不匹配: 方法 %s 期望 %s，实际 %s",
                            funcName, expectedTypeName, actualTypeName)
                    );
                }
            }
        } else {
            // 普通Java对象输入
            if (method.getInput() != null && inputs != null) {
                if (!method.getInput().isInstance(inputs)) {
                    throw new IllegalArgumentException(
                        String.format("输入类型不匹配: 方法 %s 期望 %s，实际 %s",
                            funcName, method.getInput().getName(), inputs.getClass().getName())
                    );
                }
            }
        }
    }

    public Object callStaticMethod2(DynamicObject dynamicObject, String funcName, Object inputs) {
        // 1. 验证方法调用参数
        verify(funcName, inputs);

        // 2. 获取方法定义
        DMethod method = getMethod(funcName);
        if (method == null) {
            throw new IllegalArgumentException("方法不存在: " + funcName);
        }

        // 3. 优先使用原始Java方法（方式4 - 直接调用，性能最好）
        if (method.hasJavaMethod()) {
            return invokeJavaMethod(funcName, method, inputs);
        }

        // 4. 使用预编译脚本类（方式2 - 性能好）
        if (method.getScriptClassName() != null && !method.getScriptClassName().isBlank()) {
            return executeCompiledScript(funcName, method, inputs);
        }

        // 5. 使用脚本文件路径（方式3 - 灵活性好）
        if (method.getScriptFilePath() != null && !method.getScriptFilePath().isBlank()) {
            return executeScriptFromFile(funcName, method, inputs);
        }

        // 6. 没有定义任何实现
        throw new IllegalArgumentException(
            "方法 " + funcName + " 没有定义任何实现（javaMethod/scriptClassName/scriptFilePath）"
        );
    }

    /**
     * 调用原始Java方法（方式4 - 直接反射调用，性能最好）
     */
    private Object invokeJavaMethod(String funcName, DMethod method, Object inputs) {
        try {
            java.lang.reflect.Method javaMethod = method.getJavaMethod();
            Class<?> declaringClass = method.getDeclaringClass();

            if (javaMethod == null) {
                throw new IllegalStateException("方法 " + funcName + " 的Java方法引用为null");
            }

            // 判断是静态方法还是实例方法
            boolean isStatic = java.lang.reflect.Modifier.isStatic(javaMethod.getModifiers());

            Object instance = null;
            if (!isStatic) {
                // 实例方法：需要创建实例
                instance = createInstance(declaringClass);
            }

            // 确保方法可访问（处理私有/受保护方法）
            if (!javaMethod.canAccess(instance)) {
                javaMethod.setAccessible(true);
            }

            Object result;
            if (isStatic) {
                // 静态方法：直接调用，不需要实例
                if (inputs == null) {
                    result = javaMethod.invoke(null);
                } else {
                    result = javaMethod.invoke(null, inputs);
                }
            } else {
                // 实例方法：使用创建的实例调用
                if (inputs == null) {
                    result = javaMethod.invoke(instance);
                } else {
                    result = javaMethod.invoke(instance, inputs);
                }
            }

            return result;

        } catch (java.lang.reflect.InvocationTargetException e) {
            // 提取实际的异常
            Throwable targetException = e.getTargetException();
            throw new RuntimeException(
                "方法 " + funcName + " 执行失败: " + targetException.getMessage(),
                targetException
            );
        } catch (Exception e) {
            throw new RuntimeException(
                "方法 " + funcName + " 调用失败: " + e.getMessage(), e
            );
        }
    }

    /**
     * 创建类的实例（使用无参构造器）
     */
    private Object createInstance(Class<?> clazz) {
        try {
            // 尝试使用无参构造器
            return clazz.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(
                "类 " + clazz.getName() + " 没有无参构造器，无法创建实例", e
            );
        } catch (Exception e) {
            throw new RuntimeException(
                "创建类 " + clazz.getName() + " 的实例失败: " + e.getMessage(), e
            );
        }
    }

    /**
     * 执行预编译脚本类（推荐方式）
     */
    private Object executeCompiledScript(String funcName, DMethod method, Object inputs) {
        try {
            // 加载脚本类
            Class<?> scriptClass = Class.forName(method.getScriptClassName());

            // 检查是否实现MethodScript接口
            if (!com.tanggo.fund.jldp.lwc.domain.meta.script.MethodScript.class.isAssignableFrom(scriptClass)) {
                throw new IllegalArgumentException(
                    "脚本类 " + method.getScriptClassName() + " 必须实现 MethodScript 接口"
                );
            }

            // 创建实例
            com.tanggo.fund.jldp.lwc.domain.meta.script.MethodScript script =
                (com.tanggo.fund.jldp.lwc.domain.meta.script.MethodScript) scriptClass.getDeclaredConstructor().newInstance();

            // 执行
            return script.execute(inputs);

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                "找不到脚本类: " + method.getScriptClassName(), e
            );
        } catch (Exception e) {
            throw new RuntimeException(
                "方法 " + funcName + " 执行失败: " + e.getMessage(), e
            );
        }
    }

    /**
     * 执行脚本文件（方式3 - 从文件加载脚本）
     */
    private Object executeScriptFromFile(String funcName, DMethod method, Object inputs) {
        try {
            // 1. 读取脚本文件内容
            String scriptContent = readScriptFile(method.getScriptFilePath());

            // 2. 确定脚本类型
            String scriptType = method.getScriptType();
            if (scriptType == null || scriptType.isBlank()) {
                // 自动从文件扩展名检测脚本类型
                scriptType = detectScriptTypeFromPath(method.getScriptFilePath());
            }

            // 3. 获取脚本引擎
            ScriptEngineRegistry registry = ScriptEngineRegistry.getInstance();
            ScriptEngine engine;

            if (scriptType != null && !scriptType.isBlank()) {
                engine = registry.getEngine(scriptType);
                if (engine == null) {
                    throw new IllegalArgumentException(
                        "不支持的脚本类型: " + scriptType
                    );
                }
            } else {
                // 基于脚本内容自动检测
                engine = registry.detectEngine(scriptContent);
                if (engine == null) {
                    throw new IllegalStateException(
                        "无法检测脚本类型: " + method.getScriptFilePath()
                    );
                }
            }

            // 4. 执行脚本
            return engine.execute(scriptContent, inputs);

        } catch (IOException e) {
            throw new RuntimeException(
                "读取脚本文件失败: " + method.getScriptFilePath(), e
            );
        } catch (ScriptExecutionException e) {
            throw new RuntimeException(
                "方法 " + funcName + " 执行失败: " + e.getMessage(), e
            );
        }
    }

    /**
     * 读取脚本文件内容
     */
    private String readScriptFile(String filePath) throws IOException {
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new IOException("脚本文件不存在: " + filePath);
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("不是有效的文件: " + filePath);
        }
        return Files.readString(path);
    }

    /**
     * 从文件路径自动检测脚本类型
     * @param filePath 文件路径
     * @return 脚本类型（groovy, java, dsl等）或 null
     */
    private String detectScriptTypeFromPath(String filePath) {
        if (filePath == null) {
            return null;
        }

        String lowerPath = filePath.toLowerCase();

        if (lowerPath.endsWith(".groovy") || lowerPath.endsWith(".gvy")) {
            return "groovy";
        } else if (lowerPath.endsWith(".java")) {
            return "java";
        } else if (lowerPath.endsWith(".dsl")) {
            return "dsl";
        }

        return null;
    }


    public DynamicObject createObject() {
        DynamicObject object = new DynamicObject();
        object.setDclass(this);
        return object;
    }

    /**
     * 高性能方法查找 - 使用缓存索引
     * 时间复杂度：O(1) 而非 O(n)
     */
    public DMethod getMethod(String funcName) {
        if (funcName == null) {
            return null;
        }
        ensureMethodCache();
        return methodCache != null ? methodCache.get(funcName) : null;
    }

    /**
     * 高性能属性查找 - 使用缓存索引
     */
    public DProperty getProperty(String propertyName) {
        if (propertyName == null) {
            return null;
        }
        ensurePropertyCache();
        return propertyCache != null ? propertyCache.get(propertyName) : null;
    }

    /**
     * 延迟初始化方法缓存 - Double-Check Locking 保证线程安全
     */
    private void ensureMethodCache() {
        if (methodCache == null && methodList != null) {
            synchronized (this) {
                if (methodCache == null) {
                    Map<String, DMethod> cache = new HashMap<>(methodList.size());
                    for (DMethod method : methodList) {
                        if (method.getName() != null) {
                            cache.put(method.getName(), method);
                        }
                    }
                    methodCache = cache;
                }
            }
        }
    }

    /**
     * 延迟初始化属性缓存 - Double-Check Locking 保证线程安全
     */
    private void ensurePropertyCache() {
        if (propertyCache == null && propertyList != null) {
            synchronized (this) {
                if (propertyCache == null) {
                    Map<String, DProperty> cache = new HashMap<>(propertyList.size());
                    for (DProperty property : propertyList) {
                        if (property.getName() != null) {
                            cache.put(property.getName(), property);
                        }
                    }
                    propertyCache = cache;
                }
            }
        }
    }
}
