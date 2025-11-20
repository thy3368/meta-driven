package com.tanggo.fund.metadriven.lwc.cqrs;


import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import com.tanggo.fund.metadriven.lwc.cqrs.types.CommandResult;
import com.tanggo.fund.metadriven.lwc.cqrs.types.EntityEvent;

import java.util.List;

public interface ICommandHandler {
    CommandResult handle(Command command);

    void afterHandle(Command command, List<EntityEvent> entityEvents);

    void proHandle(Command command);

    //执行真实业务命令
    List<EntityEvent> doHandle(Command command);
}
