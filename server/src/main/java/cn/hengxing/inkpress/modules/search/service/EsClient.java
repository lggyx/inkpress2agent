package cn.hengxing.inkpress.modules.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Elasticsearch 低层 REST 客户端：健康检查、索引初始化、文档写入/删除、查询 */
@Component
public class EsClient {

    private static final Logger log = LoggerFactory.getLogger(EsClient.class);

    private final String baseUrl;
    private final String index;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public EsClient(@Value("${inkpress.es.host}") String host,
                    @Value("${inkpress.es.port}") int port,
                    @Value("${inkpress.es.index}") String index) {
        this.baseUrl = "http://" + host + ":" + port;
        this.index = index;
    }

    public boolean healthy() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/_cluster/health"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            return http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** 启动时确保索引存在（IK 分词 + 1024 维向量） */
    public void ensureIndex() {
        try {
            HttpRequest head = HttpRequest.newBuilder(URI.create(baseUrl + "/" + index))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> resp = http.send(head, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return;
            }
            String body = """
                    {"mappings":{"properties":{
                      "title":{"type":"text","analyzer":"ik_max_word","search_analyzer":"ik_smart"},
                      "content":{"type":"text","analyzer":"ik_max_word","search_analyzer":"ik_smart"},
                      "tags":{"type":"keyword"},
                      "embedding":{"type":"dense_vector","dims":1024,"index":true,"similarity":"cosine"},
                      "publishedAt":{"type":"date"}
                    }}}""";
            HttpRequest create = HttpRequest.newBuilder(URI.create(baseUrl + "/" + index))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> created = http.send(create, HttpResponse.BodyHandlers.ofString());
            if (created.statusCode() == 200 || created.statusCode() == 201) {
                log.info("ES index created: {}", index);
            } else {
                log.warn("ES index create failed: {}", created.body());
            }
        } catch (Exception e) {
            log.warn("ES unavailable at startup, search degraded to MySQL LIKE: {}", e.getMessage());
        }
    }

    public void index(String id, Map<String, Object> doc) throws IOException, InterruptedException {
        put("/" + index + "/_doc/" + id, objectMapper.writeValueAsString(doc));
    }

    public void delete(String id) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/" + index + "/_doc/" + id))
                .timeout(Duration.ofSeconds(10)).DELETE().build();
        http.send(req, HttpResponse.BodyHandlers.discarding());
    }

    public JsonNode search(String body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/" + index + "/_search"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("ES search returned " + resp.statusCode());
        }
        return objectMapper.readTree(resp.body());
    }

    public String scrollAllIds(String body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/" + index + "/_search"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    private void put(String path, String body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200 && resp.statusCode() != 201) {
            throw new IOException("ES put failed: " + resp.statusCode());
        }
    }
}
