package com.inkpress.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语义检索基准测试（对应论文 4.4 节 / 6.3 节检索实验的性能数据）。
 * 通过 HTTP 调用运行中的后端（28080），测量冷启动与热态延迟。
 * 前置条件：后端已启动（mvn spring-boot:run 或 java -jar）。
 * 复现：mvn test -Dtest=SemanticSearchBenchmarkTest
 */
class SemanticSearchBenchmarkTest {

    private static final String BASE = "http://localhost:28080/api";
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void semanticSearchBenchmark() throws Exception {
        assumeServerUp();

        // 冷启动首轮（含 Redis 向量按需加载）
        JsonNode cold = search("动态规划");
        double coldMs = cold.get("ms").asDouble();

        // 热态多轮
        String[] queries = {"Redis 缓存", "Vue 组件", "MySQL 索引", "WebRTC", "动态规划"};
        List<Double> warm = new ArrayList<>();
        for (String q : queries) {
            JsonNode node = search(q);
            warm.add(node.get("ms").asDouble());
        }
        double avg = warm.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        System.out.printf("冷启动检索: %.0fms%n", coldMs);
        for (int i = 0; i < warm.size(); i++) {
            System.out.printf("热态检索[%s]: %.0fms%n", queries[i], warm.get(i));
        }
        System.out.printf("热态平均: %.0fms（含 HTTP 往返）%n", avg);

        // 交互可用性断言：热态平均延迟应低于 400ms（38 篇规模）
        assertTrue(avg < 400, "热态平均延迟应低于 400ms，实际 " + avg + "ms");
    }

    private JsonNode search(String q) throws Exception {
        String url = BASE + "/search/semantic?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8) + "&limit=5";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        long t0 = System.nanoTime();
        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        double ms = (System.nanoTime() - t0) / 1e6;
        JsonNode node = mapper.readTree(resp.body());
        assertEquals(200, node.get("code").asInt(), "语义检索应返回 200: " + url);
        // 把耗时并入结果节点，便于输出
        return ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("ms", ms);
    }

    private void assumeServerUp() throws Exception {
        boolean up;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE + "/articles/page?pageSize=1"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            up = resp.statusCode() == 200;
        } catch (Exception e) {
            up = false;
        }
        Assumptions.assumeTrue(up, "后端未启动（28080），跳过基准测试");
    }
}
