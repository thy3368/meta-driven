package com.tanggo.fund.metadriven.lwc.orm;



import com.tanggo.fund.metadriven.lwc.dobject.atom.DAnnotation;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DClass;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DProperty;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DClass的ORM元数据提取器
 *
 * <p>职责：
 * <ul>
 *   <li>从DClass的注解中提取ORM映射信息</li>
 *   <li>提供表信息查询（表名、schema、catalog）</li>
 *   <li>提供实体验证功能</li>
 *   <li>提供属性筛选功能（主键、关系、持久化属性等）</li>
 * </ul>
 * </p>
 *
 * <p>设计原则：
 * <ul>
 *   <li>不修改DClass对象，只读取和解析</li>
 *   <li>使用OrmUtils工具类进行注解操作</li>
 *   <li>遵循单一职责原则，专注于ORM元数据</li>
 * </ul>
 * </p>
 */
public class DClassOrmMetadata {

    private final DClass dClass;

    /**
     * 构造函数
     *
     * @param dClass 动态类定义
     */
    public DClassOrmMetadata(DClass dClass) {
        if (dClass == null) {
            throw new IllegalArgumentException("DClass cannot be null");
        }
        this.dClass = dClass;
    }

    // ==================== 实体验证 ====================

    /**
     * 检查是否是有效的实体（有@Entity注解）
     *
     * @return 如果是实体返回true，否则返回false
     */
    public boolean isEntity() {
        return getClassAnnotation("Entity") != null;
    }

    /**
     * 检查是否有主键定义
     *
     * @return 如果有主键返回true，否则返回false
     */
    public boolean hasIdProperty() {
        return getIdProperty() != null;
    }

    /**
     * 验证实体定义的完整性
     *
     * @throws IllegalStateException 如果实体定义不完整或不合法
     */
    public void validate() {
        // 1. 验证实体必须有@Entity注解
        if (!isEntity()) {
            throw new IllegalStateException(
                "DClass '" + dClass.getName() + "' must have @Entity annotation");
        }

        // 2. 验证实体必须有主键
        if (!hasIdProperty()) {
            throw new IllegalStateException(
                "Entity '" + dClass.getName() + "' must define an @Id property");
        }

        // 3. 验证实体名称不为空
        OrmUtils.requireNonBlank(dClass.getName(), "Entity name");

        // 4. 验证所有属性
        List<DProperty> properties = dClass.getPropertyList();
        if (properties != null) {
            for (DProperty property : properties) {
                validateProperty(property);
            }
        }
    }

    /**
     * 验证单个属性
     *
     * @param property 属性对象
     */
    private void validateProperty(DProperty property) {
        // 验证属性名不为空
        OrmUtils.requireNonBlank(property.getName(), "Property name");

        // 验证Java类型不为空
        OrmUtils.requireNonNull(property.getJavaType(), "Property '" + property.getName() + "' javaType");

        // 如果是关系属性，验证dynamicObjectType
        DPropertyOrmMetadata propMeta = OrmUtils.getOrmMetadata(property);
        if (propMeta.isRelation() && propMeta.isDynamicObjectRelation()) {
            if (property.getDynamicObjectType() == null) {
                throw new IllegalStateException(
                    "Relation property '" + property.getName() + "' must specify dynamicObjectType");
            }
        }
    }

    // ==================== 类注解查询 ====================

    /**
     * 获取指定类型的类级别注解
     *
     * @param annotationType 注解类型
     * @return 注解对象，如果不存在返回null
     */
    public DAnnotation getClassAnnotation(String annotationType) {
        return OrmUtils.findAnnotation(dClass.getClassAnnList(), annotationType);
    }

    /**
     * 检查是否有指定类型的类级别注解
     *
     * @param annotationType 注解类型
     * @return 如果存在返回true，否则返回false
     */
    public boolean hasClassAnnotation(String annotationType) {
        return getClassAnnotation(annotationType) != null;
    }

    // ==================== 表信息提取 ====================

    /**
     * 获取表名（从@Table注解中提取，如果没有则使用类名转换）
     *
     * @return 表名
     */
    public String getTableName() {
        DAnnotation tableAnn = getClassAnnotation("Table");
        if (tableAnn != null) {
            String tableName = tableAnn.getValue("name");
            if (tableName != null && !tableName.isBlank()) {
                return tableName;
            }
        }
        // 默认使用类名转下划线命名
        return OrmUtils.camelToSnakeCase(dClass.getName());
    }

    /**
     * 获取schema名
     *
     * @return schema名，如果没有定义返回null
     */
    public String getSchemaName() {
        DAnnotation tableAnn = getClassAnnotation("Table");
        return tableAnn != null ? tableAnn.getValue("schema") : null;
    }

    /**
     * 获取catalog名
     *
     * @return catalog名，如果没有定义返回null
     */
    public String getCatalogName() {
        DAnnotation tableAnn = getClassAnnotation("Table");
        return tableAnn != null ? tableAnn.getValue("catalog") : null;
    }

    /**
     * 获取完整的表限定名
     * 格式：[catalog].[schema].table
     *
     * @return 完整表名
     */
    public String getFullyQualifiedTableName() {
        StringBuilder sb = new StringBuilder();

        String catalog = getCatalogName();
        if (catalog != null && !catalog.isBlank()) {
            sb.append(catalog).append(".");
        }

        String schema = getSchemaName();
        if (schema != null && !schema.isBlank()) {
            sb.append(schema).append(".");
        }

        sb.append(getTableName());

        return sb.toString();
    }

    // ==================== 属性查询 ====================

    /**
     * 获取主键属性（带@Id注解的属性）
     *
     * @return 主键属性，如果不存在返回null
     */
    public DProperty getIdProperty() {
        List<DProperty> idProperties = getPropertiesWithAnnotation("Id");
        return idProperties.isEmpty() ? null : idProperties.get(0);
    }

    /**
     * 获取带有特定注解的所有属性
     *
     * @param annotationType 注解类型
     * @return 属性列表
     */
    public List<DProperty> getPropertiesWithAnnotation(String annotationType) {
        List<DProperty> properties = dClass.getPropertyList();
        if (properties == null) {
            return Collections.emptyList();
        }
        return properties.stream()
            .filter(prop -> OrmUtils.hasAnnotation(prop.getPropertyAnnList(), annotationType))
            .collect(Collectors.toList());
    }

    /**
     * 获取所有持久化属性（排除@Transient标记的属性）
     *
     * @return 持久化属性列表
     */
    public List<DProperty> getPersistentProperties() {
        List<DProperty> properties = dClass.getPropertyList();
        if (properties == null) {
            return Collections.emptyList();
        }
        return properties.stream()
            .filter(prop -> !OrmUtils.hasAnnotation(prop.getPropertyAnnList(), "Transient"))
            .collect(Collectors.toList());
    }

    /**
     * 获取所有关系属性（OneToMany, ManyToOne, ManyToMany, OneToOne）
     *
     * @return 关系属性列表
     */
    public List<DProperty> getRelationProperties() {
        List<DProperty> properties = dClass.getPropertyList();
        if (properties == null) {
            return Collections.emptyList();
        }
        return properties.stream()
            .filter(prop -> OrmUtils.hasAnyAnnotation(prop.getPropertyAnnList(),
                "OneToMany", "ManyToOne", "ManyToMany", "OneToOne"))
            .collect(Collectors.toList());
    }

    /**
     * 获取所有基本属性（排除关系属性和瞬态属性）
     *
     * @return 基本属性列表
     */
    public List<DProperty> getBasicProperties() {
        List<DProperty> properties = dClass.getPropertyList();
        if (properties == null) {
            return Collections.emptyList();
        }
        return properties.stream()
            .filter(prop -> {
                List<DAnnotation> annotations = prop.getPropertyAnnList();
                return !OrmUtils.hasAnnotation(annotations, "Transient") &&
                       !OrmUtils.hasAnyAnnotation(annotations,
                           "OneToMany", "ManyToOne", "ManyToMany", "OneToOne");
            })
            .collect(Collectors.toList());
    }

    /**
     * 获取所有列名（从持久化属性中提取）
     *
     * @return 列名列表
     */
    public List<String> getColumnNames() {
        return getPersistentProperties().stream()
            .map(this::getColumnName)
            .collect(Collectors.toList());
    }

    /**
     * 获取属性对应的列名
     *
     * @param property 属性对象
     * @return 列名
     */
    public String getColumnName(DProperty property) {
        DPropertyOrmMetadata propMeta = OrmUtils.getOrmMetadata(property);
        return propMeta.getColumnName();
    }

    // ==================== 索引信息 ====================

    /**
     * 获取索引定义字符串
     *
     * @return 索引定义，如果没有返回null
     */
    public String getIndexesDefinition() {
        DAnnotation tableAnn = getClassAnnotation("Table");
        return tableAnn != null ? tableAnn.getValue("indexes") : null;
    }

    /**
     * 检查是否定义了索引
     *
     * @return 如果定义了索引返回true，否则返回false
     */
    public boolean hasIndexes() {
        String indexes = getIndexesDefinition();
        return indexes != null && !indexes.isBlank();
    }

    // ==================== 继承信息 ====================

    /**
     * 获取继承策略
     *
     * @return 继承策略，如果没有定义返回null
     */
    public String getInheritanceStrategy() {
        DAnnotation inheritance = getClassAnnotation("Inheritance");
        return inheritance != null ? inheritance.getValue("strategy") : null;
    }

    /**
     * 获取鉴别器列名
     *
     * @return 鉴别器列名，如果没有定义返回null
     */
    public String getDiscriminatorColumn() {
        DAnnotation discriminator = getClassAnnotation("DiscriminatorColumn");
        return discriminator != null ? discriminator.getValue("name") : null;
    }

    /**
     * 获取鉴别器值
     *
     * @return 鉴别器值，如果没有定义返回null
     */
    public String getDiscriminatorValue() {
        DAnnotation discriminator = getClassAnnotation("DiscriminatorValue");
        return discriminator != null ? discriminator.getValue("value") : null;
    }

    // ==================== 访问器 ====================

    /**
     * 获取原始DClass对象
     *
     * @return DClass对象
     */
    public DClass getDClass() {
        return dClass;
    }

    @Override
    public String toString() {
        return "DClassOrmMetadata{" +
            "entity=" + dClass.getName() +
            ", table=" + getFullyQualifiedTableName() +
            ", isEntity=" + isEntity() +
            ", hasId=" + hasIdProperty() +
            "}";
    }
}
