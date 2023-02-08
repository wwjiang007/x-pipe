package com.ctrip.xpipe.service.client.redis;

import com.ctrip.xpipe.api.command.CommandFuture;
import com.ctrip.xpipe.client.redis.AsyncRedisClient;
import com.ctrip.xpipe.command.DefaultCommandFuture;
import com.ctrip.xpipe.exception.XpipeRuntimeException;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import credis.java.client.AsyncCacheProvider;
import credis.java.client.async.command.CRedisAsyncRequest;
import credis.java.client.async.impl.AsyncCacheProviderImpl;
import credis.java.client.async.qclient.CRedisClusterSessionManager;
import credis.java.client.async.qclient.CRedisSessionLocator;
import credis.java.client.async.qclient.network.CRedisSessionChannel;
import credis.java.client.config.route.DefaultRoute;
import credis.java.client.entity.RedisNode;
import credis.java.client.sync.RedisClient;
import credis.java.client.sync.applier.ApplierCacheProvider;
import credis.java.client.transaction.RedisTransactionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qunar.tc.qclient.redis.codec.Codec;
import qunar.tc.qclient.redis.codec.SedisCodec;
import qunar.tc.qclient.redis.command.value.ValueResult;
import redis.clients.util.SafeEncoder;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Slight
 * <p>
 * Feb 26, 2022 2:37 PM
 */
public class CRedisAsyncClient implements AsyncRedisClient {

    Logger logger = LoggerFactory.getLogger(getClass());

    final AsyncCacheProviderImpl asyncProvider;

    final Codec codec;

    final ApplierCacheProvider txnProvider;

    final ExecutorService credisNotifyThread;

    boolean isInMulti = false;

    int db = 0;

    public CRedisAsyncClient(AsyncCacheProvider asyncProvider, ApplierCacheProvider txnProvider, ExecutorService credisNotifyExecutor) {
        this.asyncProvider = (AsyncCacheProviderImpl) asyncProvider;
        this.txnProvider = txnProvider;
        this.codec = new SedisCodec();
        this.credisNotifyThread = credisNotifyExecutor;
    }

    @Override
    public Object[] broadcast() {
        /* not efficient */
        return locator().getAllSession(true).toArray();
    }

    public Map<RedisClient, RedisTransactionClient> clients2TxnClients = new HashMap<>();

    @Override
    public Object select(Object key) {
        if (isInMulti) {
            RedisClient client = txnProvider.getClientOnlyForApplier(key, clients2TxnClients.keySet());
            if (!client.isInMulti()) {
                RedisTransactionClient txnClient = client.multi();
                clients2TxnClients.put(client, txnClient);
            }
            return client;
        }
        return locator().getSessionForObject(key, true);
    }

    @Override
    public Map<Object, List<Object>> selectMulti(List<Object> keys) {
        if (isInMulti) {
            Map<RedisClient, List<Object>> clients = txnProvider.getClientsOnlyForApplier(keys, clients2TxnClients.keySet());
            for (RedisClient client : clients.keySet()) {
                if (!client.isInMulti()) {
                    RedisTransactionClient txnClient = client.multi();
                    clients2TxnClients.put(client, txnClient);
                }
            }
            return new TMapWrapper<>(clients);
        }
        return new TMapWrapper<>(locator().getSessionForList(true, keys.toArray()));
    }

    @Override
    public void selectDB(int db) {
        this.db = db;
    }

    @Override
    public CommandFuture<Object> write(Object resource, Object... rawArgs) {
        if (isInMulti) {
            try {
                RedisClient client = (RedisClient) resource;
                client.selectDB(db);
                RedisTransactionClient txnClient = clients2TxnClients.get(client);
                txnClient.write(rawArgs);
                return resultFuture("OK");
            } catch (Throwable t) {
                return errorFuture(t);
            }
        }
        CRedisSessionChannel channel = (CRedisSessionChannel) resource;
        GenericCommand command = new GenericCommand(codec);
        for (Object rawArg : rawArgs) {
            command.write(rawArg);
        }
        CRedisAsyncRequest<Object> request = CRedisAsyncRequest.from(new ValueResult<>(), db);
        DefaultCommandFuture<Object> commandFuture = new DefaultCommandFuture<>();
        channel.dispatch(request, command).addListener(new FutureCallback<Object>() {
            @Override
            public void onSuccess(@Nullable Object result) {
                commandFuture.setSuccess(result);
            }

            @Override
            public void onFailure(Throwable t) {
                commandFuture.setFailure(t);
            }
        }, 1, TimeUnit.SECONDS, credisNotifyThread);
        return commandFuture;
    }

    @Override
    public CommandFuture<Object> multi() {
        if (isInMulti) {
            return errorFuture("multi() is called when client is in multi");
        }
        isInMulti = true;
        return resultFuture("OK");
    }

    @Override
    public CommandFuture<Object> exec(Object... rawArgs) {
        if (txnProvider.isValidOnlyForApplier(clients2TxnClients.keySet())) {
            try {
                for (RedisTransactionClient txnClient : clients2TxnClients.values()) {
                    List<Object> results = txnClient.exec(rawArgs);
                    for (Object result : results) {
                        if (result instanceof Throwable) {
                            return errorFuture("one of txnClients.exec() failed", (Throwable) result);
                        }
                    }
                }
                return resultFuture("OK");
            } catch (Throwable t) {
                return errorFuture("one of txnClients.exec() failed", t);
            } finally {
                isInMulti = false;
            }
        } else {
            isInMulti = false;
            return errorFuture("txnClients not valid when exec() called");
        }
    }

    private CRedisSessionLocator locator() {
        return asyncProvider.asyncClient.cRedisSessionLocator;
    }

    private <T> CommandFuture<T> resultFuture(T result) {
        CommandFuture<T> future = new DefaultCommandFuture<>();
        future.setSuccess(result);
        return future;
    }

    private CommandFuture<Object> errorFuture(Throwable cause) {
        CommandFuture<Object> future = new DefaultCommandFuture<>();
        future.setFailure(cause);
        return future;
    }

    private CommandFuture<Object> errorFuture(String message) {
        CommandFuture<Object> future = new DefaultCommandFuture<>();
        future.setFailure(new XpipeRuntimeException(message));
        return future;
    }

    private CommandFuture<Object> errorFuture(String message, Throwable cause) {
        CommandFuture<Object> future = new DefaultCommandFuture<>();
        future.setFailure(new XpipeRuntimeException(message, cause));
        return future;
    }
}
