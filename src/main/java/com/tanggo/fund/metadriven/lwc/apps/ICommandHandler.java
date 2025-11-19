package com.tanggo.fund.metadriven.lwc.apps;


import com.tanggo.fund.metadriven.lwc.domain.Command;
import com.tanggo.fund.metadriven.lwc.domain.CommandResult;

public interface ICommandHandler {
    CommandResult handle(Command command);
}
