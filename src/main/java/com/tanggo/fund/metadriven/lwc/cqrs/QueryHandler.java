package com.tanggo.fund.metadriven.lwc.cqrs;


import com.tanggo.fund.metadriven.lwc.cqrs.outbound.trait.IEntityObjectRepo;
import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import com.tanggo.fund.metadriven.lwc.cqrs.types.CommandResult;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DynamicObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    private void proHandle() {
        //todo 权限检查，参数检查，状态检查等
    }


    private void afterHandle() {
        //todo
    }


    //执行真实业务命令
    private CommandResult doQuery(Command command) {
        DynamicObject entity = entityRepo.queryOne("entityName");
        //do something biz
        //生成entity_event 用于持久化
        CommandResult result = new CommandResult();
        return result;
    }


}
