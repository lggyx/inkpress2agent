package cn.hengxing.inkpress.modules.search.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChunkingService 单元测试：切分粒度、句界保留、重叠、硬切与幂等性。
 * 「3000+ 语料」口径的正确性由这些断言守护。
 */
class ChunkingServiceTest {

    private final ChunkingService svc = new ChunkingService();

    @Test
    void emptyOrNullYieldsEmpty() {
        assertTrue(svc.chunk(null).isEmpty());
        assertTrue(svc.chunk("").isEmpty());
        assertTrue(svc.chunk("   \n  ").isEmpty());
    }

    @Test
    void shortTextIsSingleChunk() {
        String text = "短文本，不足一个块。".repeat(3);
        List<String> chunks = svc.chunk(text);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).length() <= ChunkingService.TARGET);
    }

    @Test
    void longTextChunksRespectTarget() {
        // 300 句 × 平均 12 字 ≈ 3600 字 → 应切出约 24+ 块
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append("这是第").append(i).append("句用于测试的句子内容。");
        }
        List<String> chunks = svc.chunk(sb.toString());
        assertTrue(chunks.size() >= 20, "expected 20+ chunks, got " + chunks.size());
        for (String c : chunks) {
            // 单块不超过目标长度（句界累积允许最后一块略长也受目标约束）
            assertTrue(c.length() <= ChunkingService.TARGET + 20,
                    "chunk too long: " + c.length());
            assertFalse(c.isBlank(), "blank chunk");
        }
    }

    @Test
    void chunksCoverAllContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            sb.append("编号").append(i).append("的句子，带标点结尾。");
        }
        String text = sb.toString();
        List<String> chunks = svc.chunk(text);
        // 无重叠丢失：每个编号都至少出现在某个块里
        for (int i = 0; i < 120; i++) {
            String marker = "编号" + i + "的句子";
            assertTrue(chunks.stream().anyMatch(c -> c.contains(marker)),
                    "missing marker: " + marker);
        }
    }

    @Test
    void overlapPreservesContextAcrossChunks() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("第").append(i).append("段文字在这里结尾。");
        }
        List<String> chunks = svc.chunk(sb.toString());
        assertTrue(chunks.size() >= 2);
        // 相邻块之间存在重叠：后块开头应复现前块结尾的片段
        boolean foundOverlap = false;
        for (int i = 1; i < chunks.size(); i++) {
            String prevTail = chunks.get(i - 1).substring(Math.max(0, chunks.get(i - 1).length() - 10));
            if (chunks.get(i).contains(prevTail)) {
                foundOverlap = true;
                break;
            }
        }
        assertTrue(foundOverlap, "expected overlap between adjacent chunks");
    }

    @Test
    void longRunWithoutPunctuationIsHardSplit() {
        String text = "无标点长串".repeat(200); // 1000 字无句界
        List<String> chunks = svc.chunk(text);
        assertTrue(chunks.size() >= 6);
        for (String c : chunks) {
            assertTrue(c.length() <= ChunkingService.TARGET,
                    "hard-split chunk over target: " + c.length());
        }
    }

    @Test
    void deterministic() {
        String text = "确定性测试句子，重复执行结果应一致。".repeat(50);
        assertEquals(svc.chunk(text), svc.chunk(text));
    }
}
