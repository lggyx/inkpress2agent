package cn.hengxing.inkpress.modules.media.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("media_asset")
public class MediaAsset {

    public static final String KIND_AI_IMAGE = "AI_IMAGE";
    public static final String KIND_UPLOAD = "UPLOAD";
    /** 存储位置：LOCAL 本地字节（AI 配图）/ OSS 云对象存储外链（上传与视频） */
    public static final String STORAGE_LOCAL = "LOCAL";
    public static final String STORAGE_OSS = "OSS";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String kind;
    private String mime;
    private Integer byteSize;
    private byte[] content;
    /** LOCAL / OSS */
    private String storage;
    /** 公网访问链接（storage=OSS 时有值） */
    private String url;
    private Long createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getKind() { return kind; }
    public String getMime() { return mime; }
    public Integer getByteSize() { return byteSize; }
    public byte[] getContent() { return content; }
    public String getStorage() { return storage; }
    public String getUrl() { return url; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setKind(String kind) { this.kind = kind; }
    public void setMime(String mime) { this.mime = mime; }
    public void setByteSize(Integer byteSize) { this.byteSize = byteSize; }
    public void setContent(byte[] content) { this.content = content; }
    public void setStorage(String storage) { this.storage = storage; }
    public void setUrl(String url) { this.url = url; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
