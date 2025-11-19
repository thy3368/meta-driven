package com.tanggo.fund.metadriven.lwc.lob.domain;

import java.math.BigDecimal;


/**
 * 成交记录
 */
public class Trade {
    private final String buyOrderId;
    private final String sellOrderId;
    private final BigDecimal price;
    private final BigDecimal quantity;

    public Trade(String buyOrderId, String sellOrderId, BigDecimal price, BigDecimal quantity) {
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.price = price;
        this.quantity = quantity;
    }

    public String getBuyOrderId() {
        return buyOrderId;
    }

    public String getSellOrderId() {
        return sellOrderId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    @Override
    public String toString() {
        return String.format("Trade{buy=%s, sell=%s, price=%s, qty=%s}", buyOrderId, sellOrderId, price, quantity);
    }
}
