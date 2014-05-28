// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.redis.protocol;

import com.google.common.base.Supplier;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.group.ChannelGroup;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * A netty {@link ChannelHandler} responsible for monitoring the channel and reconnecting when the connection is lost.
 * 
 * @author Will Glozer
 */
@ChannelHandler.Sharable
public class ConnectionWatchdog extends ChannelInboundHandlerAdapter implements TimerTask {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ConnectionWatchdog.class);
    public static final int RETRY_TIMEOUT_MAX = 14;
    private Bootstrap bootstrap;
    private Channel channel;
    private Timer timer;
    private boolean reconnect;
    private int attempts;
    private SocketAddress remoteAddress;
    private Supplier<SocketAddress> socketAddressSupplier;
    private boolean firstReconnect = false;

    /**
     * Create a new watchdog that adds to new connections to the supplied {@link ChannelGroup} and establishes a new
     * {@link Channel} when disconnected, while reconnect is true.
     * 
     * @param bootstrap Configuration for new channels.
     * @param timer Timer used for delayed reconnect.
     */
    public ConnectionWatchdog(Bootstrap bootstrap, Timer timer) {
        this(bootstrap, timer, null);
    }

    /**
     * Create a new watchdog that adds to new connections to the supplied {@link ChannelGroup} and establishes a new
     * {@link Channel} when disconnected, while reconnect is true. The socketAddressSupplier can supply the reconnect address.
     * 
     * @param bootstrap Configuration for new channels.
     * @param timer Timer used for delayed reconnect.
     * @param socketAddressSupplier
     */
    public ConnectionWatchdog(Bootstrap bootstrap, Timer timer, Supplier<SocketAddress> socketAddressSupplier) {
        this.bootstrap = bootstrap;
        this.timer = timer;
    }

    public void setReconnect(boolean reconnect) {
        this.reconnect = reconnect;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        firstReconnect = false;
        channel = ctx.channel();
        attempts = 0;
        remoteAddress = channel.remoteAddress();
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

        channel = null;
        if (reconnect) {
            firstReconnect = true;
            scheduleReconnect();
        }
        super.channelInactive(ctx);
    }

    private void scheduleReconnect() {
        if (channel == null || !channel.isActive()) {
            if (attempts < RETRY_TIMEOUT_MAX)
                attempts++;
            int timeout = 2 << attempts;
            timer.newTimeout(this, timeout, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Reconnect to the remote address that the closed channel was connected to. This creates a new {@link ChannelPipeline} with
     * the same handler instances contained in the old channel's pipeline.
     * 
     * @param timeout Timer task handle.
     * 
     * @throws Exception when reconnection fails.
     */
    @Override
    public void run(Timeout timeout) throws Exception {

        try {
            if (firstReconnect) {
                logger.info("Connecting");
            }
            if (socketAddressSupplier != null) {
                try {
                    remoteAddress = socketAddressSupplier.get();
                } catch (RuntimeException e) {
                    if (firstReconnect) {
                        logger.warn("Cannot retrieve the current address from socketAddressSupplier: " + e.toString());
                    }
                }
            }

            bootstrap.connect(remoteAddress).sync().channel();
            logger.info("Reconnected to " + remoteAddress);
        } catch (Exception e) {

            if (firstReconnect) {
                logger.warn("Cannot connect: " + e.toString());
            }
            scheduleReconnect();
        } finally {
            firstReconnect = false;
        }
    }
}
