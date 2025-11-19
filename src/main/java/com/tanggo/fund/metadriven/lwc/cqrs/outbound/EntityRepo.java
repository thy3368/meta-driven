package com.tanggo.fund.metadriven.lwc.cqrs.outbound;



import com.tanggo.fund.metadriven.lwc.cqrs.types.EntityEvent;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DAnnotation;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DClass;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DProperty;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DynamicObject;
import com.tanggo.fund.metadriven.lwc.domain.meta.registry.DClassRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体仓储 - 将EntityEvent转换为SQL并执行
 *
 * <p>遵循Clean Architecture原则：
 * - 基础设施层组件，实现领域接口
 * - 负责数据持久化和SQL生成
 * </p>
 */
public class EntityRepo {
    private EntityRepoCallback entityRepoCallback;

    public void process(EntityEvent entityEvent, DynamicObject newEntity) {
        if (entityRepoCallback != null) {
            entityRepoCallback.before2(entityEvent, newEntity);
        }

        process2(entityEvent);

        if (entityRepoCallback != null) {
            entityRepoCallback.after2(entityEvent, newEntity);
        }
    }

    /**
     * 处理实体事件 - 完整版本
     *
     * <p>处理流程：
     * <ol>
     *   <li>验证EntityEvent有效性</li>
     *   <li>根据实体名查找DClass元数据</li>
     *   <li>根据DClass标签解析数据库表定义</li>
     *   <li>根据entityEvent的新旧值和操作类型生成SQL</li>
     *   <li>执行SQL并处理结果</li>
     *   <li>记录性能指标（符合低延迟要求）</li>
     * </ol>
     * </p>
     *
     * <p>遵循Clean Architecture原则：
     * - 业务逻辑验证在领域层完成
     * - 基础设施层只负责持久化
     * - 异常处理清晰明确
     * </p>
     *
     * @param entityEvent 实体事件，不能为null
     * @throws IllegalArgumentException 如果参数无效
     * @throws EntityRepositoryException 如果处理失败
     */
    public void process2(EntityEvent entityEvent) {
        // 性能监控：开始时间（纳秒级精度）
        long startTimeNanos = System.nanoTime();

        try {
            // ============ 步骤1: 验证EntityEvent有效性 ============
            validateEntityEvent(entityEvent);

            // ============ 步骤2: 根据实体名查找DClass ============
            DClass dClass = findDClass(entityEvent.getEntityName());

            // ============ 步骤3-4: 生成SQL ============
            String sql = generate(entityEvent, dClass);

            // 验证生成的SQL
            if (sql == null || sql.trim().isEmpty()) {
                throw new IllegalStateException(
                    "生成的SQL为空，实体: " + entityEvent.getEntityName() +
                    ", 操作: " + entityEvent.getOperationType()
                );
            }

            // ============ 步骤5: 执行SQL ============
            ExecutionResult result = execute(sql);

            // ============ 步骤6: 性能监控 ============
            long durationNanos = System.nanoTime() - startTimeNanos;
            logPerformance(entityEvent, durationNanos, result);

        } catch (IllegalArgumentException e) {
            // 参数验证异常 - 直接抛出
            throw e;
        } catch (Exception e) {
            // 其他异常 - 包装为EntityRepositoryException
            throw new EntityRepositoryException(
                "处理实体事件失败: " + entityEvent.getEntityName() +
                ", 操作: " + entityEvent.getOperationType() +
                ", 原因: " + e.getMessage(),
                e
            );
        }
    }

    /**
     * 验证EntityEvent有效性
     */
    private void validateEntityEvent(EntityEvent entityEvent) {
        if (entityEvent == null) {
            throw new IllegalArgumentException("EntityEvent不能为null");
        }

        // 调用EntityEvent自身的验证方法
        entityEvent.validate();

        // 额外验证
        if (entityEvent.getEntityName() == null || entityEvent.getEntityName().trim().isEmpty()) {
            throw new IllegalArgumentException("实体名称不能为空");
        }

        if (entityEvent.getOperationType() == null) {
            throw new IllegalArgumentException("操作类型不能为null");
        }
    }

    /**
     * 查找DClass元数据
     */
    private DClass findDClass(String entityName) {
        DClass dClass = DClassRegistry.INSTANCE.findByName(entityName);

        if (dClass == null) {
            throw new IllegalArgumentException(
                "未找到实体类定义: " + entityName +
                "，请先将DClass注册到DClassRegistry中。" +
                "\n提示: 使用 DClassRegistry.INSTANCE.register(dClass) 进行注册"
            );
        }

        return dClass;
    }

    /**
     * 记录性能指标
     */
    private void logPerformance(EntityEvent entityEvent, long durationNanos, ExecutionResult result) {
        // 转换为微秒
        double durationMicros = durationNanos / 1000.0;

        // 性能日志（生产环境应使用专业日志框架）
        if (durationMicros > 1000) { // 超过1毫秒时警告
            System.err.printf(
                "[WARN] 实体事件处理耗时较长: %.2f μs (%.2f ms), 实体=%s, 操作=%s, 影响行数=%d%n",
                durationMicros,
                durationMicros / 1000.0,
                entityEvent.getEntityName(),
                entityEvent.getOperationType(),
                result.getAffectedRows()
            );
        } else {
            System.out.printf(
                "[INFO] 实体事件处理成功: %.2f μs, 实体=%s, 操作=%s, 影响行数=%d%n",
                durationMicros,
                entityEvent.getEntityName(),
                entityEvent.getOperationType(),
                result.getAffectedRows()
            );
        }
    }

    /**
     * SQL执行结果
     */
    public static class ExecutionResult {
        private final int affectedRows;
        private final boolean success;
        private final String message;

        public ExecutionResult(int affectedRows, boolean success, String message) {
            this.affectedRows = affectedRows;
            this.success = success;
            this.message = message;
        }

        public int getAffectedRows() {
            return affectedRows;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 实体仓储异常
     */
    public static class EntityRepositoryException extends RuntimeException {
        public EntityRepositoryException(String message) {
            super(message);
        }

        public EntityRepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 执行SQL语句
     *
     * <p>TODO: 实际实现需要连接数据库并执行SQL</p>
     *
     * <p>建议的实现方式：
     * <ul>
     *   <li>使用连接池（如HikariCP）获取数据库连接</li>
     *   <li>使用PreparedStatement防止SQL注入</li>
     *   <li>处理事务（根据需要）</li>
     *   <li>记录执行时间和影响行数</li>
     *   <li>异常处理和重试机制</li>
     * </ul>
     * </p>
     *
     * @param sql SQL语句
     * @return 执行结果
     */
    private ExecutionResult execute(String sql) {
        // 当前实现：仅打印SQL（用于测试和演示）
        System.out.println("生成的SQL: " + sql);

        // TODO: 实际实现示例
        // try (Connection conn = dataSource.getConnection();
        //      PreparedStatement stmt = conn.prepareStatement(sql)) {
        //     int affectedRows = stmt.executeUpdate();
        //     return new ExecutionResult(affectedRows, true, "执行成功");
        // } catch (SQLException e) {
        //     throw new EntityRepositoryException("SQL执行失败: " + e.getMessage(), e);
        // }

        // 模拟成功执行，影响1行
        return new ExecutionResult(1, true, "SQL执行成功（模拟）");
    }

    /**
     * 根据EntityEvent和DClass生成SQL语句
     */
    private String generate(EntityEvent entityEvent, DClass dClass) {
        // 获取表名
        String tableName = getTableName(dClass);

        // 根据操作类型生成不同的SQL
        switch (entityEvent.getOperationType()) {
            case CREATE:
                return generateInsertSql(entityEvent, dClass, tableName);
            case UPDATE:
                return generateUpdateSql(entityEvent, dClass, tableName);
            case DELETE:
                return generateDeleteSql(entityEvent, dClass, tableName);
            default:
                throw new IllegalArgumentException("不支持的操作类型: " + entityEvent.getOperationType());
        }
    }

    /**
     * 从DClass注解中获取表名
     */
    private String getTableName(DClass dClass) {
        if (dClass.getClassAnnList() == null || dClass.getClassAnnList().isEmpty()) {
            return dClass.getName(); // 如果没有@Table注解，使用类名作为表名
        }

        // 查找@Table注解
        for (DAnnotation annotation : dClass.getClassAnnList()) {
            if ("Table".equals(annotation.getValue("@type"))) {
                String tableName = annotation.getValue("name");
                if (tableName != null && !tableName.trim().isEmpty()) {
                    return tableName;
                }
            }
        }

        return dClass.getName(); // 默认使用类名
    }

    /**
     * 生成INSERT SQL语句
     */
    private String generateInsertSql(EntityEvent entityEvent, DClass dClass, String tableName) {
        List<String> columns = new ArrayList<>();
        List<String> values = new ArrayList<>();

        // 遍历所有字段变更，构建INSERT语句
        for (EntityEvent.FieldChange fieldChange : entityEvent.getFieldChanges()) {
            String columnName = getColumnName(dClass, fieldChange.getFieldName());
            columns.add(columnName);
            values.add(formatValue(fieldChange.getNewValue()));
        }

        return String.format("INSERT INTO %s (%s) VALUES (%s)",
            tableName,
            String.join(", ", columns),
            String.join(", ", values)
        );
    }

    /**
     * 生成UPDATE SQL语句
     */
    private String generateUpdateSql(EntityEvent entityEvent, DClass dClass, String tableName) {
        List<String> setClauses = new ArrayList<>();

        // 只更新实际变更的字段
        for (EntityEvent.FieldChange fieldChange : entityEvent.getFieldChanges()) {
            if (fieldChange.hasChanged() && !isIdField(dClass, fieldChange.getFieldName())) {
                String columnName = getColumnName(dClass, fieldChange.getFieldName());
                String value = formatValue(fieldChange.getNewValue());
                setClauses.add(columnName + " = " + value);
            }
        }

        // 获取主键条件
        String whereClause = "id = " + formatValue(entityEvent.getEntityId());

        return String.format("UPDATE %s SET %s WHERE %s",
            tableName,
            String.join(", ", setClauses),
            whereClause
        );
    }

    /**
     * 生成DELETE SQL语句
     */
    private String generateDeleteSql(EntityEvent entityEvent, DClass dClass, String tableName) {
        // 获取主键列名
        String idColumnName = getIdColumnName(dClass);

        return String.format("DELETE FROM %s WHERE %s = %s",
            tableName,
            idColumnName,
            formatValue(entityEvent.getEntityId())
        );
    }

    /**
     * 根据属性名获取对应的数据库列名
     */
    private String getColumnName(DClass dClass, String propertyName) {
        DProperty property = dClass.getProperty(propertyName);
        if (property == null || property.getPropertyAnnList() == null) {
            return propertyName; // 默认使用属性名
        }

        // 查找@Column注解
        for (DAnnotation annotation : property.getPropertyAnnList()) {
            if ("Column".equals(annotation.getValue("@type"))) {
                String columnName = annotation.getValue("name");
                if (columnName != null && !columnName.trim().isEmpty()) {
                    return columnName;
                }
            }
        }

        return propertyName; // 默认使用属性名
    }

    /**
     * 检查是否为ID字段
     */
    private boolean isIdField(DClass dClass, String propertyName) {
        DProperty property = dClass.getProperty(propertyName);
        if (property == null || property.getPropertyAnnList() == null) {
            return false;
        }

        // 查找@Id注解
        for (DAnnotation annotation : property.getPropertyAnnList()) {
            if ("Id".equals(annotation.getValue("@type"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取主键列名
     */
    private String getIdColumnName(DClass dClass) {
        if (dClass.getPropertyList() == null) {
            return "id";
        }

        for (DProperty property : dClass.getPropertyList()) {
            if (isIdField(dClass, property.getName())) {
                return getColumnName(dClass, property.getName());
            }
        }

        return "id"; // 默认
    }

    /**
     * 格式化值为SQL字符串
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }

        if (value instanceof String) {
            // 字符串需要加引号，并转义单引号
            return "'" + value.toString().replace("'", "''") + "'";
        }

        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }

        // 其他类型转为字符串并加引号
        return "'" + value.toString() + "'";
    }

    /**
     * 根据实体名字查询单个实体
     * TODO: 实现查询逻辑
     */
    public DynamicObject queryOne(String entityName) {
        // TODO: 实现查询逻辑
        return null;
    }
}
