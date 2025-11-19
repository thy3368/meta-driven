package com.tanggo.fund.metadriven.lwc.cqrs.outbound;

import com.tanggo.fund.metadriven.lwc.cqrs.types.EntityEvent;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DynamicObject;

import java.util.List;

public interface IEntityRepo {
    void process(EntityEvent entityEvent);

    //根据事件回放数据
    void replay(List<EntityEvent> entityEvents);

    //加锁 1锁 2判 3更新
    DynamicObject queryOne4Update(String entityName);

    DynamicObject queryOne(String entityName);
}
