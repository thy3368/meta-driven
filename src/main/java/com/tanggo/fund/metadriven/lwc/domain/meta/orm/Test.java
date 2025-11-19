package com.tanggo.fund.metadriven.lwc.domain.meta.orm;


import com.tanggo.fund.metadriven.lwc.domain.Command;
import com.tanggo.fund.metadriven.lwc.domain.CommandResult;
import com.tanggo.fund.metadriven.lwc.domain.EntityEvent;
import com.tanggo.fund.metadriven.lwc.domain.meta.atom.DynamicObject;
import com.tanggo.fund.metadriven.lwc.outbound.EntityRepo;

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
