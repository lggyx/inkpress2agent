package cn.hengxing.inkpress.modules.search.service;

import cn.hengxing.inkpress.modules.ai.service.AiConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 语义向量：调用远程 Embedding API（GLM embedding-3 等 OpenAI 兼容协议） */
@Service
public class EmbeddingService {

    private final AiConfigService configService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public EmbeddingService(AiConfigService configService) {
        this.configService = configService;
    }

    public boolean available() {
        return configService.isConfigured();
    }

    /** 文本转向量；失败返回 null（调用方降级为 BM25）；Anthropic 协议无 embeddings 接口，直接降级 */
    public List<Double> embed(String text) {
        if (text == null || text.isBlank() || !available()
                || AiConfigService.PROTOCOL_ANTHROPIC.equals(configService.protocol())) {
            return null;
        }
        try {
            String model = configService.get("ai.embedding_model");
            if (model == null || model.isBlank()) {
                model = "embedding-3";
            }
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "input", text.length() > 2000 ? text.substring(0, 2000) : text));
            String base = configService.get(AiConfigService.KEY_BASE_URL);
            base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/embeddings"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + configService.get(AiConfigService.KEY_API_KEY))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            JsonNode data = objectMapper.readTree(resp.body()).path("data");
            if (!data.isArray() || data.isEmpty()) {
                return null;
            }
            List<Double> vector = new ArrayList<>();
            for (JsonNode v : data.get(0).path("embedding")) {
                vector.add(v.asDouble());
            }
            return vector;
        } catch (Exception e) {
            return null;
        }
    }
}
