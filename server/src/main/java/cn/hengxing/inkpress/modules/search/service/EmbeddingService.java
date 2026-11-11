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

    /** 文本转向量；失败返回 null（调用方降级为 BM25）；独立 embedding 能力槽，Anthropic 协议无此接口直接降级 */
    public List<Double> embed(String text) {
        if (text == null || text.isBlank() || !available()) {
            return null;
        }
        AiConfigService.AiProvider p = configService.provider(AiConfigService.CAP_EMBEDDING);
        if (AiConfigService.PROTOCOL_ANTHROPIC.equals(p.protocol())
                || p.baseUrl() == null || p.baseUrl().isBlank() || p.apiKey() == null || p.apiKey().isBlank()) {
            return null;
        }
        try {
            String model = p.model() == null || p.model().isBlank() ? "embedding-3" : p.model();
            // 维度默认 1024，与 ES 索引 dense_vector 一致；智谱 embedding-3 等支持 dimensions 参数
            int dims = dims();
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "input", text.length() > 2000 ? text.substring(0, 2000) : text,
                    "dimensions", dims));
            String base = p.baseUrl().endsWith("/") ? p.baseUrl().substring(0, p.baseUrl().length() - 1) : p.baseUrl();
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/embeddings"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + p.apiKey())
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
            // 维度与 ES 索引不符直接降级（避免写入被拒后文档缺向量）
            if (vector.size() != dims()) {
                return null;
            }
            return vector;
        } catch (Exception e) {
            return null;
        }
    }

    /** 向量维度：默认 1024（与 ES 索引 dense_vector 一致），可经 ai.embedding.dimensions 配置 */
    public int dims() {
        String d = configService.get("ai.embedding.dimensions");
        try {
            return d == null || d.isBlank() ? 1024 : Integer.parseInt(d.trim());
        } catch (Exception e) {
            return 1024;
        }
    }
}
