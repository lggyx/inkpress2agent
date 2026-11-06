package com.inkpress.service;

import com.inkpress.config.AiProperties;
import com.inkpress.dto.AiConfigDTO;
import com.inkpress.vo.AiConfigVO;

/**
 * AI 服务配置管理（管理端动态配置）。
 * 解析优先级：数据库配置（ai_config 表有行）> 环境变量（application.yml / AI_*）> 代码默认值。
 */
public interface AiConfigService {

    /** 当前生效配置（每次调用实时解析，保证管理端保存后立即生效） */
    AiProperties getEffective();

    /** 管理端回显：含配置来源与脱敏密钥 */
    AiConfigVO getForAdmin();

    /** 保存配置（整行替换）；向量化相关配置变化时发布变更事件触发索引重建 */
    void update(AiConfigDTO dto, String operator);

    /** 删除数据库配置行，回落环境变量配置 */
    void reset(String operator);

    /** 向量化实现的签名（provider + 模型 + 维度），用于判断变化是否影响已存向量 */
    String embeddingSignature(AiProperties properties);
}
