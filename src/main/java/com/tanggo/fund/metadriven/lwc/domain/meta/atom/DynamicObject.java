package com.tanggo.fund.metadriven.lwc.domain.meta.atom;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
//todo 只有动态对象才需要MEntity描述
public class DynamicObject {

    private Map<String, Object> data = new HashMap<>();


    private DClass dclass;

    public Object callMethod(String func2, Object inputs) {


        validateInput(func2, inputs);
        return dclass.callStaticMethod2(this, func2, inputs);

    }

    private void validateInput(String func2, Object inputs) {

        if (inputs instanceof DynamicObject) {

        } else {

        }

        //todo
    }

    public void setValue(String key, Object object) {
        validate2(key, object);
        //check class if have attribute
        data.put(key, object);
    }

    /**
     * 根据DClass定义验证属性值类型
     * @param key 属性名
     * @param object 属性值
     * @throws IllegalArgumentException 验证失败时抛出
     */
    private void validate2(String key, Object object) {
        if (dclass == null) {
            return; // 没有类型定义，不进行验证
        }

        DProperty property = dclass.getProperty(key);
        if (property == null) {
            // 可选：是否允许设置未定义的属性
            // throw new IllegalArgumentException("属性未定义: " + key);
            return; // 允许动态添加属性
        }

        // 类型验证
        if (object != null && property.getJavaType() != null) {
            Class<?> expectedType = property.getJavaType();

            // 处理DynamicObject类型
            if (expectedType == DynamicObject.class) {
                if (!(object instanceof DynamicObject)) {
                    throw new IllegalArgumentException(
                        String.format("属性 %s 期望 DynamicObject 类型，实际: %s",
                            key, object.getClass().getName())
                    );
                }
                // 验证DynamicObject的具体类型
                if (property.getDynamicObjectType() != null) {
                    DynamicObject dynValue = (DynamicObject) object;
                    String expectedTypeName = property.getDynamicObjectType().getName();
                    String actualTypeName = dynValue.getDclass() != null ?
                        dynValue.getDclass().getName() : "null";
                    if (!expectedTypeName.equals(actualTypeName)) {
                        throw new IllegalArgumentException(
                            String.format("属性 %s 期望 DynamicObject<%s>，实际: DynamicObject<%s>",
                                key, expectedTypeName, actualTypeName)
                        );
                    }
                }
            } else if (!expectedType.isInstance(object)) {
                throw new IllegalArgumentException(
                    String.format("属性 %s 类型不匹配: 期望 %s，实际 %s",
                        key, expectedType.getName(), object.getClass().getName())
                );
            }
        }
    }

    public Object getValue(String key) {
        return data.get(key);
    }
}

