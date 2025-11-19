package com.tanggo.fund.metadriven.lwc.domain.meta.orm;

/**
 * JPA关系类型枚举
 */
public enum RelationType {
    /**
     * 一对一关系
     */
    ONE_TO_ONE("OneToOne"),

    /**
     * 一对多关系
     */
    ONE_TO_MANY("OneToMany"),

    /**
     * 多对一关系
     */
    MANY_TO_ONE("ManyToOne"),

    /**
     * 多对多关系
     */
    MANY_TO_MANY("ManyToMany");

    private final String annotationName;

    RelationType(String annotationName) {
        this.annotationName = annotationName;
    }

    /**
     * 获取对应的注解名称
     *
     * @return 注解名称
     */
    public String getAnnotationName() {
        return annotationName;
    }

    /**
     * 从注解名称获取关系类型
     *
     * @param annotationName 注解名称
     * @return 关系类型，如果不匹配返回null
     */
    public static RelationType fromAnnotationName(String annotationName) {
        for (RelationType type : values()) {
            if (type.annotationName.equals(annotationName)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 检查是否为双向关系（需要mappedBy的关系）
     *
     * @return 如果是双向关系返回true，否则返回false
     */
    public boolean isBidirectional() {
        return this == ONE_TO_MANY || this == MANY_TO_MANY;
    }

    /**
     * 检查是否为拥有方（需要维护外键的一方）
     *
     * @return 如果是拥有方返回true，否则返回false
     */
    public boolean isOwner() {
        return this == MANY_TO_ONE || this == ONE_TO_ONE;
    }
}
