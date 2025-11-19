package com.tanggo.fund.metadriven.lwc.domain.meta.script;

/**
 * 脚本引擎接口
 * 支持多种脚本语言的执行策略
 */
public interface ScriptEngine {

    /**
     * 获取引擎名称
     */
    String getName();

    /**
     * 检查是否支持指定的脚本语言
     * @param scriptType 脚本类型标识（如 "java", "groovy", "dsl"）
     * @return true 如果支持该脚本类型
     */
    boolean supports(String scriptType);

    /**
     * 执行脚本代码
     * @param scriptCode 脚本源代码
     * @param inputs 输入参数（变量名为 "inputs"）
     * @return 脚本执行结果
     * @throws ScriptExecutionException 脚本执行失败时抛出
     */
    Object execute(String scriptCode, Object inputs) throws ScriptExecutionException;

    /**
     * 预编译脚本（可选优化）
     * @param scriptCode 脚本源代码
     * @return 编译后的脚本对象
     */
    default Object compile(String scriptCode) throws ScriptExecutionException {
        return null; // 默认不支持预编译
    }

    /**
     * 执行预编译的脚本
     * @param compiledScript 预编译的脚本对象
     * @param inputs 输入参数
     * @return 执行结果
     */
    default Object executeCompiled(Object compiledScript, Object inputs) throws ScriptExecutionException {
        throw new UnsupportedOperationException("预编译执行未实现");
    }
}
