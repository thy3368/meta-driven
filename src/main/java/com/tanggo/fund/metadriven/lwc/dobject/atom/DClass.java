package com.tanggo.fund.metadriven.lwc.dobject.atom;

import lombok.Data;

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

    /**
     * 调用静态方法（兼容接口）
     * @param funcName 方法名
     * @param inputs 输入参数
     * @return 执行结果
     */
    public Object callStaticMethod(String funcName, Object inputs) {
        return callStaticMethod2(null, funcName, inputs);
    }

    /**
     * 调用方法（支持实例方法和静态方法）
     * 新架构：委托给DMethod的invoke方法，由ExecutionEngine执行
     * @param dObject 动态对象实例（静态方法时为null）
     * @param funcName 方法名
     * @param inputs 输入参数
     * @return 执行结果
     */
    public Object callStaticMethod2(DObject dObject, String funcName, Object inputs) {
        // 1. 验证方法名
        if (funcName == null || funcName.isBlank()) {
            throw new IllegalArgumentException("方法名不能为空");
        }

        // 2. 查找方法定义
        DMethod method = getMethod(funcName);
        if (method == null) {
            throw new IllegalArgumentException("方法不存在: " + funcName);
        }

        // 3. 委托给DMethod执行（使用ExecutionEngine架构）
        return method.invoke(inputs);
    }

    /**
     * 创建动态对象实例
     * @return 新的DynamicObject实例
     */
    public DObject createObject() {
        DObject object = new DObject();
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
