package com.inkpress.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 服务配置管理测试（对应论文 5.x 节 AI 可插拔适配层的管理端设计）：
 * 鉴权 → 获取（脱敏）→ 保存（含密钥保持/清除语义）→ 参数校验 → 恢复默认 → 操作审计。
 * 前置条件：后端已启动；ai_config 表已存在（init.sql 自动建表）。
 * 复现：mvn test -Dtest=AiConfigFlowTest
 * 清理：@AfterAll 恢复默认（删除配置行），避免影响其他用例的“无密钥”预期。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiConfigFlowTest {

    private static final String BASE = "http://localhost:28080/api";
    private static final String TEST_KEY = "junit-test-key-abcdef987654";
    private static final String MASKED = "junit-****7654";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    private static String adminToken;

    @Test
    @Order(1)
    void adminLogin() throws Exception {
        assumeServerUp();
        JsonNode node = post("/auth/login",
                "{\"username\":\"admin\",\"password\":\"admin123\"}", null);
        assertEquals(200, node.get("code").asInt(), "管理员登录应成功");
        adminToken = node.get("data").get("token").asText();
        assertTrue(adminToken.length() > 20, "应返回有效令牌");
    }

    @Test
    @Order(2)
    void configRequiresAuth() throws Exception {
        assumeToken();
        JsonNode node = get("/admin/ai-config", null);
        assertEquals(401, node.get("code").asInt(), "无 token 获取配置应返回 401");
    }

    @Test
    @Order(3)
    void saveAndMask() throws Exception {
        assumeToken();
        JsonNode saved = put("/admin/ai-config", """
                {"baseUrl":"https://open.bigmodel.cn/api/paas/v4",
                 "apiKey":"%s","model":"glm-4-flash","temperature":0.7,
                 "timeoutSeconds":60,"embeddingProvider":"local",
                 "embeddingModel":"embedding-3","localDim":512}
                """.formatted(TEST_KEY));
        assertEquals(200, saved.get("code").asInt(), "保存配置应成功");

        JsonNode cfg = get("/admin/ai-config", adminToken);
        assertEquals(200, cfg.get("code").asInt());
        JsonNode data = cfg.get("data");
        assertEquals("db", data.get("source").asText(), "保存后配置来源应为 db");
        assertTrue(data.get("hasApiKey").asBoolean(), "应标记已配置密钥");
        assertEquals(MASKED, data.get("apiKeyMasked").asText(), "密钥应脱敏回显");
        assertFalse(data.toString().contains(TEST_KEY), "任何字段不得泄露完整密钥");
        assertEquals("本地特征哈希（512 维）", data.get("embeddingImpl").asText(),
                "local 模式应回显本地实现");
    }

    @Test
    @Order(4)
    void omitApiKeyKeepsExisting() throws Exception {
        assumeToken();
        // 省略 apiKey 字段：其余字段更新，密钥保持
        JsonNode saved = put("/admin/ai-config", """
                {"baseUrl":"https://open.bigmodel.cn/api/paas/v4",
                 "model":"glm-4-air","temperature":0.5,"timeoutSeconds":30,
                 "embeddingProvider":"local","embeddingModel":"embedding-3","localDim":512}
                """);
        assertEquals(200, saved.get("code").asInt());
        JsonNode data = get("/admin/ai-config", adminToken).get("data");
        assertEquals(MASKED, data.get("apiKeyMasked").asText(), "省略 apiKey 应保持原密钥");
        assertEquals("glm-4-air", data.get("model").asText(), "其余字段应已更新");
    }

    @Test
    @Order(5)
    void emptyApiKeyClears() throws Exception {
        assumeToken();
        JsonNode saved = put("/admin/ai-config", """
                {"baseUrl":"https://open.bigmodel.cn/api/paas/v4","apiKey":"",
                 "model":"glm-4-flash","temperature":0.7,"timeoutSeconds":60,
                 "embeddingProvider":"local","embeddingModel":"embedding-3","localDim":512}
                """);
        assertEquals(200, saved.get("code").asInt());
        JsonNode data = get("/admin/ai-config", adminToken).get("data");
        assertFalse(data.get("hasApiKey").asBoolean(), "空串应清除密钥");
        assertEquals("", data.get("apiKeyMasked").asText(), "清除后脱敏串为空");
    }

    @Test
    @Order(6)
    void invalidParamsRejected() throws Exception {
        assumeToken();
        JsonNode bad = put("/admin/ai-config", """
                {"baseUrl":"ftp://invalid","apiKey":"x","model":"m","temperature":0.7,
                 "timeoutSeconds":60,"embeddingProvider":"auto","embeddingModel":"e","localDim":512}
                """);
        assertEquals(400, bad.get("code").asInt(), "非法 base-url 应返回 400");
        JsonNode badProvider = put("/admin/ai-config", """
                {"baseUrl":"https://x.com/v1","apiKey":"x","model":"m","temperature":0.7,
                 "timeoutSeconds":60,"embeddingProvider":"quantum","embeddingModel":"e","localDim":512}
                """);
        assertEquals(400, badProvider.get("code").asInt(), "非法向量化方式应返回 400");
    }

    @Test
    @Order(7)
    void testConnectionDegrades() throws Exception {
        assumeToken();
        // 测试密钥无效：接口应返回 success=false 与耗时，而不是异常
        put("/admin/ai-config", """
                {"baseUrl":"https://open.bigmodel.cn/api/paas/v4","apiKey":"%s",
                 "model":"glm-4-flash","temperature":0.7,"timeoutSeconds":10,
                 "embeddingProvider":"local","embeddingModel":"embedding-3","localDim":512}
                """.formatted(TEST_KEY));
        JsonNode result = post("/admin/ai-config/test", null, adminToken);
        assertEquals(200, result.get("code").asInt());
        JsonNode data = result.get("data");
        assertNotNull(data.get("success"), "应返回 success 字段");
        assertFalse(data.get("success").asBoolean(), "无效密钥应连接失败");
        assertTrue(data.get("latencyMs").asLong() >= 0, "应返回耗时");
    }

    @Test
    @Order(8)
    void operationLogRecorded() throws Exception {
        assumeToken();
        JsonNode logs = get("/admin/logs?pageSize=10", adminToken);
        assertEquals(200, logs.get("code").asInt());
        boolean found = false;
        for (JsonNode record : logs.get("data").get("records")) {
            if ("AI 服务配置".equals(record.get("module").asText())
                    && "修改配置".equals(record.get("operation").asText())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "操作日志应记录 AI 配置修改");
    }

    @Test
    @Order(9)
    void resetFallsBackToEnv() throws Exception {
        assumeToken();
        JsonNode reset = delete("/admin/ai-config");
        assertEquals(200, reset.get("code").asInt(), "恢复默认应成功");
        JsonNode data = get("/admin/ai-config", adminToken).get("data");
        assertEquals("env", data.get("source").asText(), "恢复默认后应回落环境变量");
    }

    @AfterAll
    static void cleanup() {
        if (adminToken == null) {
            return;
        }
        try {
            AiConfigFlowTest t = new AiConfigFlowTest();
            t.delete("/admin/ai-config");
        } catch (Exception ignored) {
            // 清理失败不影响测试结论
        }
    }

    // ---------- HTTP 工具 ----------

    private JsonNode post(String path, String json, String token) throws Exception {
        return send("POST", path, json, token);
    }

    private JsonNode put(String path, String json) throws Exception {
        return send("PUT", path, json, adminToken);
    }

    private JsonNode get(String path, String token) throws Exception {
        return send("GET", path, null, token);
    }

    private JsonNode delete(String path) throws Exception {
        return send("DELETE", path, null, adminToken);
    }

    private JsonNode send(String method, String path, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(20));
        if (json != null) {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return mapper.readTree(resp.body());
    }

    private void assumeToken() {
        Assumptions.assumeTrue(adminToken != null, "前置登录未通过，跳过本用例");
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
        Assumptions.assumeTrue(up, "后端未启动（28080），跳过配置管理测试");
    }
}
