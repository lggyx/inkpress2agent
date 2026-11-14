package cn.hengxing.inkpress.modules.evolution;

import cn.hengxing.inkpress.modules.evolution.service.EvolutionService;
import cn.hengxing.inkpress.modules.evolution.service.WritingMemoryService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 进化指标纯函数：归一化、编辑距离（万分比）、指标汇总、记忆类目收敛 */
class EvolutionServiceTest {

    @Test
    void normalizeCollapsesWhitespace() {
        assertEquals("a b c", EvolutionService.normalize("a\n b\r\n  c\t"));
        assertEquals("", EvolutionService.normalize(null));
        assertEquals("", EvolutionService.normalize("   "));
    }

    @Test
    void identicalTextIsZeroDistance() {
        String s = "# 标题\n\n这是同一段中文内容，含空格 与 标点。";
        assertEquals(0, EvolutionService.levenshteinBp(EvolutionService.normalize(s), EvolutionService.normalize(s)));
    }

    @Test
    void distanceGrowsWithEdits() {
        String base = EvolutionService.normalize("随着互联网的发展，本文将讨论内容创作的未来。");
        String lightEdit = EvolutionService.normalize("随着互联网的发展，本文将探讨内容创作的未来。"); // 一字之差
        String heavyEdit = EvolutionService.normalize("Here is a completely different opening sentence for the article.");
        int zero = EvolutionService.levenshteinBp(base, base);
        int light = EvolutionService.levenshteinBp(base, lightEdit);
        int heavy = EvolutionService.levenshteinBp(base, heavyEdit);
        assertEquals(0, zero);
        assertTrue(light > 0 && light < 3000, "同语境微调应为低幅度（bp=" + light + "）");
        assertTrue(heavy > light, "大改应比微调距离更大（heavy=" + heavy + ", light=" + light + "）");
        assertTrue(heavy <= 10000);
    }

    @Test
    void verbatimContainmentDrivesZeroDistance() {
        // 采纳后未修改：建议文本归一化后应是定稿的子串（REPLACE 全文替换 / APPEND 追加同理）
        String suggestion = "第一段 AI 输出内容。第二段 AI 输出内容。";
        String finalContent = "开头保留的作者句子。\n\n" + suggestion + "\n\n结尾补一句。";
        String sugNorm = EvolutionService.normalize(suggestion);
        String finNorm = EvolutionService.normalize(finalContent);
        assertTrue(finNorm.contains(sugNorm));
        assertEquals(0, EvolutionService.levenshteinBp(sugNorm, sugNorm));
    }

    @Test
    void metricsMapCarriesAllFields() {
        Map<String, Object> m = EvolutionService.buildMetrics(5, 3, 2, 1, 1, 450, 2, 3, true);
        assertEquals(5, m.get("suggestionsTotal"));
        assertEquals(3, m.get("applied"));
        assertEquals(2, m.get("verbatim"));
        assertEquals(450, m.get("avgEditDistanceBp"));
        assertEquals(3, m.get("memoryLearned"));
        assertEquals(true, m.get("distilled"));
    }

    @Test
    void memoryCategoryFallsBackToStyle() {
        assertEquals("TONE", WritingMemoryService.safeCategory("tone"));
        assertEquals("STRUCTURE", WritingMemoryService.safeCategory("STRUCTURE"));
        assertEquals("STYLE", WritingMemoryService.safeCategory("whatever"));
        assertEquals("STYLE", WritingMemoryService.safeCategory(null));
    }

    @Test
    void longTextDistanceStaysBounded() {
        String a = "中文内容重复。".repeat(2000);
        String b = a.replace("中文", "文字");
        int bp = EvolutionService.levenshteinBp(a, b);
        assertTrue(bp > 0 && bp < 10000);
        assertNotEquals(0, bp);
    }
}
