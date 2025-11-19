package com.tanggo.fund.metadriven.lwc.cqrs.types;

import lombok.Data;

@Data
public class Command {

    private String methodName;

    private Object inputs;
}
