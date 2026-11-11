package cn.hengxing.inkpress.modules.messaging.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("email_digest_log")
public class EmailDigestLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate weekStart;
    private LocalDateTime sentAt;

    public EmailDigestLog() {
    }

    public EmailDigestLog(Long userId, LocalDate weekStart) {
        this.userId = userId;
        this.weekStart = weekStart;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getWeekStart() { return weekStart; }
    public LocalDateTime getSentAt() { return sentAt; }

    public void setId(Long id) { this.id = id; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}
