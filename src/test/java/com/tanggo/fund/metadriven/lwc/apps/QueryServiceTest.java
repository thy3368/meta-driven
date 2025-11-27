package com.tanggo.fund.metadriven.lwc.apps;

import com.tanggo.fund.metadriven.lwc.cqrs.CommandService;
import com.tanggo.fund.metadriven.lwc.cqrs.ICommandHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class QueryServiceTest {

    @Autowired
    private CommandService commandService;


    @Test
    void testCommonHandle() {

        ICommandHandler.Command command = new ICommandHandler.Command();


        ICommandHandler.CommandResult commandResult = commandService.handleCommand(command);
        ICommandHandler.CommandResult queryResult = commandService.handleQuery(command);

    }
}
