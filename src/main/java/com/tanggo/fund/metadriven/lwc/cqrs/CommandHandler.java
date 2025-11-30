package com.tanggo.fund.metadriven.lwc.cqrs;


import com.tanggo.fund.metadriven.lwc.cqrs.outbound.EntityEventRepo;
import com.tanggo.fund.metadriven.lwc.cqrs.outbound.trait.IEntityObjectRepo;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class CommandHandler implements ICommandHandler {

    @Autowired
    private IEntityObjectRepo entityRepo;
    @Autowired
    private EntityEventRepo entityEventRepo;


    @Override
    public CommandResult handle(Command command) {
        try {
            //1. 预处理
            preHandle(command);
            //2. 业务操作后 生成entity_event
            List<EntityEvent> entityEvents = doHandle(command);
            //3. 写入流水
            entityEventRepo.insertBatch(entityEvents);
            //4. 写库
            entityRepo.replay(entityEvents);
            //5. 后置处理
            afterHandle(command, entityEvents);

            return CommandResult.success(command, entityEvents);
        } catch (Exception e) {
            return CommandResult.fromException(command, e);
        }
    }

    @Override
    public void afterHandle(Command command, List<EntityEvent> entityEvents) {
    }

    @Override
    public void preHandle(Command command) {
    }

//    private void proHandle() {
//        //todo 权限检查，参数检查，状态检查等
//    }
//
//
//    private void afterHandle(entityEvents) {
//        //todo
//    }


    //执行真实业务命令
    @Override
    public List<EntityEvent> doHandle(Command command) {
        DObject entity = entityRepo.queryOne4Update("entityName");
        //do something biz
        //生成entity_event 用于持久化
        return Collections.singletonList(new EntityEvent());
    }


}
