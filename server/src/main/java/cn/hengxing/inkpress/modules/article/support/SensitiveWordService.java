package cn.hengxing.inkpress.modules.article.support;

import cn.hengxing.inkpress.modules.article.entity.SensitiveWord;
import cn.hengxing.inkpress.modules.article.mapper.SensitiveWordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 敏感词库：启动加载 + 每 60s 刷新；命中检查供评论与评审复用 */
@Component
public class SensitiveWordService {

    private final SensitiveWordMapper sensitiveWordMapper;
    private volatile List<String> words = List.of();

    public SensitiveWordService(SensitiveWordMapper sensitiveWordMapper) {
        this.sensitiveWordMapper = sensitiveWordMapper;
        reload();
    }

    @Scheduled(fixedDelay = 60_000)
    public void reload() {
        words = sensitiveWordMapper.selectList(null).stream().map(SensitiveWord::getWord)
                .filter(w -> w != null && !w.isBlank()).toList();
    }

    /** 返回首个命中词，未命中返回 null */
    public String firstHit(String text) {
        if (text == null) {
            return null;
        }
        for (String w : words) {
            if (text.contains(w)) {
                return w;
            }
        }
        return null;
    }
}
