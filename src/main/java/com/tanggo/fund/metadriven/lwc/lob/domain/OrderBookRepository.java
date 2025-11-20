package com.tanggo.fund.metadriven.lwc.lob.domain;



/**
 * 订单薄仓储接口 - 遵循Clean Architecture的依赖倒置原则
 * 领域层定义接口，基础设施层实现
 */
public interface OrderBookRepository {

    /**
     * 添加订单到订单薄并尝试撮合
     *
     * @param order 限价订单
     * @return 撮合结果（包含成交记录）
     */
    MatchResult addOrder(LimitOrder order);

    /**
     * 取消指定订单
     *
     * @param symbol 交易对符号
     * @param orderId 订单ID
     * @return true表示成功取消，false表示订单不存在
     */
    boolean cancelOrder(String symbol, String orderId);

    /**
     * 获取订单薄快照
     *
     * @param symbol 交易对符号
     * @param depth 深度（档位数量）
     * @return 订单薄快照
     */
    OrderBookSnapshot getSnapshot(String symbol, int depth);

    /**
     * 查询订单是否存在
     *
     * @param symbol 交易对符号
     * @param orderId 订单ID
     * @return true表示订单存在
     */
    boolean existsOrder(String symbol, String orderId);

    /**
     * 获取指定交易对的订单数量
     *
     * @param symbol 交易对符号
     * @return 订单数量
     */
    int getOrderCount(String symbol);
}
