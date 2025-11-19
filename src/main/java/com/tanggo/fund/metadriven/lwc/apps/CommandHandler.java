package com.tanggo.fund.metadriven.lwc.apps;


import com.tanggo.fund.metadriven.lwc.domain.Command;
import com.tanggo.fund.metadriven.lwc.domain.CommandResult;
import com.tanggo.fund.metadriven.lwc.domain.EntityEvent;
import com.tanggo.fund.metadriven.lwc.domain.meta.atom.DynamicObject;
import com.tanggo.fund.metadriven.lwc.outbound.EntityEventRepo;
import com.tanggo.fund.metadriven.lwc.outbound.EntityRepo;

public class CommandHandler extends DynamicObject implements ICommandHandler {
    private final EntityRepo entityRepo = new EntityRepo();
    private final EntityEventRepo entityEventRepo = null;

    private CommandResult handle2(Command command) {

        CommandResult result = new CommandResult();
        callMethod(command.getMethodName(), command.getInputs());
        return result;
    }

    @Override
    public CommandResult handle(Command command) {

        //业务操作后 生成entityevent
        EntityEvent entityEvent = doHandle(command);
        entityEventRepo.insert(entityEvent);
        entityRepo.process2(entityEvent);

        CommandResult handleResult = new CommandResult();
        return handleResult;

    }

    //执行真实业务命令
    private EntityEvent doHandle(Command command) {
        DynamicObject entity = entityRepo.queryOne("entityName");
        //do something biz
        return new EntityEvent();
    }


}
