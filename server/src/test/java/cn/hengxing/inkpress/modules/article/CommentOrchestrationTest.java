package cn.hengxing.inkpress.modules.article;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.modules.article.entity.Article;
import cn.hengxing.inkpress.modules.article.entity.Comment;
import cn.hengxing.inkpress.modules.article.mapper.ArticleMapper;
import cn.hengxing.inkpress.modules.article.mapper.CommentMapper;
import cn.hengxing.inkpress.modules.article.service.CommentService;
import cn.hengxing.inkpress.modules.article.support.SensitiveWordService;
import cn.hengxing.inkpress.modules.auth.entity.User;
import cn.hengxing.inkpress.modules.auth.mapper.UserMapper;
import cn.hengxing.inkpress.modules.messaging.service.NotificationService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论编排层单测（Mockito）：敏感词转人工审核、直发计数与作者通知、
 * 自评不通知、审核状态机与删评减计数、被回复评论缺失校验。
 */
class CommentOrchestrationTest {

    private static final long AUTHOR = 9L;
    private static final long VISITOR = 8L;

    private CommentMapper commentMapper;
    private ArticleMapper articleMapper;
    private SensitiveWordService sensitiveWordService;
    private NotificationService notificationService;
    private UserMapper userMapper;
    private CommentService service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, Comment.class);
    }

    @BeforeEach
    void setUp() {
        commentMapper = mock(CommentMapper.class);
        articleMapper = mock(ArticleMapper.class);
        sensitiveWordService = mock(SensitiveWordService.class);
        notificationService = mock(NotificationService.class);
        userMapper = mock(UserMapper.class);
        service = new CommentService(commentMapper, articleMapper, sensitiveWordService,
                notificationService, userMapper);
        when(articleMapper.selectById(1L)).thenReturn(publishedArticle());
        doAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(500L);
            return 1;
        }).when(commentMapper).insert(any(Comment.class));
        User commenter = new User();
        commenter.setId(VISITOR);
        commenter.setNickname("访客甲");
        when(userMapper.selectById(VISITOR)).thenReturn(commenter);
    }

    private static Article publishedArticle() {
        Article a = new Article();
        a.setId(1L);
        a.setAuthorId(AUTHOR);
        a.setStatus("PUBLISHED");
        a.setDeleted(0);
        a.setTitle("文章一");
        return a;
    }

    @Test
    void postCleanCommentPublishesCountsAndNotifiesAuthor() {
        when(sensitiveWordService.firstHit(anyString())).thenReturn(null);

        var r = service.post(1L, VISITOR, null, "写得不错");

        assertEquals("PUBLISHED", r.get("status"));
        verify(articleMapper).update(eq(null), any());
        verify(notificationService).notifyUser(eq(AUTHOR), eq("COMMENT"), anyString(), anyString(), eq("/article/1"));
    }

    @Test
    void postSelfCommentDoesNotNotifyAuthor() {
        when(sensitiveWordService.firstHit(anyString())).thenReturn(null);

        service.post(1L, AUTHOR, null, "自评一条");

        verify(articleMapper).update(eq(null), any());
        verify(notificationService, never()).notifyUser(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void postSensitiveHitGoesToManualAudit() {
        when(sensitiveWordService.firstHit(anyString())).thenReturn("违禁词");

        var r = service.post(1L, VISITOR, null, "含违禁词内容");

        assertEquals("PENDING_AUDIT", r.get("status"));
        verify(articleMapper, never()).update(eq(null), any());
        verify(notificationService, never()).notifyUser(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void postRejectsUnpublishedArticle() {
        Article draft = publishedArticle();
        draft.setStatus("DRAFT");
        when(articleMapper.selectById(1L)).thenReturn(draft);

        assertEquals(2003, assertThrows(BizException.class,
                () -> service.post(1L, VISITOR, null, "评论")).getCode());
    }

    @Test
    void replyToMissingParentRejected() {
        when(commentMapper.selectById(999L)).thenReturn(null);

        assertEquals(2202, assertThrows(BizException.class,
                () -> service.post(1L, VISITOR, 999L, "回复")).getCode());
    }

    @Test
    void replyToTopLevelCommentInheritsItAsRoot() {
        Comment parent = new Comment();
        parent.setId(77L);
        parent.setUserId(2L);
        parent.setRootId(0L);
        when(commentMapper.selectById(77L)).thenReturn(parent);
        when(sensitiveWordService.firstHit(anyString())).thenReturn(null);

        var r = service.post(1L, VISITOR, 77L, "回复一下");

        assertEquals("PUBLISHED", r.get("status"));
        ArgumentCaptor<Comment> cap = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper).insert(cap.capture());
        assertEquals(77L, cap.getValue().getRootId());
        // 楼主评论的回复：通知被回复人（非本人）
        verify(notificationService).notifyUser(eq(2L), eq("REPLY"), anyString(), anyString(), eq("/article/1"));
    }

    @Test
    void approveRequiresPendingAuditState() {
        Comment c = new Comment();
        c.setId(5L);
        c.setArticleId(1L);
        c.setStatus("PUBLISHED");
        when(commentMapper.selectById(5L)).thenReturn(c);

        assertEquals(2201, assertThrows(BizException.class, () -> service.approve(5L)).getCode());
    }

    @Test
    void removePublishedCommentDecrementsCount() {
        Comment c = new Comment();
        c.setId(5L);
        c.setArticleId(1L);
        c.setStatus("PUBLISHED");
        when(commentMapper.selectById(5L)).thenReturn(c);

        service.remove(5L);

        assertEquals("REMOVED", c.getStatus());
        verify(articleMapper).update(eq(null), any());
    }
}
