package com.tanggo.fund.metadriven.lwc.lob.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 限价订单实体 - Clean Architecture实体层
 * 纯业务逻辑，无外部依赖
 */
public class LimitOrder {

    private final String orderId;
    private final String symbol;
    private final OrderSide side;
    private final BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal filledQuantity;
    private OrderStatus status;
    private final Instant createTime;
    private Instant updateTime;

    public LimitOrder(String orderId, String symbol, OrderSide side,
                      BigDecimal price, BigDecimal quantity) {
        if (orderId == null || orderId.isEmpty()) {
            throw new IllegalArgumentException("OrderId cannot be null or empty");
        }
        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.filledQuantity = BigDecimal.ZERO;
        this.status = OrderStatus.PENDING;
        this.createTime = Instant.now();
        this.updateTime = Instant.now();
    }

    /**
     * 业务规则：部分成交
     */
    public void fill(BigDecimal fillQuantity) {
        if (fillQuantity == null || fillQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Fill quantity must be positive");
        }
        if (this.status == OrderStatus.CANCELLED || this.status == OrderStatus.FILLED) {
            throw new IllegalStateException("Cannot fill cancelled or filled order");
        }

        BigDecimal newFilledQty = this.filledQuantity.add(fillQuantity);
        if (newFilledQty.compareTo(this.quantity) > 0) {
            throw new IllegalArgumentException("Fill quantity exceeds remaining quantity");
        }

        this.filledQuantity = newFilledQty;
        this.updateTime = Instant.now();

        if (this.filledQuantity.compareTo(this.quantity) == 0) {
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    /**
     * 业务规则：取消订单
     */
    public void cancel() {
        if (this.status == OrderStatus.FILLED) {
            throw new IllegalStateException("Cannot cancel filled order");
        }
        this.status = OrderStatus.CANCELLED;
        this.updateTime = Instant.now();
    }

    /**
     * 获取剩余数量
     */
    public BigDecimal getRemainingQuantity() {
        return this.quantity.subtract(this.filledQuantity);
    }

    /**
     * 判断是否可成交
     */
    public boolean isActive() {
        return this.status == OrderStatus.PENDING ||
               this.status == OrderStatus.PARTIALLY_FILLED;
    }

    // Getters
    public String getOrderId() {
        return orderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public OrderSide getSide() {
        return side;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getFilledQuantity() {
        return filledQuantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }

    @Override
    public String toString() {
        return String.format("LimitOrder{id=%s, symbol=%s, side=%s, price=%s, qty=%s, filled=%s, status=%s}",
                orderId, symbol, side, price, quantity, filledQuantity, status);
    }
}