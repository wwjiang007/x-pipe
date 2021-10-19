package com.ctrip.xpipe.redis.meta.server.keeper.keepermaster.impl;

import com.ctrip.xpipe.redis.meta.server.AbstractMetaServerTest;
import com.ctrip.xpipe.redis.meta.server.meta.CurrentMetaManager;
import com.ctrip.xpipe.redis.meta.server.meta.DcMetaCache;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.*;

/**
 * @author chen.zhu
 * <p>
 * Sep 27, 2020
 */
public class AbstractClusterShardPeriodicTaskTest extends AbstractMetaServerTest {

    private AbstractClusterShardPeriodicTask task;

    private ScheduledExecutorService scheduled;

    @Mock
    private DcMetaCache dcMetaCache;

    @Mock
    private CurrentMetaManager currentMetaManager;

    private AtomicInteger counter = new AtomicInteger();

    private AtomicInteger periodSeconds = new AtomicInteger(10);

    private CountDownLatch countDownLatch;

    @Before
    public void beforeAbstractClusterShardPeriodicTaskTest() {
        MockitoAnnotations.initMocks(this);
        scheduled = Executors.newScheduledThreadPool(2);
        countDownLatch = new CountDownLatch(1);
        task = spy(new AbstractClusterShardPeriodicTask("cluster", "shard", dcMetaCache, currentMetaManager, scheduled) {
            @Override
            protected void work() {
                logger.info("[work]");
                counter.incrementAndGet();
                countDownLatch.countDown();
            }

            @Override
            protected int getWorkIntervalSeconds() {
                return periodSeconds.get();
            }
        });
    }

    @Test(expected = TimeoutException.class)
    public void testDoStart() throws Exception {
        int startTimes = 10;
        try {
            for(int i = 0; i < startTimes; i++) {
                task.start();
            }
        } catch (Exception ignore) {
        }
        verify(task, times(1)).doStart();
        countDownLatch.await();
        waitConditionUntilTimeOut(() -> counter.get() > 1, 1000, 100);
    }

    @Test(expected = TimeoutException.class)
    public void testDoStop() throws Exception {
        periodSeconds.set(1);
        int startTimes = 10;
        try {
            for(int i = 0; i < startTimes; i++) {
                task.stop();
            }
        } catch (Exception ignore) {
        }
        verify(task, times(0)).doStop();
        Assert.assertEquals(0, counter.get());

        task.start();
        int stopTimes = 10;
        try {
            for(int i = 0; i < stopTimes; i++) {
                task.stop();
            }
        } catch (Exception ignore) {
        }
        verify(task, times(1)).doStop();
        countDownLatch.await();
        waitConditionUntilTimeOut(() -> counter.get() > 1, 2000, 100);
    }

    @Test(expected = TimeoutException.class)
    public void testRelease() throws Exception {
        periodSeconds.set(1);
        task.start();
        int stopTimes = 10;
        try {
            for(int i = 0; i < stopTimes; i++) {
                task.release();
            }
        } catch (Exception ignore) {
        }
        verify(task, times(1)).doStop();
        countDownLatch.await();
        waitConditionUntilTimeOut(() -> counter.get() > 1, 2000, 100);
    }
}