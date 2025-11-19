package com.tanggo.fund.metadriven.lwc.domain.meta.atom;

import lombok.Data;

import java.util.List;

@Data
public class DProperty {
    private List<DAnnotation> propertyAnnList;
    private String name;
    private String defaultValue;
    private Class javaType;

    //仅在JavaType 为DynamicObject才需求
    private DClass dynamicObjectType;


    private Class collectionType;

}
