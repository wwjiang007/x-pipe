package com.ctrip.xpipe.redis.proxy.session.state;

import com.ctrip.xpipe.redis.proxy.Session;
import com.ctrip.xpipe.redis.proxy.session.BackendSession;
import com.ctrip.xpipe.redis.proxy.session.SessionState;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.DefaultChannelProgressivePromise;

/**
 * @author chen.zhu
 * <p>
 * May 13, 2018
 */
public class SessionInit extends AbstractSessionState {

    public SessionInit(Session session) {
        super(session);
    }

    @Override
    protected SessionState doNextAfterSuccess() {
        return new SessionEstablished(session);
    }

    @Override
    protected SessionState doNextAfterFail() {
        return new SessionClosed(session);
    }

    @Override
    public ChannelFuture tryWrite(ByteBuf byteBuf) {
        if(session instanceof BackendSession) {
            try {
                ((BackendSession) session).sendAfterProtocol(byteBuf);
            } catch (Exception e) {
                throw new UnsupportedOperationException(e);
            }
            return new DefaultChannelProgressivePromise(session.getChannel());
        }
        throw new UnsupportedOperationException("No write through init state");
    }

    @Override
    public void disconnect() {
        throw new UnsupportedOperationException("disconnect only allowed in session closing");
    }

    @Override
    public String name() {
        return "Session-Init";
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
