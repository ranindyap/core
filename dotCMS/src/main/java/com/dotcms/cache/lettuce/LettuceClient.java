package com.dotcms.cache.lettuce;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.util.Config;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.CompressionCodec;
import io.lettuce.core.masterreplica.MasterReplica;
import io.lettuce.core.masterreplica.StatefulRedisMasterReplicaConnection;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.support.ConnectionPoolSupport;
import io.vavr.control.Try;

public enum LettuceClient {



    INSTANCE;

    private final List<RedisURI> redisUris =
                    Arrays.asList(Config.getStringArrayProperty("redis.connection.uris", new String[] {"redis://oboxturbo"}))
                                    .stream().map(u -> RedisURI.create(u)).collect(Collectors.toList());

    private final int timeout = Config.getIntProperty("redis.server.timeout.ms", 10000);

    private final GenericObjectPool<StatefulRedisConnection<String, Object>> pool;

    LettuceClient() {
        pool = buildPool();
    }

    // Static getter
    public static LettuceClient getInstance() {
        return INSTANCE;
    }

    StatefulRedisConnection<String, Object> get() {
        return Try.of(() -> pool.borrowObject()).getOrElseThrow(e -> new DotRuntimeException(e));

    }

    private GenericObjectPool<StatefulRedisConnection<String, Object>> buildPool() {

        GenericObjectPoolConfig config = new GenericObjectPoolConfig();
        config.setMinIdle(2);
        config.setMaxIdle(5);
        config.setMaxTotal(20);


        return ConnectionPoolSupport.createGenericObjectPool(() -> {
            ClientResources sharedResources = DefaultClientResources.create();
            RedisClient lettuceClient = RedisClient.create(sharedResources);

            StatefulRedisMasterReplicaConnection<String, Object> connection = MasterReplica.connect(lettuceClient,
                            CompressionCodec.valueCompressor(new DotObjectCodec(), CompressionCodec.CompressionType.GZIP),
                            redisUris);
            connection.setReadFrom(ReadFrom.MASTER_PREFERRED);
            if (timeout > 0) {
                connection.setTimeout(Duration.ofMillis(timeout));
            }
            return connection;
        }, config, true);


    }
}

