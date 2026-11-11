package cn.hengxing.inkpress.modules.article.controller;

import cn.hengxing.inkpress.common.BizException;
import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.article.entity.SensitiveWord;
import cn.hengxing.inkpress.modules.article.mapper.SensitiveWordMapper;
import cn.hengxing.inkpress.modules.article.support.SensitiveWordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** M5：敏感词库管理员端维护（增删后 60s 内自动生效，亦可即时触发刷新） */
@RestController
@RequestMapping("/api/admin/sensitive-words")
@PreAuthorize("hasRole('ADMIN')")
public class SensitiveWordAdminController {

    public record AddRequest(@NotBlank String word) {
    }

    private final SensitiveWordMapper sensitiveWordMapper;
    private final SensitiveWordService sensitiveWordService;

    public SensitiveWordAdminController(SensitiveWordMapper sensitiveWordMapper,
                                        SensitiveWordService sensitiveWordService) {
        this.sensitiveWordMapper = sensitiveWordMapper;
        this.sensitiveWordService = sensitiveWordService;
    }

    @GetMapping
    public Result<List<SensitiveWord>> list() {
        return Result.ok(sensitiveWordMapper.selectList(
                new LambdaQueryWrapper<SensitiveWord>().orderByAsc(SensitiveWord::getId)));
    }

    @PostMapping
    public Result<Void> add(@RequestBody AddRequest req) {
        SensitiveWord sw = new SensitiveWord();
        sw.setWord(req.word().trim());
        if (sw.getWord().isEmpty()) {
            throw BizException.of(2701, "敏感词不能为空");
        }
        try {
            sensitiveWordMapper.insert(sw);
        } catch (DuplicateKeyException e) {
            throw BizException.of(2702, "敏感词已存在");
        }
        sensitiveWordService.reload();
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        sensitiveWordMapper.deleteById(id);
        sensitiveWordService.reload();
        return Result.ok();
    }

    @PostMapping("/reload")
    public Result<Map<String, Object>> reload() {
        sensitiveWordService.reload();
        return Result.ok(Map.of("reloaded", true));
    }
}
