package cn.hengxing.inkpress.modules.media.controller;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.ai.service.AiConfigService;
import cn.hengxing.inkpress.modules.auth.support.JwtAuthFilter;
import cn.hengxing.inkpress.modules.ai.entity.SystemConfig;
import cn.hengxing.inkpress.modules.ai.mapper.SystemConfigMapper;
import cn.hengxing.inkpress.modules.media.support.OssStorageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 对象存储 OSS 配置（S3 兼容）：七牛/阿里云 OSS/腾讯 COS/MinIO 等，密钥脱敏回显 */
@RestController
@RequestMapping("/api/admin/oss-config")
@PreAuthorize("hasRole('ADMIN')")
public class OssAdminController {

    public record OssUpdate(String endpoint, String region, String bucket, String accessKey,
                            String secretKey, String publicBaseUrl, String pathStyle) {
    }

    private final SystemConfigMapper systemConfigMapper;
    private final OssStorageService ossStorageService;
    private final AiConfigService aiConfigService;

    public OssAdminController(SystemConfigMapper systemConfigMapper, OssStorageService ossStorageService,
                              AiConfigService aiConfigService) {
        this.systemConfigMapper = systemConfigMapper;
        this.ossStorageService = ossStorageService;
        this.aiConfigService = aiConfigService;
    }

    @GetMapping
    public Result<Map<String, Object>> view() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("endpoint", aiConfigService.get("oss.endpoint"));
        view.put("region", aiConfigService.get("oss.region"));
        view.put("bucket", aiConfigService.get("oss.bucket"));
        view.put("accessKeyMasked", mask(aiConfigService.get("oss.access_key")));
        view.put("secretKeyMasked", mask(aiConfigService.get("oss.secret_key")));
        view.put("publicBaseUrl", aiConfigService.get("oss.public_base_url"));
        view.put("pathStyle", aiConfigService.get("oss.path_style"));
        view.put("configured", ossStorageService.configured());
        return Result.ok(view);
    }

    private String mask(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.length() <= 8) {
            return key.charAt(0) + "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    @PutMapping
    public Result<Void> update(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                               @RequestBody OssUpdate req) {
        put(user.id(), "oss.endpoint", req.endpoint());
        put(user.id(), "oss.region", req.region());
        put(user.id(), "oss.bucket", req.bucket());
        // AK：null=不改；空串=清除；含掩码（****）=前端回显误传，拒绝落库；其余更新
        if (req.accessKey() != null && !req.accessKey().contains("****")) {
            if (req.accessKey().isBlank()) {
                systemConfigMapper.deleteById("oss.access_key");
            } else {
                put(user.id(), "oss.access_key", req.accessKey());
            }
        }
        put(user.id(), "oss.public_base_url", req.publicBaseUrl());
        put(user.id(), "oss.path_style", req.pathStyle());
        if (req.secretKey() != null) {
            if (req.secretKey().isBlank()) {
                systemConfigMapper.deleteById("oss.secret_key");
            } else {
                put(user.id(), "oss.secret_key", req.secretKey());
            }
        }
        return Result.ok();
    }

    /** 测试连接：确保桶存在（无则创建）并写入探测对象 */
    @PostMapping("/test")
    public Result<Map<String, Boolean>> test() {
        return Result.ok(Map.of("ok", ossStorageService.test()));
    }

    private void put(Long operatorId, String key, String value) {
        if (value == null) {
            return;
        }
        SystemConfig sc = new SystemConfig();
        sc.setConfigKey(key);
        sc.setConfigValue(value.isBlank() ? "" : value.trim());
        sc.setUpdatedBy(operatorId);
        if (systemConfigMapper.selectById(key) == null) {
            systemConfigMapper.insert(sc);
        } else {
            systemConfigMapper.updateById(sc);
        }
    }
}
