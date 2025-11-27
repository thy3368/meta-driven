package com.tanggo.fund.metadriven.lwc.apps;

import com.tanggo.fund.metadriven.lwc.cqrs.ICommandHandler;
import com.tanggo.fund.metadriven.lwc.cqrs.CommandService;
import com.tanggo.fund.metadriven.lwc.cqrs.outbound.CommandRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CommandServiceTest {

    @Autowired
    private CommandService commandService;

    @Autowired
    private CommandRepo commandRepo;

    @Test
    void testCommonHandle() {
        ICommandHandler.Command command = commandRepo.queryById("ddd");
        ICommandHandler.CommandResult commandResult = commandService.handleCommand(command);
    }
}
