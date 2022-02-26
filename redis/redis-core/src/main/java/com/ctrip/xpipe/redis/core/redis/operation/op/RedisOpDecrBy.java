package com.ctrip.xpipe.redis.core.redis.operation.op;

import com.ctrip.xpipe.redis.core.redis.operation.RedisKey;
import com.ctrip.xpipe.redis.core.redis.operation.RedisOpType;
import com.ctrip.xpipe.redis.core.redis.operation.RedisSingleKeyOp;

import java.util.List;

/**
 * @author lishanglin
 * date 2022/2/19
 */
public class RedisOpDecrBy extends AbstractRedisSingleKeyOp<Long> implements RedisSingleKeyOp<Long> {

    public RedisOpDecrBy(List<String> rawArgs, RedisKey redisKey, Long redisValue) {
        super(rawArgs, redisKey, redisValue);
    }

    public RedisOpDecrBy(List<String> rawArgs, RedisKey redisKey, Long redisValue, String gtid) {
        super(rawArgs, redisKey, redisValue, gtid);
    }

    @Override
    public RedisOpType getOpType() {
        return RedisOpType.DECRBY;
    }
}
