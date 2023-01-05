package com.ctrip.xpipe.redis.keeper.applier.command;

import com.ctrip.xpipe.api.command.CommandFuture;
import com.ctrip.xpipe.api.command.CommandFutureListener;
import com.ctrip.xpipe.client.redis.AsyncRedisClient;
import com.ctrip.xpipe.command.AbstractCommand;
import com.ctrip.xpipe.command.ParallelCommandChain;
import com.ctrip.xpipe.redis.core.redis.operation.RedisKey;
import com.ctrip.xpipe.redis.core.redis.operation.RedisMultiKeyOp;
import com.ctrip.xpipe.redis.core.redis.operation.RedisOp;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class MultiDataCommand extends AbstractCommand<Boolean> implements RedisOpDataCommand<Boolean> {

    final AsyncRedisClient client;

    final RedisMultiKeyOp redisMultiKeyOp;

    final ExecutorService workThreads;

    public MultiDataCommand(AsyncRedisClient client, RedisMultiKeyOp redisMultiKeyOp, ExecutorService workThreads) {
        this.client = client;
        this.redisMultiKeyOp = redisMultiKeyOp;
        this.workThreads = workThreads;
    }

    @Override
    protected void doExecute() throws Throwable {
        List<Object> keys = keys().stream().map(RedisKey::get).collect(Collectors.toList());
        DefaultDataCommand[] dataCommands = client.selectMulti(keys).entrySet().stream().map(e ->
                new DefaultDataCommand(client,
                        /* resource */ e.getKey(),
                        /* subOp */ redisOpAsMulti().subOp(e.getValue().stream().map(keys::indexOf).collect(Collectors.toSet())))).toArray(DefaultDataCommand[]::new);
        ParallelCommandChain parallelCommandChain = new ParallelCommandChain(workThreads, dataCommands);
        parallelCommandChain.execute().addListener(new CommandFutureListener<Object>() {
            @Override
            public void operationComplete(CommandFuture<Object> commandFuture) throws Exception {
                if (commandFuture.isSuccess()) {
                    MultiDataCommand.this.future().setSuccess(true);
                } else {
                    MultiDataCommand.this.future().setFailure(commandFuture.cause());
                }
            }
        });
    }

    @Override
    protected void doReset() {

    }

    @Override
    public RedisOp redisOp() {
        return redisMultiKeyOp;
    }
}
