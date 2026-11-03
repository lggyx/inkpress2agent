package com.inkpress.ai;

import com.inkpress.config.AiProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 本地向量实现：字符 bigram 特征哈希（feature hashing）。
 * 思路：对文本提取相邻二元字符组，哈希到固定维度桶并累加词频，
 * 最后 L2 归一化。不依赖任何外部服务与分词词典，
 * 对中文短文本检索是简单有效的基线方案。
 */
@Component
public class HashingEmbeddingProvider implements EmbeddingProvider {

    private final AiProperties properties;

    public HashingEmbeddingProvider(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public double[] embed(String text) {
        int dim = properties.getEmbedding().getLocalDim() != null
                ? properties.getEmbedding().getLocalDim() : 512;
        double[] vector = new double[dim];
        if (text == null || text.isBlank()) {
            return vector;
        }
        // 去除标点与空白，仅保留有效字符
        String cleaned = text.replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "");
        if (cleaned.length() < 2) {
            return vector;
        }
        // 字符 bigram 频率统计，哈希分桶
        Map<Integer, Integer> buckets = new HashMap<>();
        for (int i = 0; i < cleaned.length() - 1; i++) {
            String bigram = cleaned.substring(i, i + 2);
            int bucket = Math.floorMod(bigram.hashCode(), dim);
            buckets.merge(bucket, 1, Integer::sum);
        }
        // 词频开方缓和长文优势，再 L2 归一化
        double norm = 0;
        for (Map.Entry<Integer, Integer> e : buckets.entrySet()) {
            double w = Math.sqrt(e.getValue());
            vector[e.getKey()] = w;
            norm += w * w;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    @Override
    public String name() {
        return "hashing-bigram-" + (properties.getEmbedding().getLocalDim() != null
                ? properties.getEmbedding().getLocalDim() : 512);
    }
}
