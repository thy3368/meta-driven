package com.tanggo.fund.metadriven.lwc.orm;


import com.tanggo.fund.metadriven.lwc.dobject.atom.DAnnotation;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DProperty;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DObject;

/**
 * DProperty的ORM元数据提取器
 *
 * <p>职责：
 * <ul>
 *   <li>从DProperty的注解中提取ORM列映射信息</li>
 *   <li>提供列信息查询（列名、类型、约束等）</li>
 *   <li>提供属性类型判断（主键、关系、瞬态等）</li>
 *   <li>提供关系映射信息提取</li>
 * </ul>
 * </p>
 */
public class DPropertyOrmMetadata {

    private final DProperty property;

    /**
     * 构造函数
     *
     * @param property 属性定义
     */
    public DPropertyOrmMetadata(DProperty property) {
        if (property == null) {
            throw new IllegalArgumentException("DProperty cannot be null");
        }
        this.property = property;
    }

    // ==================== 属性类型判断 ====================

    /**
     * 是否为主键属性
     *
     * @return 如果是主键返回true，否则返回false
     */
    public boolean isId() {
        return hasAnnotation("Id");
    }

    /**
     * 是否为瞬态属性（不持久化）
     *
     * @return 如果是瞬态属性返回true，否则返回false
     */
    public boolean isTransient() {
        return hasAnnotation("Transient");
    }

    /**
     * 是否为关系属性
     *
     * @return 如果是关系属性返回true，否则返回false
     */
    public boolean isRelation() {
        return hasAnyAnnotation("OneToMany", "ManyToOne", "ManyToMany", "OneToOne");
    }

    /**
     * 是否为动态对象关系（关联到DynamicObject）
     *
     * @return 如果是动态对象关系返回true，否则返回false
     */
    public boolean isDynamicObjectRelation() {
        return property.getJavaType() == DObject.class;
    }

    /**
     * 是否为集合属性
     *
     * @return 如果是集合属性返回true，否则返回false
     */
    public boolean isCollection() {
        return property.getCollectionType() != null;
    }

    /**
     * 是否为版本字段（用于乐观锁）
     *
     * @return 如果是版本字段返回true，否则返回false
     */
    public boolean isVersion() {
        return hasAnnotation("Version");
    }

    /**
     * 是否为嵌入式对象
     *
     * @return 如果是嵌入式对象返回true，否则返回false
     */
    public boolean isEmbedded() {
        return hasAnnotation("Embedded");
    }

    // ==================== 列信息提取 ====================

    /**
     * 获取列名（从@Column注解提取，如果没有则使用属性名转换）
     *
     * @return 列名
     */
    public String getColumnName() {
        DAnnotation columnAnn = getAnnotation("Column");
        if (columnAnn != null) {
            String columnName = columnAnn.getValue("name");
            if (columnName != null && !columnName.isBlank()) {
                return columnName;
            }
        }
        // 默认使用属性名转下划线命名
        return OrmUtils.camelToSnakeCase(property.getName());
    }

    /**
     * 获取列长度
     *
     * @return 列长度，如果未定义返回255（JPA默认值）
     */
    public int getColumnLength() {
        DAnnotation columnAnn = getAnnotation("Column");
        return OrmUtils.getAnnotationIntValue(columnAnn, "length", 255);
    }

    /**
     * 获取列精度（用于数值类型）
     *
     * @return 列精度，如果未定义返回19（JPA默认值）
     */
    public int getColumnPrecision() {
        DAnnotation columnAnn = getAnnotation("Column");
        return OrmUtils.getAnnotationIntValue(columnAnn, "precision", 19);
    }

    /**
     * 获取列小数位数（用于数值类型）
     *
     * @return 列小数位数，如果未定义返回2（JPA默认值）
     */
    public int getColumnScale() {
        DAnnotation columnAnn = getAnnotation("Column");
        return OrmUtils.getAnnotationIntValue(columnAnn, "scale", 2);
    }

    /**
     * 是否可为空
     *
     * @return 如果可为空返回true，否则返回false
     */
    public boolean isNullable() {
        DAnnotation columnAnn = getAnnotation("Column");
        return OrmUtils.getAnnotationBooleanValue(columnAnn, "nullable", true);
    }

    /**
     * 是否唯一
     *
     * @return 如果唯一返回true，否则返回false
     */
    public boolean isUnique() {
        DAnnotation columnAnn = getAnnotation("Column");
        return OrmUtils.getAnnotationBooleanValue(columnAnn, "unique", false);
    }

    /**
     * 是否可更新
     *
     * @return 如果可更新返回true，否则返回false
     */
    public boolean isUpdatable() {
        DAnnotation columnAnn = getAnnotation("Column");
        return OrmUtils.getAnnotationBooleanValue(columnAnn, "updatable", true);
    }

    /**
     * 是否可插入
     *
     * @return 如果可插入返回true，否则返回false
     */
    public boolean isInsertable() {
        DAnnotation columnAnn = getAnnotation("Column");
        return OrmUtils.getAnnotationBooleanValue(columnAnn, "insertable", true);
    }

    // ==================== 主键生成策略 ====================

    /**
     * 获取主键生成策略
     *
     * @return 生成策略，如果未定义返回null
     */
    public String getGeneratedValueStrategy() {
        DAnnotation genValueAnn = getAnnotation("GeneratedValue");
        return genValueAnn != null ? genValueAnn.getValue("strategy") : null;
    }

    /**
     * 获取序列生成器名称
     *
     * @return 序列生成器名称，如果未定义返回null
     */
    public String getSequenceGenerator() {
        DAnnotation genValueAnn = getAnnotation("GeneratedValue");
        return genValueAnn != null ? genValueAnn.getValue("generator") : null;
    }

    // ==================== 关系映射信息 ====================

    /**
     * 获取关系类型
     *
     * @return 关系类型（OneToMany, ManyToOne, ManyToMany, OneToOne），如果不是关系返回null
     */
    public RelationType getRelationType() {
        if (hasAnnotation("OneToMany")) {
            return RelationType.ONE_TO_MANY;
        } else if (hasAnnotation("ManyToOne")) {
            return RelationType.MANY_TO_ONE;
        } else if (hasAnnotation("ManyToMany")) {
            return RelationType.MANY_TO_MANY;
        } else if (hasAnnotation("OneToOne")) {
            return RelationType.ONE_TO_ONE;
        }
        return null;
    }

    /**
     * 获取关系的mappedBy属性（双向关系中的反向属性名）
     *
     * @return mappedBy属性名，如果未定义返回null
     */
    public String getMappedBy() {
        for (String relationType : new String[]{"OneToMany", "ManyToOne", "ManyToMany", "OneToOne"}) {
            DAnnotation relationAnn = getAnnotation(relationType);
            if (relationAnn != null) {
                return relationAnn.getValue("mappedBy");
            }
        }
        return null;
    }

    /**
     * 获取级联策略
     *
     * @return 级联策略，如果未定义返回null
     */
    public String getCascade() {
        for (String relationType : new String[]{"OneToMany", "ManyToOne", "ManyToMany", "OneToOne"}) {
            DAnnotation relationAnn = getAnnotation(relationType);
            if (relationAnn != null) {
                return relationAnn.getValue("cascade");
            }
        }
        return null;
    }

    /**
     * 获取获取策略（LAZY或EAGER）
     *
     * @return 获取策略，如果未定义返回null
     */
    public String getFetchType() {
        for (String relationType : new String[]{"OneToMany", "ManyToOne", "ManyToMany", "OneToOne"}) {
            DAnnotation relationAnn = getAnnotation(relationType);
            if (relationAnn != null) {
                return relationAnn.getValue("fetch");
            }
        }
        return null;
    }

    /**
     * 是否孤儿删除（OneToMany特有）
     *
     * @return 如果启用孤儿删除返回true，否则返回false
     */
    public boolean isOrphanRemoval() {
        DAnnotation oneToMany = getAnnotation("OneToMany");
        return OrmUtils.getAnnotationBooleanValue(oneToMany, "orphanRemoval", false);
    }

    /**
     * 关系是否可选（ManyToOne和OneToOne特有）
     *
     * @return 如果可选返回true，否则返回false
     */
    public boolean isOptional() {
        DAnnotation manyToOne = getAnnotation("ManyToOne");
        if (manyToOne != null) {
            return OrmUtils.getAnnotationBooleanValue(manyToOne, "optional", true);
        }
        DAnnotation oneToOne = getAnnotation("OneToOne");
        return OrmUtils.getAnnotationBooleanValue(oneToOne, "optional", true);
    }

    // ==================== 外键信息 ====================

    /**
     * 获取外键列名（从@JoinColumn提取）
     *
     * @return 外键列名，如果未定义返回null
     */
    public String getJoinColumnName() {
        DAnnotation joinColumn = getAnnotation("JoinColumn");
        return joinColumn != null ? joinColumn.getValue("name") : null;
    }

    /**
     * 获取被引用的列名
     *
     * @return 被引用的列名，如果未定义返回null
     */
    public String getReferencedColumnName() {
        DAnnotation joinColumn = getAnnotation("JoinColumn");
        return joinColumn != null ? joinColumn.getValue("referencedColumnName") : null;
    }

    /**
     * 获取外键约束名称
     *
     * @return 外键约束名称，如果未定义返回null
     */
    public String getForeignKeyName() {
        DAnnotation joinColumn = getAnnotation("JoinColumn");
        return joinColumn != null ? joinColumn.getValue("foreignKey") : null;
    }

    // ==================== 中间表信息（ManyToMany） ====================

    /**
     * 获取中间表名称（从@JoinTable提取）
     *
     * @return 中间表名称，如果未定义返回null
     */
    public String getJoinTableName() {
        DAnnotation joinTable = getAnnotation("JoinTable");
        return joinTable != null ? joinTable.getValue("name") : null;
    }

    /**
     * 获取中间表的连接列定义
     *
     * @return 连接列定义，如果未定义返回null
     */
    public String getJoinColumns() {
        DAnnotation joinTable = getAnnotation("JoinTable");
        return joinTable != null ? joinTable.getValue("joinColumns") : null;
    }

    /**
     * 获取中间表的反向连接列定义
     *
     * @return 反向连接列定义，如果未定义返回null
     */
    public String getInverseJoinColumns() {
        DAnnotation joinTable = getAnnotation("JoinTable");
        return joinTable != null ? joinTable.getValue("inverseJoinColumns") : null;
    }

    // ==================== 枚举类型 ====================

    /**
     * 获取枚举映射策略
     *
     * @return 枚举映射策略（ORDINAL或STRING），如果未定义返回null
     */
    public String getEnumeratedType() {
        DAnnotation enumerated = getAnnotation("Enumerated");
        return enumerated != null ? enumerated.getValue("value") : null;
    }

    // ==================== 时间类型 ====================

    /**
     * 是否为创建时间字段
     *
     * @return 如果是创建时间返回true，否则返回false
     */
    public boolean isCreatedDate() {
        return hasAnnotation("CreatedDate");
    }

    /**
     * 是否为最后修改时间字段
     *
     * @return 如果是最后修改时间返回true，否则返回false
     */
    public boolean isLastModifiedDate() {
        return hasAnnotation("LastModifiedDate");
    }

    /**
     * 获取时间类型
     *
     * @return 时间类型（DATE, TIME, TIMESTAMP），如果未定义返回null
     */
    public String getTemporalType() {
        DAnnotation temporal = getAnnotation("Temporal");
        return temporal != null ? temporal.getValue("value") : null;
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取指定类型的注解
     *
     * @param annotationType 注解类型
     * @return 注解对象，如果不存在返回null
     */
    private DAnnotation getAnnotation(String annotationType) {
        return OrmUtils.findAnnotation(property.getPropertyAnnList(), annotationType);
    }

    /**
     * 检查是否有指定注解
     *
     * @param annotationType 注解类型
     * @return 如果存在返回true，否则返回false
     */
    private boolean hasAnnotation(String annotationType) {
        return OrmUtils.hasAnnotation(property.getPropertyAnnList(), annotationType);
    }

    /**
     * 检查是否有任一指定注解
     *
     * @param annotationTypes 注解类型数组
     * @return 如果存在任一类型返回true，否则返回false
     */
    private boolean hasAnyAnnotation(String... annotationTypes) {
        return OrmUtils.hasAnyAnnotation(property.getPropertyAnnList(), annotationTypes);
    }

    // ==================== 访问器 ====================

    /**
     * 获取原始DProperty对象
     *
     * @return DProperty对象
     */
    public DProperty getProperty() {
        return property;
    }

    @Override
    public String toString() {
        return "DPropertyOrmMetadata{" +
            "property=" + property.getName() +
            ", column=" + getColumnName() +
            ", isId=" + isId() +
            ", isRelation=" + isRelation() +
            ", relationType=" + getRelationType() +
            "}";
    }
}
