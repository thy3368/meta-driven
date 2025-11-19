package com.tanggo.fund.metadriven.lwc.orm;


import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import com.tanggo.fund.metadriven.lwc.cqrs.types.CommandResult;
import com.tanggo.fund.metadriven.lwc.cqrs.types.EntityEvent;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DynamicObject;
import com.tanggo.fund.metadriven.lwc.cqrs.outbound.EntityRepo;

public class Test {


    private final EntityRepo entityRepo = new EntityRepo();

    private CommandResult handle(Command command) {


        // 执行 serviceMethodMeta 中方法
        DynamicObject entity = new DynamicObject();
        EntityEvent entityEvent = new EntityEvent();
        entityRepo.process(entityEvent, entity);

        CommandResult handleResult = new CommandResult();

        return handleResult;
    }

}
