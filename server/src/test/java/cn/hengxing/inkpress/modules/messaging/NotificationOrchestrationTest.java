package cn.hengxing.inkpress.modules.messaging;

import cn.hengxing.inkpress.modules.messaging.entity.Notification;
import cn.hengxing.inkpress.modules.messaging.mapper.NotificationMapper;
import cn.hengxing.inkpress.modules.messaging.service.MailService;
import cn.hengxing.inkpress.modules.messaging.service.NotificationService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 站内通知编排层单测（Mockito）：落库 + 邮件提醒联动（评论/回复类才触发）、
 * 邮件失败不影响站内通知主链路。
 */
class NotificationOrchestrationTest {

    private NotificationMapper notificationMapper;
    private MailService mailService;
    private NotificationService service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, Notification.class);
    }

    @BeforeEach
    void setUp() {
        notificationMapper = mock(NotificationMapper.class);
        mailService = mock(MailService.class);
        service = new NotificationService(notificationMapper, mailService);
    }

    private static void fill(Notification n) {
        n.setId(1L);
        n.setCreatedAt(LocalDateTime.of(2026, 11, 17, 12, 0));
    }

    @Test
    void notifyUserPersistsAndTriggersCommentMail() {
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);

        service.notifyUser(9L, Notification.TYPE_COMMENT, "有人评论了", "内容", "/article/1");

        verify(notificationMapper).insert(cap.capture());
        assertEquals(9L, cap.getValue().getUserId().longValue());
        verify(mailService).publishCommentAlert(eq(9L), anyString(), anyString(), eq("/article/1"));
    }

    @Test
    void replyTypeAlsoTriggersMail() {
        service.notifyUser(9L, Notification.TYPE_REPLY, "有人回复", "内容", "/article/1");
        verify(mailService).publishCommentAlert(eq(9L), anyString(), anyString(), anyString());
    }

    @Test
    void reviewTypeSkipsMail() {
        service.notifyUser(9L, Notification.TYPE_REVIEW, "复审结果", "内容", "/studio/articles");
        verify(mailService, never()).publishCommentAlert(any(), anyString(), anyString(), anyString());
    }

    @Test
    void mailFailureDoesNotBreakNotificationChain() {
        doThrow(new RuntimeException("MQ 不可达")).when(mailService)
                .publishCommentAlert(any(), anyString(), anyString(), anyString());

        service.notifyUser(9L, Notification.TYPE_COMMENT, "有人评论了", "内容", "/article/1");

        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    void unreadCountsUnreadOnly() {
        when(notificationMapper.selectCount(any())).thenReturn(3L);
        assertEquals(3L, service.unread(9L));
    }
}
