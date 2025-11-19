package com.tanggo.fund.metadriven.lwc.cqrs;

import lombok.Data;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
@Data
public class CommandHandlerRepo {
    private Map<String, ICommandHandler> commandHandlerMap;


    public ICommandHandler queryQueryHandler(String methodName) {
        return commandHandlerMap.get(methodName);
    }

    public ICommandHandler queryCommandHandler(String methodName) {
        return commandHandlerMap.get(methodName);
    }
}
