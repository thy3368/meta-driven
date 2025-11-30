package com.tanggo.fund.metadriven.lwc.cqrs;


import com.tanggo.fund.metadriven.lwc.cqrs.outbound.CommandHandlerRepo;
import com.tanggo.fund.metadriven.lwc.cqrs.outbound.CommandRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CommandService {

    @Autowired
    private CommandRepo commandRepo;

    @Autowired
    private CommandHandlerRepo commandHandlerRepo;


    public ICommandHandler.CommandResult handleCommand(ICommandHandler.Command command) {

//        commandRepo.insert(command);
        ICommandHandler handler = commandHandlerRepo.queryCommandHandler(command.methodName());
        return handler.handle(command);


    }


    public ICommandHandler.CommandResult handleQuery(ICommandHandler.Command command) {

//        commandRepo.insert(command);
        ICommandHandler handler = commandHandlerRepo.queryQueryHandler(command.methodName());
        return handler.handle(command);


    }
}
