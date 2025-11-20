package com.tanggo.fund.metadriven.lwc.lob.domain;

import java.util.List;

public class OrderBookSnapshot {
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
