package com.inkpress.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkpress.common.BizException;
import com.inkpress.common.ErrorCode;
import com.inkpress.config.AiProperties;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 远程向量实现：调用 OpenAI 兼容的 /embeddings 接口。
 * 与聊天适配器同理，通过 base-url / api-key / model 即可切换厂商。
 */
@Slf4j
public class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiCompatibleEmbeddingProvider(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public double[] embed(String text) {
        String input = text == null ? "" : text.substring(0, Math.min(text.length(), 4000));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getEmbedding().getModel());
        body.put("input", input);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/embeddings"))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Embedding 接口返回异常: {} {}", response.statusCode(),
                        response.body() == null ? "" : response.body().substring(0, Math.min(200, response.body().length())));
                throw new BizException(ErrorCode.AI_CALL_FAILED);
            }
            JsonNode array = objectMapper.readTree(response.body())
                    .path("data").path(0).path("embedding");
            if (!array.isArray() || array.isEmpty()) {
                throw new BizException(ErrorCode.AI_RESPONSE_INVALID);
            }
            double[] vector = new double[array.size()];
            for (int i = 0; i < array.size(); i++) {
                vector[i] = array.get(i).asDouble();
            }
            return l2Normalize(vector);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Embedding 调用失败", e);
            throw new BizException(ErrorCode.AI_CALL_FAILED);
        }
    }

    private double[] l2Normalize(double[] vector) {
        double norm = 0;
        for (double v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    @Override
    public String name() {
        return "remote-" + properties.getEmbedding().getModel();
    }
}
