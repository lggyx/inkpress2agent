package cn.hengxing.inkpress.modules.media.controller;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.media.entity.MediaAsset;
import cn.hengxing.inkpress.modules.media.mapper.MediaAssetMapper;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/** 媒体公开读取（游客可读全站）：AI 配图等二进制资产按 MIME 原样返回 */
@RestController
public class MediaController {

    private final MediaAssetMapper mediaAssetMapper;

    public MediaController(MediaAssetMapper mediaAssetMapper) {
        this.mediaAssetMapper = mediaAssetMapper;
    }

    @GetMapping("/api/media/{id}")
    public ResponseEntity<byte[]> get(@PathVariable Long id) {
        MediaAsset asset = mediaAssetMapper.selectById(id);
        if (asset == null) {
            throw BizException.of(2401, "媒体不存在");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getMime()))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(asset.getContent());
    }
}
