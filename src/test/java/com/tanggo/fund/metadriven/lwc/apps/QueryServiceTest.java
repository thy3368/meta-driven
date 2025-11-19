package com.tanggo.fund.metadriven.lwc.apps;

import com.tanggo.fund.metadriven.lwc.cqrs.CommandService;
import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import com.tanggo.fund.metadriven.lwc.cqrs.types.CommandResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class QueryServiceTest {

    @Autowired
    private CommandService commandService;


    @Test
    void testCommonHandle() {

        Command command = new Command();


        CommandResult commandResult = commandService.handleCommand(command);
        CommandResult queryResult = commandService.handleQuery(command);

    }
}
