package com.tanggo.fund.metadriven.lwc.lob.handlers;

import com.tanggo.fund.metadriven.lwc.cqrs.ICommandHandler;
import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import com.tanggo.fund.metadriven.lwc.cqrs.types.CommandResult;
import com.tanggo.fund.metadriven.lwc.lob.commands.PlaceOrderCommand;
import com.tanggo.fund.metadriven.lwc.lob.domain.LimitOrder;
import com.tanggo.fund.metadriven.lwc.lob.domain.MatchResult;
import com.tanggo.fund.metadriven.lwc.lob.domain.Trade;
import com.tanggo.fund.metadriven.lwc.lob.service.OrderBookService;

/**
 * 下单命令处理器
 */
public class PlaceOrderCommandHandler implements ICommandHandler {

    private OrderBookService orderBookService;

    // Setter for Spring XML injection
    public void setOrderBookService(OrderBookService orderBookService) {
        this.orderBookService = orderBookService;
    }

    @Override
    public CommandResult handle(Command command) {
        if (!(command instanceof PlaceOrderCommand)) {
            throw new IllegalArgumentException("Command must be PlaceOrderCommand");
        }

        PlaceOrderCommand cmd = (PlaceOrderCommand) command;

        // 创建限价订单
        LimitOrder order = new LimitOrder(
            cmd.getOrderId(),
            cmd.getSymbol(),
            cmd.getSide(),
            cmd.getPrice(),
            cmd.getQuantity()
        );

        // 提交到订单薄
        MatchResult result = orderBookService.placeOrder(order);

        // 构造返回结果
        CommandResult cmdResult = new CommandResult();
        PlaceOrderResult data = new PlaceOrderResult();
        data.setOrder(result.getOrder());
        data.setTrades(result.getTrades());
        data.setSuccess(true);
        cmdResult.setDate(data);

        return cmdResult;
    }

    /**
     * 下单结果DTO
     */
    public static class PlaceOrderResult {
        private boolean success;
        private LimitOrder order;
        private java.util.List<Trade> trades;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public LimitOrder getOrder() {
            return order;
        }

        public void setOrder(LimitOrder order) {
            this.order = order;
        }

        public java.util.List<Trade> getTrades() {
            return trades;
        }

        public void setTrades(java.util.List<Trade> trades) {
            this.trades = trades;
        }

        @Override
        public String toString() {
            return String.format("PlaceOrderResult{success=%s, order=%s, trades=%d}",
                    success, order, trades != null ? trades.size() : 0);
        }
    }
}
