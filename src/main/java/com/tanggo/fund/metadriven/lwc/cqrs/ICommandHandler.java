package com.tanggo.fund.metadriven.lwc.cqrs;


import lombok.Data;

import java.util.List;

public interface ICommandHandler {
    CommandResult handle(Command command);

    void afterHandle(Command command, List<EntityEvent> entityEvents);

    void proHandle(Command command);

    //执行真实业务命令
    List<EntityEvent> doHandle(Command command);

    @Data
    class Command {

        private String methodName;

        private Object param;
    }

    @Data
    class CommandResult {
        private Object date;
    }

    /**
     * 实体事件 - 用于追踪实体的变更记录
     * 支持增(CREATE)、删(DELETE)、改(UPDATE)操作，记录原值和修改后的值
     *
     * 遵循Clean Architecture原则：
     * - 纯领域对象，无外部依赖
     * - 不可变性保证线程安全
     * - 业务规则封装在领域层
     */
    @Data
    class EntityEvent {

        /**
         * 操作类型枚举
         */
        public enum OperationType {
            /** 创建操作 */
            CREATE("CREATE"),
            /** 更新操作 */
            UPDATE("UPDATE"),
            /** 删除操作 */
            DELETE("DELETE");

            private final String code;

            OperationType(String code) {
                this.code = code;
            }

            public String getCode() {
                return code;
            }
        }

        /**
         * 字段变更记录 - 值对象(Value Object)
         * 记录单个字段的原值和新值
         */
        @Data
        public static class FieldChange {
            /** 字段名称 */
            private final String fieldName;
            /** 原始值 */
            private final Object originalValue;
            /** 修改后的值 */
            private final Object newValue;
            /** 字段类型 */
            private final String fieldType;

            public FieldChange(String fieldName, Object originalValue, Object newValue) {
                this(fieldName, originalValue, newValue, null);
            }

            public FieldChange(String fieldName, Object originalValue, Object newValue, String fieldType) {
                this.fieldName = fieldName;
                this.originalValue = originalValue;
                this.newValue = newValue;
                this.fieldType = fieldType;
            }

            /**
             * 判断值是否真正发生变更
             */
            public boolean hasChanged() {
                if (originalValue == null && newValue == null) {
                    return false;
                }
                if (originalValue == null || newValue == null) {
                    return true;
                }
                return !originalValue.equals(newValue);
            }

            /**
             * 获取变更描述
             */
            public String getChangeDescription() {
                return String.format("[%s]: %s -> %s",
                        fieldName,
                        formatValue(originalValue),
                        formatValue(newValue));
            }

            private String formatValue(Object value) {
                if (value == null) {
                    return "null";
                }
                if (value instanceof String) {
                    return "\"" + value + "\"";
                }
                return value.toString();
            }
        }

        /** 实体名称 */
        private String entityName;

        /** 事件名称 */
        private String eventName;

        /** 操作类型 */
        private OperationType operationType;

        /** 实体ID */
        private String entityId;

        /** 字段变更列表 */
        private List<FieldChange> fieldChanges;

        /** 事件发生时间戳(纳秒) - 符合低延迟性能要求 */
        private long timestampNanos;

        /** 操作用户 */
        private String operator;

        /** 备注信息 */
        private String remarks;

        /**
         * 构造器 - CREATE操作
         */
        public static EntityEvent createEvent(String entityName, String entityId,
                                              List<FieldChange> fieldChanges) {
            EntityEvent event = new EntityEvent();
            event.entityName = entityName;
            event.eventName = "ENTITY_CREATED";
            event.operationType = OperationType.CREATE;
            event.entityId = entityId;
            event.fieldChanges = fieldChanges != null ? fieldChanges : new java.util.ArrayList<>();
            event.timestampNanos = System.nanoTime();
            return event;
        }

        /**
         * 构造器 - UPDATE操作
         */
        public static EntityEvent updateEvent(String entityName, String entityId,
                                              List<FieldChange> fieldChanges) {
            EntityEvent event = new EntityEvent();
            event.entityName = entityName;
            event.eventName = "ENTITY_UPDATED";
            event.operationType = OperationType.UPDATE;
            event.entityId = entityId;
            event.fieldChanges = fieldChanges != null ? fieldChanges : new java.util.ArrayList<>();
            event.timestampNanos = System.nanoTime();
            return event;
        }

        /**
         * 构造器 - DELETE操作
         */
        public static EntityEvent deleteEvent(String entityName, String entityId,
                                              List<FieldChange> fieldChanges) {
            EntityEvent event = new EntityEvent();
            event.entityName = entityName;
            event.eventName = "ENTITY_DELETED";
            event.operationType = OperationType.DELETE;
            event.entityId = entityId;
            event.fieldChanges = fieldChanges != null ? fieldChanges : new java.util.ArrayList<>();
            event.timestampNanos = System.nanoTime();
            return event;
        }

        /**
         * 业务规则验证
         */
        public void validate() {
            if (entityName == null || entityName.trim().isEmpty()) {
                throw new IllegalArgumentException("实体名称不能为空");
            }
            if (operationType == null) {
                throw new IllegalArgumentException("操作类型不能为空");
            }
            if (entityId == null || entityId.trim().isEmpty()) {
                throw new IllegalArgumentException("实体ID不能为空");
            }

            // UPDATE操作必须有字段变更
            if (operationType == OperationType.UPDATE) {
                if (fieldChanges == null || fieldChanges.isEmpty()) {
                    throw new IllegalArgumentException("UPDATE操作必须包含至少一个字段变更");
                }
                // 验证至少有一个字段真正发生了变更
                boolean hasRealChange = fieldChanges.stream().anyMatch(FieldChange::hasChanged);
                if (!hasRealChange) {
                    throw new IllegalArgumentException("UPDATE操作必须包含至少一个实际变更的字段");
                }
            }
        }

        /**
         * 获取实际发生变更的字段数量
         */
        public long getActualChangedFieldsCount() {
            if (fieldChanges == null) {
                return 0;
            }
            return fieldChanges.stream()
                    .filter(FieldChange::hasChanged)
                    .count();
        }

        /**
         * 获取变更摘要
         */
        public String getChangeSummary() {
            StringBuilder summary = new StringBuilder();
            summary.append(String.format("[%s] 实体: %s, ID: %s",
                    operationType.getCode(), entityName, entityId));

            if (operator != null) {
                summary.append(", 操作人: ").append(operator);
            }

            long changedCount = getActualChangedFieldsCount();
            if (changedCount > 0) {
                summary.append(String.format(", 变更字段数: %d", changedCount));
            }

            return summary.toString();
        }

        /**
         * 获取详细的变更描述
         */
        public String getDetailedChangeDescription() {
            StringBuilder description = new StringBuilder(getChangeSummary());
            description.append("\n变更明细:\n");

            if (fieldChanges != null) {
                fieldChanges.stream()
                        .filter(FieldChange::hasChanged)
                        .forEach(change -> description.append("  ")
                                .append(change.getChangeDescription())
                                .append("\n"));
            }

            if (remarks != null && !remarks.trim().isEmpty()) {
                description.append("备注: ").append(remarks);
            }

            return description.toString();
        }

        /**
         * 添加字段变更
         */
        public void addFieldChange(String fieldName, Object originalValue, Object newValue) {
            if (fieldChanges == null) {
                fieldChanges = new java.util.ArrayList<>();
            }
            fieldChanges.add(new FieldChange(fieldName, originalValue, newValue));
        }

        /**
         * 添加字段变更（带类型）
         */
        public void addFieldChange(String fieldName, Object originalValue, Object newValue, String fieldType) {
            if (fieldChanges == null) {
                fieldChanges = new java.util.ArrayList<>();
            }
            fieldChanges.add(new FieldChange(fieldName, originalValue, newValue, fieldType));
        }
    }
}
