package com.tanggo.fund.metadriven.lwc.cqrs.outbound;


import com.tanggo.fund.metadriven.lwc.cqrs.types.EntityEvent;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EntityEventRepo {
    //没有修改，只有写操作，象entity变更流水
    public void insert(EntityEvent entityEvent) {
    }

    public void insertBatch(List<EntityEvent> entityEvents) {
        //todo写入流水
    }
}
