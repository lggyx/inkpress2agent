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
import java.util.List;
import java.util.Map;

/** Elasticsearch 低层 REST 客户端：健康检查、索引初始化、文档写入/删除、查询 */
@Component
public class EsClient {

    private static final Logger log = LoggerFactory.getLogger(EsClient.class);

    private final String baseUrl;
    private final String index;
    /** 检索单元（chunk）索引：文章级索引名 + "-chunks" */
    private final String chunkIndex;
    /** 文章级索引的向量维度缓存（-1 = 未知）：写向量前必须与之对齐，避免整篇文档被 ES 拒收 */
    private volatile int cachedIndexDims = -1;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public EsClient(@Value("${inkpress.es.host}") String host,
                    @Value("${inkpress.es.port}") int port,
                    @Value("${inkpress.es.index}") String index) {
        this.baseUrl = "http://" + host + ":" + port;
        this.index = index;
        this.chunkIndex = index + "-chunks";
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

    /** 启动时确保索引存在（IK 分词 + 按配置维度建向量字段；已存在的索引不动） */
    public void ensureIndex(int dims) {
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
                      "embedding":{"type":"dense_vector","dims":%d,"index":true,"similarity":"cosine"},
                      "publishedAt":{"type":"date"}
                    }}}""".formatted(dims);
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

    /**
     * 文章级索引的实际向量维度（读 _mapping，进程内缓存）。
     * 返回 -1 表示未知（ES 不可达/字段未建）——调用方应退回配置口径并显式告警。
     */
    public int indexDims() {
        if (cachedIndexDims > 0) {
            return cachedIndexDims;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/" + index + "/_mapping"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return -1;
            }
            JsonNode dims = objectMapper.readTree(resp.body())
                    .path(index).path("mappings").path("properties").path("embedding").path("dims");
            int d = dims.asInt(-1);
            if (d > 0) {
                cachedIndexDims = d;
            }
            return d;
        } catch (Exception e) {
            return -1;
        }
    }

    // ---------- chunk（检索单元）索引 ----------

    /** 启动时确保 chunk 索引存在（IK 分词，无向量——chunk 级只做 BM25 精排） */
    public void ensureChunkIndex() {
        try {
            HttpRequest head = HttpRequest.newBuilder(URI.create(baseUrl + "/" + chunkIndex))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            if (http.send(head, HttpResponse.BodyHandlers.ofString()).statusCode() == 200) {
                return;
            }
            String body = """
                    {"mappings":{"properties":{
                      "articleId":{"type":"long"},
                      "chunkIndex":{"type":"integer"},
                      "title":{"type":"text","analyzer":"ik_max_word","search_analyzer":"ik_smart"},
                      "content":{"type":"text","analyzer":"ik_max_word","search_analyzer":"ik_smart"},
                      "tags":{"type":"keyword"},
                      "publishedAt":{"type":"date"}
                    }}}""";
            HttpRequest create = HttpRequest.newBuilder(URI.create(baseUrl + "/" + chunkIndex))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> created = http.send(create, HttpResponse.BodyHandlers.ofString());
            if (created.statusCode() == 200 || created.statusCode() == 201) {
                log.info("ES chunk index created: {}", chunkIndex);
            } else {
                log.warn("ES chunk index create failed: {}", created.body());
            }
        } catch (Exception e) {
            log.warn("ES chunk index ensure failed: {}", e.getMessage());
        }
    }

    /**
     * 批量写 chunk 文档。docs 每项须含 chunkId（"{articleId}-{chunkIndex}"）与文档体。
     */
    public void chunkBulkIndex(List<Map<String, Object>> docs) throws IOException, InterruptedException {
        if (docs == null || docs.isEmpty()) {
            return;
        }
        StringBuilder ndjson = new StringBuilder();
        for (Map<String, Object> doc : docs) {
            String chunkId = String.valueOf(doc.remove("chunkId"));
            ndjson.append("{\"index\":{\"_index\":\"").append(chunkIndex).append("\",\"_id\":\"").append(chunkId).append("\"}}\n");
            ndjson.append(objectMapper.writeValueAsString(doc)).append('\n');
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/_bulk"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-ndjson")
                .POST(HttpRequest.BodyPublishers.ofString(ndjson.toString()))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("ES chunk bulk failed: " + resp.statusCode());
        }
        JsonNode root = objectMapper.readTree(resp.body());
        if (root.path("errors").asBoolean(false)) {
            log.warn("ES chunk bulk partial errors: {}", resp.body().length() > 500 ? resp.body().substring(0, 500) : resp.body());
        }
    }

    /** 删除某文章的全部 chunk（重建前清旧） */
    public void chunkDeleteByArticle(Long articleId) throws IOException, InterruptedException {
        String body = """
                {"query":{"term":{"articleId":%d}}}
                """.formatted(articleId);
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/" + chunkIndex + "/_delete_by_query"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("ES chunk delete_by_query failed: " + resp.statusCode());
        }
    }

    /** chunk 索引文档总数（语料规模的可验证数字） */
    public long chunkCount() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/" + chunkIndex + "/_count"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return -1;
            }
            return objectMapper.readTree(resp.body()).path("count").asLong(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    /** 文章级索引文档总数 */
    public long docCount() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/" + index + "/_count"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return -1;
            }
            return objectMapper.readTree(resp.body()).path("count").asLong(-1);
        } catch (Exception e) {
            return -1;
        }
    }

    /** chunk 索引检索（BM25，调用方自组 body） */
    public JsonNode chunkSearch(String body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/" + chunkIndex + "/_search"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("ES chunk search returned " + resp.statusCode());
        }
        return objectMapper.readTree(resp.body());
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
