package com.tanggo.fund.metadriven.lwc.cqrs.outbound.trait;


import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;

public interface ICommandRepo {


    void insert(Command command);


    Command queryById(String id);
}
