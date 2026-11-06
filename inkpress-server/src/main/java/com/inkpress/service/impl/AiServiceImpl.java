package com.inkpress.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkpress.ai.ChatMessage;
import com.inkpress.ai.ChatModelAdapter;
import com.inkpress.ai.ChatStreamHandler;
import com.inkpress.common.BizException;
import com.inkpress.common.ErrorCode;
import com.inkpress.service.AiConfigService;
import com.inkpress.service.AiService;
import com.inkpress.vo.AiTestResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 写作辅助实现：提示词模板 + 结果解析 + 失败降级
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceImpl implements AiService {

    /** 喂给模型的正文上限，避免超出上下文 */
    private static final int MAX_INPUT = 8000;

    private final ChatModelAdapter adapter;
    private final AiConfigService aiConfigService;
    private final ObjectMapper objectMapper;

    @Override
    public void ensureAvailable() {
        if (!aiConfigService.getEffective().enabled()) {
            throw new BizException(ErrorCode.AI_NOT_CONFIGURED);
        }
    }

    @Override
    public void polish(String content, String instruction, ChatStreamHandler handler) {
        ensureAvailable();
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位专业的中文写作助手。请对用户提供的博客文章内容进行润色：")
                .append("保持原意与整体结构，修正错别字和语病，使表达更流畅自然；")
                .append("可以优化小标题和过渡句，但不要增删事实内容。")
                .append("直接输出润色后的全文，不要任何解释或前缀，不要用代码块包裹。");
        if (instruction != null && !instruction.isBlank()) {
            prompt.append("用户的额外要求：").append(instruction.trim());
        }
        adapter.stream(List.of(ChatMessage.system(prompt.toString()), ChatMessage.user(content)), handler);
    }

    @Override
    public void continueWriting(String content, String instruction, ChatStreamHandler handler) {
        ensureAvailable();
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一位中文写作助手。用户正在写一篇博客文章，请顺着上文续写 200~400 字：")
                .append("保持与上文一致的语气、观点和结构，自然衔接，不要重复上文已有内容。")
                .append("直接输出续写内容，不要任何解释或前缀。");
        if (instruction != null && !instruction.isBlank()) {
            prompt.append("用户的额外要求：").append(instruction.trim());
        }
        adapter.stream(List.of(ChatMessage.system(prompt.toString()), ChatMessage.user(content)), handler);
    }

    @Override
    public String summary(String content) {
        ensureAvailable();
        List<ChatMessage> messages = List.of(
                ChatMessage.system("你是文章摘要助手。请为用户提供的内容生成一段 80~150 字的中文摘要，"
                        + "概括核心观点，直接输出摘要正文，不要任何前缀、引号或解释。"),
                ChatMessage.user(truncate(content)));
        try {
            return adapter.complete(messages).trim();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("生成摘要失败", e);
            throw new BizException(ErrorCode.AI_CALL_FAILED);
        }
    }

    @Override
    public List<String> suggestTags(String content, List<String> candidates) {
        ensureAvailable();
        String candidateText = (candidates == null || candidates.isEmpty())
                ? "（暂无，可自由提出）"
                : String.join("、", candidates);
        List<ChatMessage> messages = List.of(
                ChatMessage.system("你是标签推荐助手。根据文章内容从候选标签中选择最合适的 3~5 个；"
                        + "若候选标签都不合适，可以新增不超过 2 个标签。"
                        + "只输出 JSON 字符串数组，例如 [\"Java\",\"后端\"]，不要输出任何其他内容。"),
                ChatMessage.user("候选标签：" + candidateText + "\n\n文章内容：\n" + truncate(content)));
        try {
            String answer = adapter.complete(messages);
            return parseTagArray(answer);
        } catch (Exception e) {
            log.warn("推荐标签失败", e);
            return List.of();
        }
    }

    @Override
    public void ask(String question, String knowledge, ChatStreamHandler handler) {
        ensureAvailable();
        List<ChatMessage> messages = List.of(
                ChatMessage.system("你是博客站内的知识问答助手。请仅依据下面提供的站内文章资料回答用户问题："
                        + "回答要准确、简洁，使用中文；如果资料不足以回答，直接说明站内暂无相关内容，不要编造。"
                        + "回答中可以用 [1][2] 这类编号标注信息来自哪篇资料。"),
                ChatMessage.user("站内文章资料：\n" + knowledge + "\n\n用户问题：" + question));
        try {
            adapter.stream(messages, handler);
        } catch (BizException e) {
            handler.onError(e);
        }
    }

    @Override
    public List<String> suggestTitles(String content) {
        ensureAvailable();
        List<ChatMessage> messages = List.of(
                ChatMessage.system("你是标题助手。根据文章内容生成 5 个中文标题候选："
                        + "要求有信息量、不标题党、长度 8~25 字，风格可多样（直述/悬念/数字型）。"
                        + "只输出 JSON 字符串数组，例如 [\"标题一\",\"标题二\"]，不要输出任何其他内容。"),
                ChatMessage.user(truncate(content)));
        try {
            return parseStringArray(adapter.complete(messages));
        } catch (Exception e) {
            log.warn("生成标题失败", e);
            return List.of();
        }
    }

    @Override
    public AiTestResultVO testConnection() {
        AiTestResultVO result = new AiTestResultVO();
        long start = System.currentTimeMillis();
        try {
            ensureAvailable();
            String reply = adapter.complete(List.of(
                    ChatMessage.system("你是连通性测试助手，只需回复两个字：正常。"),
                    ChatMessage.user("测试")));
            result.setSuccess(true);
            result.setReply(reply.length() > 50 ? reply.substring(0, 50) + "…" : reply);
        } catch (BizException e) {
            result.setSuccess(false);
            result.setMessage(e.getMessage());
        } catch (Exception e) {
            log.warn("AI 连通性测试失败", e);
            result.setSuccess(false);
            result.setMessage(ErrorCode.AI_CALL_FAILED.getMessage());
        }
        result.setLatencyMs(System.currentTimeMillis() - start);
        return result;
    }

    /** 从模型输出中提取 JSON 数组，容忍代码块包裹等噪音；解析失败返回空列表 */
    private List<String> parseStringArray(String answer) {
        int start = answer.indexOf('[');
        int end = answer.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            List<String> items = objectMapper.readValue(answer.substring(start, end + 1),
                    new TypeReference<List<String>>() {
                    });
            return items.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(String::trim)
                    .limit(8)
                    .toList();
        } catch (JsonProcessingException e) {
            log.warn("数组解析失败: {}", answer);
            return List.of();
        }
    }

    /** 从模型输出中提取标签 JSON 数组；解析失败返回空列表 */
    private List<String> parseTagArray(String answer) {
        int start = answer.indexOf('[');
        int end = answer.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            List<String> tags = objectMapper.readValue(answer.substring(start, end + 1),
                    new TypeReference<List<String>>() {
                    });
            return tags.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(String::trim)
                    .limit(8)
                    .toList();
        } catch (JsonProcessingException e) {
            log.warn("标签解析失败: {}", answer);
            return List.of();
        }
    }

    private String truncate(String content) {
        return content.length() <= MAX_INPUT ? content : content.substring(0, MAX_INPUT);
    }
}
