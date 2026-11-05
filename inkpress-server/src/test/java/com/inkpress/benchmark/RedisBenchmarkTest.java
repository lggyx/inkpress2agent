package com.inkpress.benchmark;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis 基准测试（对应论文 5.3 节浏览量方案的性能依据）。
 * 管线批量测量 SET / GET / INCR 吞吐与单次 GET 延迟。
 * 前置条件：MySQL 与 Redis 容器运行中（docker compose up -d mysql redis）。
 * 复现：mvn test -Dtest=RedisBenchmarkTest
 */
@SpringBootTest
class RedisBenchmarkTest {

    private static final int N = 50000;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void redisBenchmark() {
        assumeRedisUp();

        long t0 = System.nanoTime();
        redis.executePipelined((RedisCallback<Object>) conn -> {
            for (int i = 0; i < N; i++) {
                conn.stringCommands().set(("bench:key:" + i).getBytes(), ("value:" + i).getBytes());
            }
            return null;
        });
        double setSec = (System.nanoTime() - t0) / 1e9;

        t0 = System.nanoTime();
        redis.executePipelined((RedisCallback<Object>) conn -> {
            for (int i = 0; i < N; i++) {
                conn.stringCommands().get(("bench:key:" + i).getBytes());
            }
            return null;
        });
        double getSec = (System.nanoTime() - t0) / 1e9;

        t0 = System.nanoTime();
        redis.executePipelined((RedisCallback<Object>) conn -> {
            for (int i = 0; i < N; i++) {
                conn.stringCommands().incr("bench:counter".getBytes());
            }
            return null;
        });
        double incrSec = (System.nanoTime() - t0) / 1e9;

        // 单次 GET 延迟采样
        double[] lat = new double[200];
        for (int i = 0; i < lat.length; i++) {
            long s = System.nanoTime();
            redis.opsForValue().get("bench:key:" + i);
            lat[i] = (System.nanoTime() - s) / 1e6;
        }
        java.util.Arrays.sort(lat);
        double p50 = lat[lat.length / 2];
        double p99 = lat[(int) (lat.length * 0.99)];

        System.out.printf("SET:  %d 次管线批量, 耗时 %.3fs, 吞吐 %,.0f ops/s%n", N, setSec, N / setSec);
        System.out.printf("GET:  %d 次管线批量, 耗时 %.3fs, 吞吐 %,.0f ops/s%n", N, getSec, N / getSec);
        System.out.printf("INCR: %d 次管线批量, 耗时 %.3fs, 吞吐 %,.0f ops/s%n", N, incrSec, N / incrSec);
        System.out.printf("单次 GET 延迟: p50=%.2fms, p99=%.2fms%n", p50, p99);

        // 基线断言：本机 Redis 单机吞吐应远高于浏览量场景需求
        assertTrue(N / incrSec > 10000, "INCR 吞吐应高于 10000 ops/s");

        // 清理基准键
        redis.executePipelined((RedisCallback<Object>) conn -> {
            for (int i = 0; i < N; i++) {
                conn.keyCommands().del(("bench:key:" + i).getBytes());
            }
            conn.keyCommands().del("bench:counter".getBytes());
            return null;
        });
    }

    private void assumeRedisUp() {
        boolean up;
        try {
            String pong = redis.execute((RedisCallback<String>) conn -> conn.ping());
            up = "PONG".equalsIgnoreCase(pong);
        } catch (Exception e) {
            up = false;
        }
        Assumptions.assumeTrue(up, "Redis 不可用（docker compose up -d mysql redis），跳过基准测试");
    }
}
