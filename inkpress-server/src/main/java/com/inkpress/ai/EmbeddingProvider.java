package com.inkpress.ai;

/**
 * 文本向量化 SPI：将文本映射为稠密向量，供余弦相似度计算。
 * 本系统支持两种实现：
 *  1. HashingEmbeddingProvider —— 本地字符 bigram 特征哈希，无需外部服务；
 *  2. OpenAiCompatibleEmbeddingProvider —— 调用远程 Embedding API（智谱/通义等）。
 * 通过配置 inkpress.ai.embedding.provider 切换，业务层无感知。
 */
public interface EmbeddingProvider {

    /**
     * 将文本向量化
     *
     * @param text 输入文本（超长由实现方自行截断）
     * @return L2 归一化后的向量
     */
    double[] embed(String text);

    /** 实现标识，用于日志与论文实验说明 */
    String name();
}
