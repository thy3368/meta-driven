package com.tanggo.fund.metadriven.lwc.lob.domain.repo;

import com.tanggo.fund.metadriven.lwc.lob.domain.LimitOrder;
import com.tanggo.fund.metadriven.lwc.lob.domain.Trade;

import java.util.List;

/**
 * 撮合结果
 */
public class MatchResult {
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

    @Override
    public String toString() {
        return String.format("MatchResult[order=%s, tradesCount=%d, matched=%s]", order.getOrderId(), trades.size(), hasMatched());
    }
}


