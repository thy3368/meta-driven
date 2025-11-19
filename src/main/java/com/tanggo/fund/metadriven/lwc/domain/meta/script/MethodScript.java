package com.tanggo.fund.metadriven.lwc.domain.meta.script;

/**
 * 方法脚本接口
 *
 * 用户编写的业务逻辑类需要实现此接口，然后独立编译成.class文件
 * 这样可以避免运行时编译，提高性能和类型安全
 *
 * 使用流程：
 * 1. 用户编写实现类（独立的Java文件）
 * 2. 编译成.class文件
 * 3. 在DMethod中引用类名
 * 4. 运行时动态加载并执行
 */
@FunctionalInterface
public interface MethodScript {

    /**
     * 执行脚本逻辑
     *
     * @param inputs 输入参数（类型由方法定义决定）
     * @return 执行结果
     * @throws Exception 执行过程中的任何异常
     */
    Object execute(Object inputs) throws Exception;
}
