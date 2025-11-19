package com.tanggo.fund.metadriven.lwc.apps;

import com.tanggo.fund.metadriven.lwc.cqrs.CommandService;
import com.tanggo.fund.metadriven.lwc.cqrs.outbound.CommandRepo;
import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import com.tanggo.fund.metadriven.lwc.cqrs.types.CommandResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LobServiceTest {

    @Autowired
    private CommandService commandService;

    @Autowired
    private CommandRepo commandRepo;

    @Test
    void testCommonHandle() {
        Command command = commandRepo.queryById("ddd");
        CommandResult commandResult = commandService.handleCommand(command);
    }
}
