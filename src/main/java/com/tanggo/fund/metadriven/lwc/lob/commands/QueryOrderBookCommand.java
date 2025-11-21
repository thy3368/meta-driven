package com.tanggo.fund.metadriven.lwc.lob.commands;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 查询订单薄命令参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryOrderBookCommand {

    private String symbol;
    private Integer depth;  // 查询深度
}
