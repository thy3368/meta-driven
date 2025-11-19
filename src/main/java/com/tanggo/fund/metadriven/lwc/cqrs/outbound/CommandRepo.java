package com.tanggo.fund.metadriven.lwc.cqrs.outbound;


import com.tanggo.fund.metadriven.lwc.cqrs.outbound.trait.ICommandRepo;
import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import org.springframework.stereotype.Repository;

@Repository
public class CommandRepo implements ICommandRepo {


    @Override
    public void insert(Command command) {

    }

    @Override
    public Command queryById(String id) {
        return null;
    }
}
