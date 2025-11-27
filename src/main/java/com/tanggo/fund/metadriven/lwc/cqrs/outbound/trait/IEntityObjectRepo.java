package com.tanggo.fund.metadriven.lwc.cqrs.outbound.trait;

import com.tanggo.fund.metadriven.lwc.cqrs.ICommandHandler;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DObject;

import java.util.List;

public interface IEntityObjectRepo extends IRepository {
    void process(ICommandHandler.EntityEvent entityEvent);

    //根据事件回放数据
    void replay(List<ICommandHandler.EntityEvent> entityEvents);

    //加锁 1锁 2判 3更新
    DObject queryOne4Update(String entityName);

    DObject queryOne(String entityName);
}
