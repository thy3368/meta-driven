package com.tanggo.fund.metadriven.lwc.cqrs;

import com.tanggo.fund.metadriven.lwc.dobject.atom.LogicEngine;
import com.tanggo.fund.metadriven.lwc.dobject.atom.LogicEngineRepo;
import com.tanggo.fund.metadriven.lwc.dobject.atom.LogicContext;
import com.tanggo.fund.metadriven.lwc.lob.commands.CancelOrderCommand;
import com.tanggo.fund.metadriven.lwc.lob.commands.CancelOrderResult;

import java.util.List;

/**
 * 撤单命令处理器
 */
public class DynCommandHandler implements ICommandHandler {

    private String type;

    private String code;

    private LogicEngineRepo logicEngineRepo;


    @Override
    public CommandResult handle(Command command) {
        Object param = command.getParam();
        if (!(param instanceof CancelOrderCommand cmd)) {
            throw new IllegalArgumentException("Command param must be CancelOrderCommand");
        }

        // 获取执行引擎
        LogicEngine logicEngine = logicEngineRepo.query(type);

        // 构建执行上下文
        LogicContext context =
            LogicContext.builder()
                .type(type)
                .scriptCode(code)
                .methodName("handle")
                .build();

        // 执行命令
        Object result = logicEngine.invoke(command, context);

        // 构造返回结果
        CommandResult cmdResult = (result instanceof CommandResult)
            ? (CommandResult) result
            : new CommandResult();

        CancelOrderResult data = new CancelOrderResult();
        data.setSuccess(true);
        data.setOrderId(cmd.getOrderId());
        cmdResult.setDate(data);

        return cmdResult;
    }

    @Override
    public void afterHandle(Command command, List<EntityEvent> entityEvents) {

    }

    @Override
    public void proHandle(Command command) {

    }

    @Override
    public List<EntityEvent> doHandle(Command command) {
        return List.of();
    }
}
