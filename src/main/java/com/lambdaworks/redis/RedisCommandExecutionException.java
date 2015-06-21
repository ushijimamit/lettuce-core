package com.lambdaworks.redis;

/**
 * Exception for errors states reported by Redis.
 * 
 * @author <a href="mailto:mpaluch@paluch.biz">Mark Paluch</a>
 */
@SuppressWarnings("serial")
public class RedisCommandExecutionException extends RedisException {

    public RedisCommandExecutionException(String msg) {
        super(msg);
    }

    public RedisCommandExecutionException(String msg, Throwable e) {
        super(msg, e);
    }

}
