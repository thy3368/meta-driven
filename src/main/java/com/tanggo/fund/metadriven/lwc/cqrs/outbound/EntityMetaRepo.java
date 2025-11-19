package com.tanggo.fund.metadriven.lwc.cqrs.outbound;

import com.tanggo.fund.metadriven.lwc.cqrs.outbound.trait.IEntityMetaRepo;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DClass;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EntityMetaRepo implements IEntityMetaRepo {


    @Override
    public void insert(DClass entity) {

    }

    @Override
    public void insertBatch(List<DClass> entities) {

    }

    @Override
    public List<DClass> query() {
        return List.of();
    }
}
