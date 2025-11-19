package com.tanggo.fund.metadriven.lwc.lob.handlers;

import com.tanggo.fund.metadriven.lwc.cqrs.ICommandHandler;
import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import com.tanggo.fund.metadriven.lwc.cqrs.types.CommandResult;
import com.tanggo.fund.metadriven.lwc.lob.commands.QueryOrderBookCommand;
import com.tanggo.fund.metadriven.lwc.lob.service.OrderBookService;

/**
 * 查询订单薄命令处理器
 */
public class QueryOrderBookCommandHandler implements ICommandHandler {

    private OrderBookService orderBookService;

    // Setter for Spring XML injection
    public void setOrderBookService(OrderBookService orderBookService) {
        this.orderBookService = orderBookService;
    }

    @Override
    public CommandResult handle(Command command) {
        if (!(command instanceof QueryOrderBookCommand)) {
            throw new IllegalArgumentException("Command must be QueryOrderBookCommand");
        }

        QueryOrderBookCommand cmd = (QueryOrderBookCommand) command;

        // 查询订单薄快照
        int depth = cmd.getDepth() != null ? cmd.getDepth() : 10;
        OrderBookService.OrderBookSnapshot snapshot =
            orderBookService.getOrderBookSnapshot(cmd.getSymbol(), depth);

        // 构造返回结果
        CommandResult cmdResult = new CommandResult();
        cmdResult.setDate(snapshot);

        return cmdResult;
    }
}