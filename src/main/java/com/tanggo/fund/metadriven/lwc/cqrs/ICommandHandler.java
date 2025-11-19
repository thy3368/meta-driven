package com.tanggo.fund.metadriven.lwc.cqrs;


import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import com.tanggo.fund.metadriven.lwc.cqrs.types.CommandResult;

public interface ICommandHandler {
    CommandResult handle(Command command);
}
