package com.tanggo.fund.metadriven.lwc.cqrs;


import com.tanggo.fund.metadriven.lwc.cqrs.outbound.trait.IEntityObjectRepo;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QueryHandler implements ICommandHandler {

    @Autowired
    private IEntityObjectRepo entityRepo;


    @Override
    public CommandResult handle(Command command) {

        //1. 预处理
        proHandle();
        //2. 业务操作后 生成entity_event
        CommandResult handleResult = doQuery(command);

        //5. 后置处理
        afterHandle();
        return handleResult;

    }

    @Override
    public void afterHandle(Command command, List<EntityEvent> entityEvents) {

    }

    @Override
    public void preHandle(Command command) {

    }

    @Override
    public List<EntityEvent> doHandle(Command command) {
        return List.of();
    }

    private void proHandle() {
        //todo 权限检查，参数检查，状态检查等
    }


    private void afterHandle() {
        //todo
    }


    //执行真实业务命令
    private CommandResult doQuery(Command command) {
        DObject entity = entityRepo.queryOne("entityName");
        //do something biz
        //生成entity_event 用于持久化
        return CommandResult.success(command, entity);
    }


}
