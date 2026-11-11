package cn.hengxing.inkpress.modules.media.controller;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.auth.support.JwtAuthFilter;
import cn.hengxing.inkpress.modules.media.entity.MediaAsset;
import cn.hengxing.inkpress.modules.media.mapper.MediaAssetMapper;
import cn.hengxing.inkpress.modules.media.support.OssStorageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 媒体上传（登录可用）：文件经服务器中转上传到云 OSS（S3 兼容），
 * 公网外链入库（media_asset.storage=OSS），前端直接渲染外链；视频同流程。
 */
@RestController
public class MediaUploadController {

    private static final long MAX_BYTES = 200L * 1024 * 1024;

    private final OssStorageService ossStorageService;
    private final MediaAssetMapper mediaAssetMapper;

    public MediaUploadController(OssStorageService ossStorageService, MediaAssetMapper mediaAssetMapper) {
        this.ossStorageService = ossStorageService;
        this.mediaAssetMapper = mediaAssetMapper;
    }

    @PostMapping("/api/media/upload")
    public Result<Map<String, Object>> upload(@AuthenticationPrincipal JwtAuthFilter.LoginUser user,
                                              @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.of(2402, "文件为空");
        }
        if (file.getSize() > MAX_BYTES) {
            throw BizException.of(2403, "文件超过 200MB 上限");
        }
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        String kind = mime.startsWith("video/") ? "video" : mime.startsWith("image/") ? "image" : null;
        if (kind == null) {
            throw BizException.of(2404, "仅支持图片或视频文件");
        }
        if (!ossStorageService.configured()) {
            throw BizException.of(3008, "对象存储未配置，请管理员在系统设置中填写 OSS 信息");
        }
        String ext = extOf(file.getOriginalFilename());
        String key = ossStorageService.objectKey(ext);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw BizException.of(2405, "文件读取失败");
        }
        String url = ossStorageService.put(key, bytes, mime);

        MediaAsset asset = new MediaAsset();
        asset.setKind(MediaAsset.KIND_UPLOAD);
        asset.setMime(mime);
        asset.setByteSize((int) file.getSize());
        asset.setStorage(MediaAsset.STORAGE_OSS);
        asset.setUrl(url);
        asset.setCreatedBy(user.id());
        mediaAssetMapper.insert(asset);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mediaId", asset.getId());
        out.put("url", url);
        out.put("mime", mime);
        out.put("kind", kind);
        out.put("byteSize", asset.getByteSize());
        return Result.ok(out);
    }

    private String extOf(String name) {
        if (name == null || !name.contains(".")) {
            return "";
        }
        return name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }
}
