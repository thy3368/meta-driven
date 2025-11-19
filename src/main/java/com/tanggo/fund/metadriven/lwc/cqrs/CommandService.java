package com.tanggo.fund.metadriven.lwc.cqrs;


import com.tanggo.fund.metadriven.lwc.cqrs.outbound.CommandRepo;
import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import com.tanggo.fund.metadriven.lwc.cqrs.types.CommandResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CommandService {

    @Autowired
    private CommandRepo commandRepo;

    @Autowired
    private CommandHandlerRepo commandHandlerRepo;


    public CommandResult handleCommand(Command command) {

//        commandRepo.insert(command);
        ICommandHandler handler = commandHandlerRepo.queryCommandHandler(command.getMethodName());
        return handler.handle(command);


    }


    public CommandResult handleQuery(Command command) {

//        commandRepo.insert(command);
        ICommandHandler handler = commandHandlerRepo.queryQueryHandler(command.getMethodName());
        return handler.handle(command);


    }
}
