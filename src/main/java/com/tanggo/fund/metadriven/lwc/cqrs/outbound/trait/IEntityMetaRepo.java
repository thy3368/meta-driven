package com.tanggo.fund.metadriven.lwc.cqrs.outbound.trait;

import com.tanggo.fund.metadriven.lwc.dobject.atom.DClass;

import java.util.List;

public interface IEntityMetaRepo extends IRepository {
    void insert(DClass entity);

    void insertBatch(List<DClass> entities);

    List<DClass> query();
}
