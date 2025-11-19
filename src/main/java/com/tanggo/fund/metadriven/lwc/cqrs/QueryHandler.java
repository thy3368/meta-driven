package com.tanggo.fund.metadriven.lwc.cqrs;


import com.tanggo.fund.metadriven.lwc.cqrs.outbound.EntityEventRepo;
import com.tanggo.fund.metadriven.lwc.cqrs.outbound.EntityRepo;
import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import com.tanggo.fund.metadriven.lwc.cqrs.types.CommandResult;
import com.tanggo.fund.metadriven.lwc.cqrs.types.EntityEvent;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DynamicObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class QueryHandler implements ICommandHandler {

    @Autowired
    private EntityRepo entityRepo;
    @Autowired
    private EntityEventRepo entityEventRepo;


    @Override
    public CommandResult handle(Command command) {

        //业务操作后 生成entityevent
        List<EntityEvent> entityEvents = doHandle(command);
        entityEventRepo.insertBatch(entityEvents);
        entityRepo.replay(entityEvents);

        CommandResult handleResult = new CommandResult();
        return handleResult;

    }

    //执行真实业务命令
    private List<EntityEvent> doHandle(Command command) {
        DynamicObject entity = entityRepo.queryOne4Update("entityName");
        //do something biz
        return Collections.singletonList(new EntityEvent());
    }


}
