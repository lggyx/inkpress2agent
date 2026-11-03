package com.inkpress.service;

import com.inkpress.ai.ChatStreamHandler;

import java.util.List;

/**
 * AI 写作辅助服务
 */
public interface AiService {

    /** 检查 AI 服务是否已配置，未配置抛出业务异常 */
    void ensureAvailable();

    /** 流式润色 */
    void polish(String content, String instruction, ChatStreamHandler handler);

    /** 流式续写：顺着上文继续写作 */
    void continueWriting(String content, String instruction, ChatStreamHandler handler);

    /** 生成文章摘要 */
    String summary(String content);

    /** 推荐标签，candidates 为系统已有标签，返回 3~5 个 */
    List<String> suggestTags(String content, List<String> candidates);

    /** 生成标题候选，返回 5 个；失败返回空列表 */
    List<String> suggestTitles(String content);
}
