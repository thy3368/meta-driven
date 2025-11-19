package com.tanggo.fund.metadriven.lwc.cqrs.outbound;


import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;

public class CommandRepo implements ICommandRepo {


    @Override
    public void insert(Command command) {

    }

    @Override
    public Command queryById(String id) {
        return null;
    }
}
