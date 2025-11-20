package com.tanggo.fund.metadriven.lwc.lob.handlers;

import com.tanggo.fund.metadriven.lwc.cqrs.ICommandHandler;
import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import com.tanggo.fund.metadriven.lwc.cqrs.types.CommandResult;
import com.tanggo.fund.metadriven.lwc.cqrs.types.EntityEvent;
import com.tanggo.fund.metadriven.lwc.lob.commands.CancelOrderCommand;
import com.tanggo.fund.metadriven.lwc.lob.service.OrderBookService;

import java.util.List;

/**
 * 撤单命令处理器
 */
public class CancelOrderCommandHandler implements ICommandHandler {

    private OrderBookService orderBookService;

    // Setter for Spring XML injection
    public void setOrderBookService(OrderBookService orderBookService) {
        this.orderBookService = orderBookService;
    }

    @Override
    public CommandResult handle(Command command) {
        if (!(command instanceof CancelOrderCommand)) {
            throw new IllegalArgumentException("Command must be CancelOrderCommand");
        }

        CancelOrderCommand cmd = (CancelOrderCommand) command;

        // 执行撤单
        boolean success = orderBookService.cancelOrder(cmd.getSymbol(), cmd.getOrderId());

        // 构造返回结果
        CommandResult cmdResult = new CommandResult();
        CancelOrderResult data = new CancelOrderResult();
        data.setSuccess(success);
        data.setOrderId(cmd.getOrderId());
        cmdResult.setDate(data);

        return cmdResult;
    }

    @Override
    public void afterHandle(Command command, List<EntityEvent> entityEvents) {

    }

    @Override
    public void proHandle(Command command) {

    }

    @Override
    public List<EntityEvent> doHandle(Command command) {
        return List.of();
    }

    /**
     * 撤单结果DTO
     */
    public static class CancelOrderResult {
        private boolean success;
        private String orderId;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        @Override
        public String toString() {
            return String.format("CancelOrderResult{success=%s, orderId=%s}", success, orderId);
        }
    }
}
