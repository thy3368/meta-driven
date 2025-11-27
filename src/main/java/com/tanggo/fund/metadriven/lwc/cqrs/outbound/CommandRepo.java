package com.tanggo.fund.metadriven.lwc.cqrs.outbound;


import com.tanggo.fund.metadriven.lwc.cqrs.ICommandHandler;
import com.tanggo.fund.metadriven.lwc.cqrs.outbound.trait.ICommandRepo;
import org.springframework.stereotype.Repository;

@Repository
public class CommandRepo implements ICommandRepo {


    @Override
    public void insert(ICommandHandler.Command command) {

    }

    @Override
    public ICommandHandler.Command queryById(String id) {
        return null;
    }
}
