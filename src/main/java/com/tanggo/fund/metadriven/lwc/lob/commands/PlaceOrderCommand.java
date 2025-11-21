package com.tanggo.fund.metadriven.lwc.lob.commands;

import com.tanggo.fund.metadriven.lwc.lob.domain.OrderSide;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

/**
 * 下单命令参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOrderCommand {

    private String orderId;
    private String symbol;
    private OrderSide side;
    private BigDecimal price;
    private BigDecimal quantity;
}
