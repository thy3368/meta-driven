package com.tanggo.fund.metadriven.lwc.apps;


import com.tanggo.fund.metadriven.lwc.domain.Command;
import com.tanggo.fund.metadriven.lwc.domain.CommandResult;
import com.tanggo.fund.metadriven.lwc.outbound.CommandRepo;

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
