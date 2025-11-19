package com.tanggo.fund.metadriven.lwc.dobject.atom;

import lombok.Data;

import java.util.Map;

@Data
public class DAnnotation {

    private Map<String, String> propertyMap;

    public String getValue(String table) {

        return propertyMap.get(table);

    }
}
