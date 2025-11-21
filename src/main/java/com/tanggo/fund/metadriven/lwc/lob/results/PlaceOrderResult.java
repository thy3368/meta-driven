package com.tanggo.fund.metadriven.lwc.lob.results;

import com.tanggo.fund.metadriven.lwc.lob.domain.LimitOrder;
import com.tanggo.fund.metadriven.lwc.lob.domain.Trade;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 下单结果DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaceOrderResult {
    private boolean success;
    private LimitOrder order;
    private List<Trade> trades;

    @Override
    public String toString() {
        return String.format("PlaceOrderResult{success=%s, order=%s, trades=%d}",
                success, order, trades != null ? trades.size() : 0);
    }
}