package com.tanggo.fund.metadriven.lwc.lob.results;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 撤单结果DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderResult {
    private boolean success;
    private String orderId;

    @Override
    public String toString() {
        return String.format("CancelOrderResult{success=%s, orderId=%s}", success, orderId);
    }
}