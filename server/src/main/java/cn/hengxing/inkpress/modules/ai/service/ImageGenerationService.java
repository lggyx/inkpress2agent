package cn.hengxing.inkpress.modules.ai.service;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.ai.service.AiConfigService;
import cn.hengxing.inkpress.modules.media.entity.MediaAsset;
import cn.hengxing.inkpress.modules.media.mapper.MediaAssetMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * M3 AI 配图：OpenAI 兼容 /images/generations（GLM cogview 等），
 * 返回 b64_json 优先解码，否则回 url 再下载；产物落 media_asset（媒体流），返回站内 /api/media/{id}。
 */
@Service
public class ImageGenerationService {

    /** 默认模型（image_model 未配置时） */
    private static final String DEFAULT_IMAGE_MODEL = "cogview-3-flash";
    /** GLM cogview 支持的尺寸白名单 */
    private static final Pattern SIZE_PATTERN = Pattern.compile("^(1024x1024|768x1344|864x1152|1344x768|1152x864|1440x720|720x1440)$");
    private static final long MAX_BYTES = 15L * 1024 * 1024;

    private final AiConfigService configService;
    private final MediaAssetMapper mediaAssetMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public ImageGenerationService(AiConfigService configService, MediaAssetMapper mediaAssetMapper) {
        this.configService = configService;
        this.mediaAssetMapper = mediaAssetMapper;
    }

    /** 生成并落库；返回 mediaId 与站内 URL（独立 image 能力槽：可单独配 OpenAI 兼容图像服务商，与文本协议互不影响） */
    public Map<String, Object> generateAndStore(Long userId, String prompt, String size) {
        AiConfigService.AiProvider p = configService.provider(AiConfigService.CAP_IMAGE);
        if (!configService.configured(AiConfigService.CAP_IMAGE)) {
            throw BizException.of(3004, "图像生成未配置，请管理员在 AI 设置的「图像生成」槽填写（需 OpenAI 兼容图像服务）");
        }
        if (AiConfigService.PROTOCOL_ANTHROPIC.equals(p.protocol())) {
            throw BizException.of(3006, "Anthropic 协议无图像生成接口；请在「图像生成」槽单独配置 OpenAI 兼容图像服务（协议选 openai）");
        }
        String sizeValue = size == null || size.isBlank() ? "1024x1024" : size;
        if (!SIZE_PATTERN.matcher(sizeValue).matches()) {
            throw BizException.of(3005, "不支持的图片尺寸，可选：1024x1024、768x1344、864x1152、1344x768、1152x864、1440x720、720x1440");
        }
        String model = p.model() == null || p.model().isBlank() ? DEFAULT_IMAGE_MODEL : p.model();
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "prompt", prompt,
                    "size", sizeValue));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(p.baseUrl() + "/images/generations"))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + p.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        JsonNode item;
        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String detail = resp.body() == null ? "" : resp.body().replaceAll("\\s+", " ").trim();
                if (detail.length() > 220) {
                    detail = detail.substring(0, 220) + "…";
                }
                throw BizException.of(3003, "图像模型服务返回 " + resp.statusCode()
                        + (detail.isBlank() ? "" : "：" + detail));
            }
            JsonNode data = objectMapper.readTree(resp.body()).path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw BizException.of(3002, "图像模型未返回图片");
            }
            item = data.get(0);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of(3003, "AI 调用失败：" + e.getMessage());
        }
        byte[] bytes = resolveBytes(item);
        MediaAsset asset = new MediaAsset();
        asset.setKind(MediaAsset.KIND_AI_IMAGE);
        asset.setMime("image/png");
        asset.setByteSize(bytes.length);
        asset.setContent(bytes);
        asset.setCreatedBy(userId);
        mediaAssetMapper.insert(asset);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mediaId", asset.getId());
        result.put("url", "/api/media/" + asset.getId());
        result.put("mime", asset.getMime());
        result.put("byteSize", asset.getByteSize());
        return result;
    }

    /** b64_json 优先，否则回 url 下载；缺失即协议异常 */
    private byte[] resolveBytes(JsonNode item) {
        String b64 = item.path("b64_json").asText(null);
        if (b64 != null && !b64.isBlank()) {
            try {
                return Base64.getDecoder().decode(b64);
            } catch (IllegalArgumentException e) {
                throw BizException.of(3002, "图像 b64 数据异常");
            }
        }
        String url = item.path("url").asText(null);
        if (url == null || url.isBlank()) {
            throw BizException.of(3002, "图像模型返回缺少图片数据");
        }
        try {
            HttpResponse<byte[]> resp = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200 || resp.body() == null || resp.body().length == 0) {
                throw BizException.of(3003, "图片下载失败：" + resp.statusCode());
            }
            if (resp.body().length > MAX_BYTES) {
                throw BizException.of(3002, "图片过大，超出媒体上限");
            }
            return resp.body();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.of(3003, "图片下载失败：" + e.getMessage());
        }
    }
}
