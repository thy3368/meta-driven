package com.tanggo.fund.metadriven.lwc.lob.commands;

import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import com.tanggo.fund.metadriven.lwc.lob.domain.OrderSide;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 下单命令
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PlaceOrderCommand extends Command {

    private String orderId;
    private String symbol;
    private OrderSide side;
    private BigDecimal price;
    private BigDecimal quantity;

    public PlaceOrderCommand() {
        super();
        setMethodName("placeOrder");
    }

    public PlaceOrderCommand(String orderId, String symbol, OrderSide side,
                             BigDecimal price, BigDecimal quantity) {
        this();
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        setInputs(this);
    }
}