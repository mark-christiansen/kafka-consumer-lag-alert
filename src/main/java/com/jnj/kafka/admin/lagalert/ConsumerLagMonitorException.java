package com.jnj.kafka.admin.lagalert;

public class ConsumerLagMonitorException extends Exception {

    public ConsumerLagMonitorException() {
    }

    public ConsumerLagMonitorException(String message) {
        super(message);
    }

    public ConsumerLagMonitorException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConsumerLagMonitorException(Throwable cause) {
        super(cause);
    }

    public ConsumerLagMonitorException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
