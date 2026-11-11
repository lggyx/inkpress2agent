package cn.hengxing.inkpress.modules.media.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("media_asset")
public class MediaAsset {

    public static final String KIND_AI_IMAGE = "AI_IMAGE";
    public static final String KIND_UPLOAD = "UPLOAD";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String kind;
    private String mime;
    private Integer byteSize;
    private byte[] content;
    private Long createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getKind() { return kind; }
    public String getMime() { return mime; }
    public Integer getByteSize() { return byteSize; }
    public byte[] getContent() { return content; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setKind(String kind) { this.kind = kind; }
    public void setMime(String mime) { this.mime = mime; }
    public void setByteSize(Integer byteSize) { this.byteSize = byteSize; }
    public void setContent(byte[] content) { this.content = content; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
