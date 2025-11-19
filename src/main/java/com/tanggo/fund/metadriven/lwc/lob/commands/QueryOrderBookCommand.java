package com.tanggo.fund.metadriven.lwc.lob.commands;

import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 查询订单薄命令
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryOrderBookCommand extends Command {

    private String symbol;
    private Integer depth;  // 查询深度

    public QueryOrderBookCommand() {
        super();
        setMethodName("queryOrderBook");
    }

    public QueryOrderBookCommand(String symbol, Integer depth) {
        this();
        this.symbol = symbol;
        this.depth = depth;
        setInputs(this);
    }
}