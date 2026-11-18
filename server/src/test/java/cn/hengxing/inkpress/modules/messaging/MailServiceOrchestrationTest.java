package cn.hengxing.inkpress.modules.messaging;

import cn.hengxing.inkpress.modules.auth.entity.User;
import cn.hengxing.inkpress.modules.auth.mapper.UserMapper;
import cn.hengxing.inkpress.modules.messaging.entity.EmailDigestLog;
import cn.hengxing.inkpress.modules.messaging.mapper.EmailDigestLogMapper;
import cn.hengxing.inkpress.modules.messaging.service.MailService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邮件编排层单测（Mockito）：SMTP 半配置守卫降级日志、真实发送与失败进 DLQ 语义、
 * 未知消息类型丢弃、每周精选去重、退订令牌生成/复用/退订闭环。
 */
class MailServiceOrchestrationTest {

    private RabbitTemplate rabbitTemplate;
    private UserMapper userMapper;
    private EmailDigestLogMapper digestLogMapper;
    private JavaMailSender mailSender;

    private MailService mailService(String host, String password) {
        rabbitTemplate = mock(RabbitTemplate.class);
        userMapper = mock(UserMapper.class);
        digestLogMapper = mock(EmailDigestLogMapper.class);
        mailSender = mock(JavaMailSender.class);
        return new MailService(rabbitTemplate, userMapper, digestLogMapper, mailSender,
                host, password, "伊格 <blog@example.com>", "http://127.0.0.1:80");
    }

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, User.class);
    }

    private static Map<String, Object> commentMsg() {
        return Map.of("kind", "comment", "to", "reader@example.com", "nickname", "读者",
                "subject", "评论提醒", "content", "有新评论", "url", "http://127.0.0.1:80/article/1",
                "unsubUrl", "http://127.0.0.1:80/api/unsubscribe?token=t");
    }

    @Test
    void unconfiguredSmtpFallsBackToLogWithoutSend() {
        // 半配置（只配主机没配密钥）与未配置（smtp.local）都必须降级，不得真发
        for (String[] cfg : new String[][]{{"", ""}, {"smtp.163.com", ""}, {"smtp.local", "secret"}}) {
            MailService svc = mailService(cfg[0], cfg[1]);
            svc.onMessage(commentMsg());
            verify(mailSender, never()).send(any(SimpleMailMessage.class));
        }
    }

    @Test
    void configuredSmtpSendsViaMailSender() {
        MailService svc = mailService("smtp.example.com", "secret");
        svc.onMessage(commentMsg());
        ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(cap.capture());
        assertEquals("reader@example.com", cap.getValue().getTo()[0]);
        assertEquals("评论提醒", cap.getValue().getSubject());
        assertTrue(cap.getValue().getText().contains("每周精选订阅"), "评论邮件正文应带每周精选退订指引");
        assertEquals("伊格 <blog@example.com>", cap.getValue().getFrom());
    }

    @Test
    void sendFailureRaisesRejectAndDontRequeue() {
        MailService svc = mailService("smtp.example.com", "secret");
        doThrow(new RuntimeException("connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> svc.onMessage(commentMsg()));
    }

    @Test
    void unknownMessageKindIsDiscardedSilently() {
        MailService svc = mailService("smtp.example.com", "secret");
        svc.onMessage(Map.of("kind", "marketing", "to", "someone@example.com"));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void digestDuplicateWeekIsSkipped() {
        MailService svc = mailService("smtp.example.com", "secret");
        doThrow(new DuplicateKeyException("uk_user_week")).when(digestLogMapper).insert(any(EmailDigestLog.class));

        svc.onMessage(Map.of("kind", "digest", "userId", "9", "weekStart", "2026-11-09",
                "to", "reader@example.com", "nickname", "读者", "items", List.of()));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void digestNewWeekSendsWeeklyMail() {
        MailService svc = mailService("smtp.example.com", "secret");
        svc.onMessage(Map.of("kind", "digest", "userId", "9", "weekStart", LocalDate.of(2026, 11, 9).toString(),
                "to", "reader@example.com", "nickname", "读者",
                "items", List.of(Map.of("title", "热文标题", "views", "123", "url", "http://x/1"))));

        ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(cap.capture());
        assertTrue(cap.getValue().getSubject().contains("每周精选"));
        assertTrue(cap.getValue().getText().contains("热文标题"));
    }

    @Test
    void unsubTokenReusedWhenPresent() {
        MailService svc = mailService("", "");
        User user = new User();
        user.setId(9L);
        user.setDigestUnsubToken("existing-token");

        assertEquals("existing-token", svc.ensureUnsubToken(user));
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void unsubTokenGeneratedAndPersistedOnce() {
        MailService svc = mailService("", "");
        User user = new User();
        user.setId(9L);

        String first = svc.ensureUnsubToken(user);
        String second = svc.ensureUnsubToken(user);
        assertEquals(first, second);
        assertEquals(32, second.length());
        verify(userMapper).updateById(user);
    }

    @Test
    void unsubscribeByTokenClosesSubscription() {
        MailService svc = mailService("", "");
        assertFalse(svc.unsubscribeByToken(null));
        assertFalse(svc.unsubscribeByToken("  "));

        when(userMapper.selectOne(any())).thenReturn(null);
        assertFalse(svc.unsubscribeByToken("unknown"));

        User user = new User();
        user.setId(9L);
        user.setDigestSubscribed(1);
        when(userMapper.selectOne(any())).thenReturn(user);
        assertTrue(svc.unsubscribeByToken("tok"));
        assertEquals(0, user.getDigestSubscribed().intValue());
    }

    @Test
    void commentAlertSkipsUserWithoutActiveEmail() {
        MailService svc = mailService("", "");
        User noEmail = new User();
        noEmail.setId(3L);
        noEmail.setStatus("ACTIVE");
        when(userMapper.selectById(3L)).thenReturn(noEmail);
        User inactive = new User();
        inactive.setId(4L);
        inactive.setStatus("BANNED");
        inactive.setEmail("b@example.com");
        when(userMapper.selectById(4L)).thenReturn(inactive);

        svc.publishCommentAlert(3L, "t", "c", "/article/1");
        svc.publishCommentAlert(4L, "t", "c", "/article/1");
        svc.publishCommentAlert(999L, "t", "c", "/article/1");

        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(Object.class));
    }

    @Test
    void commentAlertEnqueuesWithUnsubscribeLink() {
        MailService svc = mailService("", "");
        User user = new User();
        user.setId(3L);
        user.setStatus("ACTIVE");
        user.setEmail("r@example.com");
        user.setNickname("读者");
        user.setDigestUnsubToken("tok123");
        when(userMapper.selectById(3L)).thenReturn(user);

        svc.publishCommentAlert(3L, "评论提醒", "内容", "/article/1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(rabbitTemplate).convertAndSend(
                eq(cn.hengxing.inkpress.modules.messaging.support.MailRabbitConfig.MAIL_EXCHANGE),
                eq(cn.hengxing.inkpress.modules.messaging.support.MailRabbitConfig.RK_COMMENT),
                cap.capture());
        assertEquals("r@example.com", cap.getValue().get("to"));
        assertTrue(String.valueOf(cap.getValue().get("unsubUrl")).contains("token=tok123"));
    }
}
