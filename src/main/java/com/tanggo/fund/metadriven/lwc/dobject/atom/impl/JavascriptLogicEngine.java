package com.tanggo.fund.metadriven.lwc.dobject.atom.impl;

import com.tanggo.fund.metadriven.lwc.dobject.atom.LogicContext;
import com.tanggo.fund.metadriven.lwc.dobject.atom.LogicEngine;

/**
 * javascript 流程执行引擎
 * 专注于 javascript 流程文件的动态加载和执行
 */
public class JavascriptLogicEngine implements LogicEngine {


    @Override
    public Object invoke(Object inputs, LogicContext context) {
        return null;
    }

    @Override
    public String getType() {
        return "";
    }

    @Override
    public boolean supports(LogicContext context) {
        return false;
    }
}
