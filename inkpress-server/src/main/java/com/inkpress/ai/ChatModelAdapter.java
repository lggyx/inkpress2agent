package com.inkpress.ai;

import java.util.List;

/**
 * 对话模型适配器 SPI。
 * 业务层只依赖本接口，不感知具体厂商协议；接入新厂商时新增实现类并在配置中切换即可。
 * 默认实现 OpenAiCompatibleAdapter 覆盖所有兼容 OpenAI 协议的服务（DeepSeek、智谱、通义、Kimi、Ollama 等）。
 */
public interface ChatModelAdapter {

    /** 同步调用，返回完整回复 */
    String complete(List<ChatMessage> messages);

    /** 流式调用，增量内容通过回调输出 */
    void stream(List<ChatMessage> messages, ChatStreamHandler handler);
}
