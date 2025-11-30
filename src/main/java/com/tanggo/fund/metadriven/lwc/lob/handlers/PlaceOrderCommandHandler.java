package com.tanggo.fund.metadriven.lwc.lob.handlers;

import com.tanggo.fund.metadriven.lwc.cqrs.ICommandHandler;
import com.tanggo.fund.metadriven.lwc.lob.commands.PlaceOrderCommand;
import com.tanggo.fund.metadriven.lwc.lob.domain.LimitOrder;
import com.tanggo.fund.metadriven.lwc.lob.domain.repo.MatchResult;
import com.tanggo.fund.metadriven.lwc.lob.commands.PlaceOrderResult;
import com.tanggo.fund.metadriven.lwc.lob.service.OrderBookService;

import java.util.List;

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
        Object param = command.param();
        if (!(param instanceof PlaceOrderCommand cmd)) {
            throw new IllegalArgumentException("Command param must be PlaceOrderCommand");
        }

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
        PlaceOrderResult data = new PlaceOrderResult();
        data.setOrder(result.getOrder());
        data.setTrades(result.getTrades());
        data.setSuccess(true);

        return CommandResult.success(command, data);
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
}
