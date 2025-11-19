package com.tanggo.fund.metadriven.lwc.lob.domain;

/**
 * 订单状态枚举
 */
public enum OrderStatus {
    PENDING,           // 待处理
    PARTIALLY_FILLED,  // 部分成交
    FILLED,            // 完全成交
    CANCELLED          // 已取消
}