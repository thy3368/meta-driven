package com.tanggo.fund.metadriven.lwc.dobject.atom;

/**
 * 执行引擎接口 - 策略模式
 * 每种执行类型对应一个具体实现
 */
public interface LogicEngine {

    /**
     * 执行方法调用
     * @param inputs 输入参数
     * @param context 执行上下文（包含代码、方法信息等）
     * @return 执行结果
     */
    Object invoke(Object inputs, LogicContext context);

    /**
     * 获取引擎支持的类型
     * @return 类型标识（java, groovy, compiled, etc）
     */
    String getType();

    /**
     * 判断是否支持该上下文
     * @param context 执行上下文
     * @return true 如果支持
     */
    boolean supports(LogicContext context);
}
