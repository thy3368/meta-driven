package com.tanggo.fund.metadriven.lwc.domain.meta.script;

/**
 * 脚本执行异常
 */
public class ScriptExecutionException extends Exception {

    public ScriptExecutionException(String message) {
        super(message);
    }

    public ScriptExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ScriptExecutionException(Throwable cause) {
        super(cause);
    }
}
