package com.tanggo.fund.metadriven.lwc.cqrs.outbound;


import com.tanggo.fund.metadriven.lwc.cqrs.types.EntityEvent;
import com.tanggo.fund.metadriven.lwc.dobject.atom.DynamicObject;

public class EntityRepoCallback {
    public void before() {


        //todo call
    }

    public void after() {
        //todo call
    }

    public void before2(EntityEvent entityEvent, DynamicObject newEntity) {
    }

    public void after2(EntityEvent entityEvent, DynamicObject newEntity) {
    }
}
