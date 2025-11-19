package com.tanggo.fund.metadriven.lwc.lob.commands;

import com.tanggo.fund.metadriven.lwc.cqrs.types.Command;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 撤单命令
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CancelOrderCommand extends Command {

    private String orderId;
    private String symbol;

    public CancelOrderCommand() {
        super();
        setMethodName("cancelOrder");
    }

    public CancelOrderCommand(String orderId, String symbol) {
        this();
        this.orderId = orderId;
        this.symbol = symbol;
        setInputs(this);
    }
}