package com.tanggo.fund.metadriven.lwc.orm;



import com.tanggo.fund.metadriven.lwc.dobject.atom.DAnnotation;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DClass;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DProperty;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ORM工具类 - 提供便捷的ORM操作方法
 *
 * <p>职责：
 * <ul>
 *   <li>注解查询和检查</li>
 *   <li>创建ORM元数据对象</li>
 *   <li>通用的ORM辅助方法</li>
 * </ul>
 * </p>
 */
public final class OrmUtils {

    private OrmUtils() {
        // 工具类，禁止实例化
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ==================== 元数据创建 ====================

    /**
     * 获取DClass的ORM元数据
     *
     * @param dClass 动态类定义
     * @return ORM元数据对象
     */
    public static DClassOrmMetadata getOrmMetadata(DClass dClass) {
        if (dClass == null) {
            throw new IllegalArgumentException("DClass cannot be null");
        }
        return new DClassOrmMetadata(dClass);
    }

    /**
     * 获取DProperty的ORM元数据
     *
     * @param property 属性定义
     * @return 属性ORM元数据对象
     */
    public static DPropertyOrmMetadata getOrmMetadata(DProperty property) {
        if (property == null) {
            throw new IllegalArgumentException("DProperty cannot be null");
        }
        return new DPropertyOrmMetadata(property);
    }

    // ==================== 注解查询 ====================

    /**
     * 从注解列表中查找指定类型的注解
     *
     * @param annotations 注解列表
     * @param annotationType 注解类型，如 "Entity", "Table", "Column"
     * @return 匹配的注解，如果不存在返回null
     */
    public static DAnnotation findAnnotation(List<DAnnotation> annotations, String annotationType) {
        if (annotations == null || annotationType == null) {
            return null;
        }
        return annotations.stream()
            .filter(ann -> annotationType.equals(ann.getValue("@type")))
            .findFirst()
            .orElse(null);
    }

    /**
     * 从注解列表中查找所有指定类型的注解（支持重复注解）
     *
     * @param annotations 注解列表
     * @param annotationType 注解类型
     * @return 匹配的注解列表
     */
    public static List<DAnnotation> findAllAnnotations(List<DAnnotation> annotations, String annotationType) {
        if (annotations == null || annotationType == null) {
            return Collections.emptyList();
        }
        return annotations.stream()
            .filter(ann -> annotationType.equals(ann.getValue("@type")))
            .collect(Collectors.toList());
    }

    /**
     * 检查注解列表中是否有指定类型的注解
     *
     * @param annotations 注解列表
     * @param annotationType 注解类型
     * @return 如果存在返回true，否则返回false
     */
    public static boolean hasAnnotation(List<DAnnotation> annotations, String annotationType) {
        return findAnnotation(annotations, annotationType) != null;
    }

    /**
     * 检查注解列表中是否有任一指定类型的注解
     *
     * @param annotations 注解列表
     * @param annotationTypes 注解类型数组
     * @return 如果存在任一类型返回true，否则返回false
     */
    public static boolean hasAnyAnnotation(List<DAnnotation> annotations, String... annotationTypes) {
        if (annotations == null || annotationTypes == null) {
            return false;
        }
        for (String type : annotationTypes) {
            if (hasAnnotation(annotations, type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查注解列表中是否同时有所有指定类型的注解
     *
     * @param annotations 注解列表
     * @param annotationTypes 注解类型数组
     * @return 如果同时存在所有类型返回true，否则返回false
     */
    public static boolean hasAllAnnotations(List<DAnnotation> annotations, String... annotationTypes) {
        if (annotations == null || annotationTypes == null) {
            return false;
        }
        for (String type : annotationTypes) {
            if (!hasAnnotation(annotations, type)) {
                return false;
            }
        }
        return true;
    }

    // ==================== 注解值提取 ====================

    /**
     * 从注解中获取指定属性的值
     *
     * @param annotation 注解对象
     * @param propertyName 属性名
     * @param defaultValue 默认值
     * @return 属性值，如果不存在返回默认值
     */
    public static String getAnnotationValue(DAnnotation annotation, String propertyName, String defaultValue) {
        if (annotation == null) {
            return defaultValue;
        }
        String value = annotation.getValue(propertyName);
        return value != null ? value : defaultValue;
    }

    /**
     * 从注解中获取boolean属性值
     *
     * @param annotation 注解对象
     * @param propertyName 属性名
     * @param defaultValue 默认值
     * @return boolean值
     */
    public static boolean getAnnotationBooleanValue(DAnnotation annotation, String propertyName, boolean defaultValue) {
        String value = getAnnotationValue(annotation, propertyName, null);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * 从注解中获取int属性值
     *
     * @param annotation 注解对象
     * @param propertyName 属性名
     * @param defaultValue 默认值
     * @return int值
     */
    public static int getAnnotationIntValue(DAnnotation annotation, String propertyName, int defaultValue) {
        String value = getAnnotationValue(annotation, propertyName, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ==================== 命名转换 ====================

    /**
     * 驼峰命名转下划线命名
     * 例如: userName -> user_name
     *
     * @param camelCase 驼峰命名字符串
     * @return 下划线命名字符串
     */
    public static String camelToSnakeCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        StringBuilder result = new StringBuilder();
        result.append(Character.toLowerCase(camelCase.charAt(0)));

        for (int i = 1; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append('_');
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * 下划线命名转驼峰命名
     * 例如: user_name -> userName
     *
     * @param snakeCase 下划线命名字符串
     * @return 驼峰命名字符串
     */
    public static String snakeToCamelCase(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) {
            return snakeCase;
        }
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;

        for (int i = 0; i < snakeCase.length(); i++) {
            char c = snakeCase.charAt(i);
            if (c == '_') {
                capitalizeNext = true;
            } else {
                if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            }
        }
        return result.toString();
    }

    // ==================== 验证 ====================

    /**
     * 验证字符串非空
     *
     * @param value 字符串值
     * @param fieldName 字段名（用于错误消息）
     * @throws IllegalArgumentException 如果字符串为空
     */
    public static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
    }

    /**
     * 验证对象非空
     *
     * @param value 对象值
     * @param fieldName 字段名（用于错误消息）
     * @throws IllegalArgumentException 如果对象为空
     */
    public static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
    }
}
