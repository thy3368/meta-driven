package com.tanggo.fund.metadriven.lwc.lob.service;

import com.tanggo.fund.metadriven.lwc.lob.domain.LimitOrder;
import com.tanggo.fund.metadriven.lwc.lob.domain.OrderSide;
import com.tanggo.fund.metadriven.lwc.lob.domain.Trade;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单薄服务 - 使用TreeMap实现价格优先、时间优先的撮合逻辑
 * 符合低时延要求：O(log n)插入/删除性能
 */
public class OrderBookService {

    // 每个交易对的订单薄
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();

    /**
     * 下单
     */
    public MatchResult placeOrder(LimitOrder order) {
        OrderBook book = orderBooks.computeIfAbsent(
            order.getSymbol(),
            k -> new OrderBook()
        );
        return book.addOrder(order);
    }

    /**
     * 撤单
     */
    public boolean cancelOrder(String symbol, String orderId) {
        OrderBook book = orderBooks.get(symbol);
        if (book == null) {
            return false;
        }
        return book.cancelOrder(orderId);
    }

    /**
     * 查询订单薄快照
     */
    public OrderBookSnapshot getOrderBookSnapshot(String symbol, int depth) {
        OrderBook book = orderBooks.get(symbol);
        if (book == null) {
            return new OrderBookSnapshot(symbol, Collections.emptyList(), Collections.emptyList());
        }
        return book.getSnapshot(depth);
    }

    /**
     * 内部订单薄类
     */
    private static class OrderBook {
        // 买单：价格降序（最高价优先）
        private final TreeMap<BigDecimal, LinkedList<LimitOrder>> bids =
            new TreeMap<>(Comparator.reverseOrder());

        // 卖单：价格升序（最低价优先）
        private final TreeMap<BigDecimal, LinkedList<LimitOrder>> asks =
            new TreeMap<>();

        // 订单ID -> 订单映射（快速查找）
        private final Map<String, LimitOrder> orderIndex = new ConcurrentHashMap<>();

        /**
         * 添加订单并尝试撮合
         */
        public MatchResult addOrder(LimitOrder order) {
            List<Trade> trades = new ArrayList<>();

            // 尝试撮合
            if (order.getSide() == OrderSide.BUY) {
                matchBuyOrder(order, trades);
            } else {
                matchSellOrder(order, trades);
            }

            // 如果订单未完全成交，加入订单薄
            if (order.isActive()) {
                TreeMap<BigDecimal, LinkedList<LimitOrder>> side =
                    order.getSide() == OrderSide.BUY ? bids : asks;

                side.computeIfAbsent(order.getPrice(), k -> new LinkedList<>())
                    .addLast(order);
                orderIndex.put(order.getOrderId(), order);
            }

            return new MatchResult(order, trades);
        }

        /**
         * 撮合买单
         */
        private void matchBuyOrder(LimitOrder buyOrder, List<Trade> trades) {
            while (buyOrder.isActive() && !asks.isEmpty()) {
                Map.Entry<BigDecimal, LinkedList<LimitOrder>> bestAsk = asks.firstEntry();
                BigDecimal askPrice = bestAsk.getKey();

                // 价格不匹配，停止撮合
                if (buyOrder.getPrice().compareTo(askPrice) < 0) {
                    break;
                }

                LinkedList<LimitOrder> askOrders = bestAsk.getValue();
                LimitOrder sellOrder = askOrders.getFirst();

                // 执行成交
                BigDecimal tradeQty = buyOrder.getRemainingQuantity()
                    .min(sellOrder.getRemainingQuantity());

                buyOrder.fill(tradeQty);
                sellOrder.fill(tradeQty);

                trades.add(new Trade(
                    buyOrder.getOrderId(),
                    sellOrder.getOrderId(),
                    askPrice,
                    tradeQty
                ));

                // 卖单完全成交，移除
                if (!sellOrder.isActive()) {
                    askOrders.removeFirst();
                    orderIndex.remove(sellOrder.getOrderId());
                    if (askOrders.isEmpty()) {
                        asks.remove(askPrice);
                    }
                }
            }
        }

        /**
         * 撮合卖单
         */
        private void matchSellOrder(LimitOrder sellOrder, List<Trade> trades) {
            while (sellOrder.isActive() && !bids.isEmpty()) {
                Map.Entry<BigDecimal, LinkedList<LimitOrder>> bestBid = bids.firstEntry();
                BigDecimal bidPrice = bestBid.getKey();

                // 价格不匹配，停止撮合
                if (sellOrder.getPrice().compareTo(bidPrice) > 0) {
                    break;
                }

                LinkedList<LimitOrder> bidOrders = bestBid.getValue();
                LimitOrder buyOrder = bidOrders.getFirst();

                // 执行成交
                BigDecimal tradeQty = sellOrder.getRemainingQuantity()
                    .min(buyOrder.getRemainingQuantity());

                sellOrder.fill(tradeQty);
                buyOrder.fill(tradeQty);

                trades.add(new Trade(
                    buyOrder.getOrderId(),
                    sellOrder.getOrderId(),
                    bidPrice,
                    tradeQty
                ));

                // 买单完全成交，移除
                if (!buyOrder.isActive()) {
                    bidOrders.removeFirst();
                    orderIndex.remove(buyOrder.getOrderId());
                    if (bidOrders.isEmpty()) {
                        bids.remove(bidPrice);
                    }
                }
            }
        }

        /**
         * 取消订单
         */
        public boolean cancelOrder(String orderId) {
            LimitOrder order = orderIndex.remove(orderId);
            if (order == null) {
                return false;
            }

            TreeMap<BigDecimal, LinkedList<LimitOrder>> side =
                order.getSide() == OrderSide.BUY ? bids : asks;

            LinkedList<LimitOrder> priceLevel = side.get(order.getPrice());
            if (priceLevel != null) {
                priceLevel.remove(order);
                if (priceLevel.isEmpty()) {
                    side.remove(order.getPrice());
                }
            }

            order.cancel();
            return true;
        }

        /**
         * 获取订单薄快照
         */
        public OrderBookSnapshot getSnapshot(int depth) {
            List<PriceLevel> bidLevels = new ArrayList<>();
            List<PriceLevel> askLevels = new ArrayList<>();

            // 收集买单深度
            int count = 0;
            for (Map.Entry<BigDecimal, LinkedList<LimitOrder>> entry : bids.entrySet()) {
                if (count >= depth) break;
                BigDecimal totalQty = entry.getValue().stream()
                    .map(LimitOrder::getRemainingQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                bidLevels.add(new PriceLevel(entry.getKey(), totalQty, entry.getValue().size()));
                count++;
            }

            // 收集卖单深度
            count = 0;
            for (Map.Entry<BigDecimal, LinkedList<LimitOrder>> entry : asks.entrySet()) {
                if (count >= depth) break;
                BigDecimal totalQty = entry.getValue().stream()
                    .map(LimitOrder::getRemainingQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                askLevels.add(new PriceLevel(entry.getKey(), totalQty, entry.getValue().size()));
                count++;
            }

            return new OrderBookSnapshot("", bidLevels, askLevels);
        }
    }

    /**
     * 撮合结果
     */
    public static class MatchResult {
        private final LimitOrder order;
        private final List<Trade> trades;

        public MatchResult(LimitOrder order, List<Trade> trades) {
            this.order = order;
            this.trades = trades;
        }

        public LimitOrder getOrder() {
            return order;
        }

        public List<Trade> getTrades() {
            return trades;
        }

        public boolean hasMatched() {
            return !trades.isEmpty();
        }
    }



    /**
     * 价格档位
     */
    public static class PriceLevel {
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

    /**
     * 订单薄快照
     */
    public static class OrderBookSnapshot {
        private final String symbol;
        private final List<PriceLevel> bids;
        private final List<PriceLevel> asks;

        public OrderBookSnapshot(String symbol, List<PriceLevel> bids, List<PriceLevel> asks) {
            this.symbol = symbol;
            this.bids = bids;
            this.asks = asks;
        }

        public String getSymbol() {
            return symbol;
        }

        public List<PriceLevel> getBids() {
            return bids;
        }

        public List<PriceLevel> getAsks() {
            return asks;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("OrderBook[").append(symbol).append("]\n");
            sb.append("Asks:\n");
            for (int i = asks.size() - 1; i >= 0; i--) {
                sb.append("  ").append(asks.get(i)).append("\n");
            }
            sb.append("Bids:\n");
            for (PriceLevel bid : bids) {
                sb.append("  ").append(bid).append("\n");
            }
            return sb.toString();
        }
    }
}
