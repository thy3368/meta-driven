package com.tanggo.fund.metadriven.lwc.cqrs;


import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import com.tanggo.fund.metadriven.lwc.cqrs.types.CommandResult;
import com.tanggo.fund.metadriven.lwc.cqrs.outbound.CommandRepo;

public class CommandService {
    private final CommandRepo commandRepo = new CommandRepo();

    public CommandResult handle(Command command) {

        commandRepo.insert(command);
        ICommandHandler handler = queryHandler(command);

        return handler.handle(command);


    }

    private ICommandHandler queryHandler(Command command) {
        return new CommandHandler();
    }


}
