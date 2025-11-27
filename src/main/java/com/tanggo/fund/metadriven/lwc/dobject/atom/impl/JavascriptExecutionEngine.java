package com.tanggo.fund.metadriven.lwc.dobject.atom.impl;

import com.tanggo.fund.metadriven.lwc.dobject.atom.ExecutionContext;
import com.tanggo.fund.metadriven.lwc.dobject.atom.ExecutionEngine;

/**
 * javascript 流程执行引擎
 * 专注于 javascript 流程文件的动态加载和执行
 */
public class JavascriptExecutionEngine implements ExecutionEngine {


    @Override
    public Object invoke(Object inputs, ExecutionContext context) {
        return null;
    }

    @Override
    public String getType() {
        return "";
    }

    @Override
    public boolean supports(ExecutionContext context) {
        return false;
    }
}
