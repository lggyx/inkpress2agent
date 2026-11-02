package com.inkpress.ai;

/**
 * 流式对话回调：每收到一段增量内容触发一次 onDelta
 */
public interface ChatStreamHandler {

    void onDelta(String delta);

    default void onDone() {
    }

    default void onError(Throwable t) {
    }
}
