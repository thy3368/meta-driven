package com.tanggo.fund.metadriven.lwc.lob.domain;

import java.math.BigDecimal;

/**
 * 价格档位
 */
public  class PriceLevel {
    private final BigDecimal price;
    private final BigDecimal quantity;
    private final int orderCount;

    public PriceLevel(BigDecimal price, BigDecimal quantity, int orderCount) {
        this.price = price;
        this.quantity = quantity;
        this.orderCount = orderCount;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public int getOrderCount() {
        return orderCount;
    }

    @Override
    public String toString() {
        return String.format("[%s @ %s (%d orders)]", quantity, price, orderCount);
    }
}
