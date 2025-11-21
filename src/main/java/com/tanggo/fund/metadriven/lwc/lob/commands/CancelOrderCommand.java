package com.tanggo.fund.metadriven.lwc.lob.commands;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 撤单命令参数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelOrderCommand {

    private String orderId;
    private String symbol;
}
