package com.tanggo.fund.metadriven.lwc.lob.service;

import com.tanggo.fund.metadriven.lwc.lob.domain.*;
import com.tanggo.fund.metadriven.lwc.lob.domain.repo.MatchResult;
import com.tanggo.fund.metadriven.lwc.lob.domain.repo.OrderBookRepository;
import com.tanggo.fund.metadriven.lwc.lob.domain.repo.OrderBookSnapshot;

import java.util.Objects;

/**
 * 订单薄服务 - 应用层服务
 * 遵循Clean Architecture原则，依赖仓储接口而非具体实现
 */
public class OrderBookService {

    private final OrderBookRepository repository;

    /**
     * 构造器注入，符合依赖倒置原则
     */
    public OrderBookService(OrderBookRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository不能为null");
    }

    /**
     * 下单
     */
    public MatchResult placeOrder(LimitOrder order) {
        Objects.requireNonNull(order, "order不能为null");
        return repository.addOrder(order);
    }

    /**
     * 撤单
     */
    public boolean cancelOrder(String symbol, String orderId) {
        Objects.requireNonNull(symbol, "symbol不能为null");
        Objects.requireNonNull(orderId, "orderId不能为null");
        return repository.cancelOrder(symbol, orderId);
    }

    /**
     * 查询订单薄快照
     */
    public OrderBookSnapshot getOrderBookSnapshot(String symbol, int depth) {
        Objects.requireNonNull(symbol, "symbol不能为null");
        if (depth <= 0) {
            throw new IllegalArgumentException("depth必须大于0");
        }
        return repository.getSnapshot(symbol, depth);
    }

    /**
     * 检查订单是否存在
     */
    public boolean orderExists(String symbol, String orderId) {
        Objects.requireNonNull(symbol, "symbol不能为null");
        Objects.requireNonNull(orderId, "orderId不能为null");
        return repository.existsOrder(symbol, orderId);
    }

    /**
     * 获取指定交易对的订单数量
     */
    public int getOrderCount(String symbol) {
        Objects.requireNonNull(symbol, "symbol不能为null");
        return repository.getOrderCount(symbol);
    }



}
