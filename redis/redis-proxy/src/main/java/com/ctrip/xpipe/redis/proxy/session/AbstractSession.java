package com.ctrip.xpipe.redis.proxy.session;

import com.ctrip.xpipe.observer.AbstractLifecycleObservable;
import com.ctrip.xpipe.redis.core.proxy.endpoint.ProxyEndpoint;
import com.ctrip.xpipe.redis.proxy.Session;
import com.ctrip.xpipe.redis.proxy.Tunnel;
import com.ctrip.xpipe.redis.proxy.model.SessionMeta;
import com.ctrip.xpipe.redis.proxy.session.state.SessionClosed;
import com.ctrip.xpipe.utils.ChannelUtil;
import com.ctrip.xpipe.utils.VisibleForTesting;
import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author chen.zhu
 * <p>
 * May 24, 2018
 */
public abstract class AbstractSession extends AbstractLifecycleObservable implements Session {

    protected final static Logger logger = LoggerFactory.getLogger(AbstractSession.class);

    protected static final String SESSION_STATE_CHANGE = "Session.State.Change";

    protected ProxyEndpoint endpoint;

    protected Channel channel;

    private Tunnel tunnel;

    protected long trafficReportIntervalMillis;

    private List<SessionEventHandler> handlers = Lists.newArrayList();

    private volatile SessionWritableState writableState = SessionWritableState.WRITABLE;

    public AbstractSession(Tunnel tunnel, long trafficReportIntervalMillis) {
        this.tunnel = tunnel;
        this.trafficReportIntervalMillis = trafficReportIntervalMillis;
    }

    @Override
    public Tunnel tunnel() {
        return tunnel;
    }

    @Override
    public void disconnect() {
        getSessionState().disconnect();
    }

    @Override
    public void addSessionEventHandler(SessionEventHandler handler) {
        handlers.add(handler);
    }

    @Override
    public void makeReadable() {
        ChannelUtil.triggerChannelAutoRead(getChannel());
    }

    @Override
    public void makeUnReadable() {
        ChannelUtil.closeChannelAutoRead(getChannel());
    }

    @Override
    public void setWritableState(SessionWritableState state) {
        if(state == writableState) {
            return;
        }
        switch (state) {
            case WRITABLE:
                onSessionWritable();
                break;
            case UNWRITABLE:
                onSessionNotWritable();
                break;

                default: break;
        }
    }

    protected void onSessionCreate() {
        for(SessionEventHandler handler : handlers) {
            handler.onInit();
        }
    }

    protected void onSessionEstablished() {
        for(SessionEventHandler handler : handlers) {
            handler.onEstablished();
        }
    }

    private void onSessionWritable() {
        for(SessionEventHandler handler : handlers) {
            handler.onWritable();
        }
    }

    private void onSessionNotWritable() {
        for(SessionEventHandler handler : handlers) {
            handler.onNotWritable();
        }
    }

    public void doDisconnect() {
        try {
            channel.close();
        } catch (Exception e) {
            logger.error("[doDisconnect] session: {}", getSessionMeta(), e);
        }
    }

    @Override
    public ChannelFuture tryWrite(ByteBuf byteBuf) {
        return getSessionState().tryWrite(byteBuf);
    }

    public ChannelFuture doWrite(ByteBuf byteBuf) {
        if(logger.isDebugEnabled()) {
            logger.debug("[doWrite] {}: {}", getSessionType(), ByteBufUtil.prettyHexDump(byteBuf));
        }
        return getChannel().writeAndFlush(byteBuf.retain());
    }

    @Override
    public void setSessionState(SessionState newState) {
        if(!getSessionState().isValidNext(newState)) {
            logger.debug("[setSessionState] Set state failed, state relationship not match, old: {}, new: {}",
                    getSessionState(), newState.name());
            return;
        }
        doSetSessionState(newState);
    }

    protected abstract void doSetSessionState(SessionState newState);

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public SessionMeta getSessionMeta() {
        try {
            return new SessionMeta(this, endpoint, getSessionState());
        } catch (Exception e) {
            return new SessionMeta(getSessionType().name(), "unknown", "unknown", getSessionState().name());
        }
    }

    @Override
    public SESSION_TYPE getSessionType() {
        return null;
    }

    @Override
    public void release() {
        if(channel != null) {
            ChannelUtil.closeOnFlush(channel);
        }
        setSessionState(new SessionClosed(this));
    }

    @Override
    protected void doInitialize() throws Exception {
        super.doInitialize();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
    }

    @Override
    public boolean isReleasable() {
        SessionState sessionState = getSessionState();
        return !(sessionState instanceof SessionClosed);
    }

    @VisibleForTesting
    public void setChannel(Channel channel) {
        AbstractSession.this.channel = channel;
    }

}
