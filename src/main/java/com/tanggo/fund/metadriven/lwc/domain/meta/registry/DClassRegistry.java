package com.tanggo.fund.metadriven.lwc.domain.meta.registry;


import com.tanggo.fund.metadriven.lwc.dobject.atom.DClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DClass注册表 - 管理所有实体类的元数据定义
 *
 * <p>职责：
 * <ul>
 *   <li>注册和存储DClass元数据</li>
 *   <li>根据实体名称快速查找DClass</li>
 *   <li>线程安全的访问控制</li>
 * </ul>
 * </p>
 *
 * <p>遵循Clean Architecture原则：
 * - 纯领域层组件，无外部依赖
 * - 使用并发安全的数据结构
 * - 符合低延迟性能要求（O(1)查找）
 * </p>
 */
public class DClassRegistry {

    /**
     * 单例实例 - 使用枚举实现线程安全的单例
     */
    public static final DClassRegistry INSTANCE = new DClassRegistry();

    /**
     * DClass存储 - 使用ConcurrentHashMap保证线程安全
     * Key: 实体名称（entityName）
     * Value: DClass元数据
     */
    private final Map<String, DClass> classRegistry = new ConcurrentHashMap<>();

    /**
     * 私有构造器 - 防止外部实例化
     */
    private DClassRegistry() {
    }

    /**
     * 注册DClass
     *
     * @param dClass DClass元数据
     * @throws IllegalArgumentException 如果dClass为null或name为空
     */
    public void register(DClass dClass) {
        if (dClass == null) {
            throw new IllegalArgumentException("DClass不能为null");
        }
        if (dClass.getName() == null || dClass.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("DClass名称不能为空");
        }

        classRegistry.put(dClass.getName(), dClass);
    }

    /**
     * 根据实体名称查找DClass
     *
     * @param entityName 实体名称
     * @return DClass元数据，如果不存在返回null
     */
    public DClass findByName(String entityName) {
        if (entityName == null || entityName.trim().isEmpty()) {
            return null;
        }
        return classRegistry.get(entityName);
    }

    /**
     * 检查实体是否已注册
     *
     * @param entityName 实体名称
     * @return true如果已注册，否则false
     */
    public boolean isRegistered(String entityName) {
        return findByName(entityName) != null;
    }

    /**
     * 移除已注册的DClass
     *
     * @param entityName 实体名称
     * @return 被移除的DClass，如果不存在返回null
     */
    public DClass unregister(String entityName) {
        if (entityName == null || entityName.trim().isEmpty()) {
            return null;
        }
        return classRegistry.remove(entityName);
    }

    /**
     * 清空所有注册的DClass
     */
    public void clear() {
        classRegistry.clear();
    }

    /**
     * 获取已注册的实体数量
     *
     * @return 实体数量
     */
    public int size() {
        return classRegistry.size();
    }

    /**
     * 批量注册DClass
     *
     * @param dClasses DClass列表
     */
    public void registerAll(Iterable<DClass> dClasses) {
        if (dClasses == null) {
            throw new IllegalArgumentException("DClass列表不能为null");
        }
        for (DClass dClass : dClasses) {
            register(dClass);
        }
    }
}
