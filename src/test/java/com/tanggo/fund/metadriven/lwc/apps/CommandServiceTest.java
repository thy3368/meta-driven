package com.tanggo.fund.metadriven.lwc.apps;

import com.tanggo.fund.metadriven.lwc.domain.Command;
import com.tanggo.fund.metadriven.lwc.domain.CommandResult;
import com.tanggo.fund.metadriven.lwc.outbound.CommandRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommandServiceTest {

    private CommandService commandService;
    private CommandRepo commandRepo;

    @Test
    void commonHandle() {
    }

    @BeforeEach
    void setUp() {
        commandService = new CommandService();
        commandRepo = new CommandRepo();
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void testCommonHandle() {


        Command command = commandRepo.queryById("ddd");
        CommandResult commandResult = commandService.handle(command);
    }
}
