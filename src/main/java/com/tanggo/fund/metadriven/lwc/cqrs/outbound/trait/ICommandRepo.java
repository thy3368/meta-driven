package com.tanggo.fund.metadriven.lwc.cqrs.outbound.trait;


import com.tanggo.fund.metadriven.lwc.cqrs.ICommandHandler;

public interface ICommandRepo extends IRepository {


    void insert(ICommandHandler.Command command);


    ICommandHandler.Command queryById(String id);
}
