package cn.hengxing.inkpress.modules.search.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 语料切分：把文章 Markdown 正文切成检索单元（chunk）。
 * <p>
 * 策略：去 Markdown 标记 → 按中文句界（。！？；换行）累积到目标长度 → 块间重叠保留上下文。
 * 目标 150 字符/块：35 篇人类原创（554,464 字符）可切出约 3,700 个检索单元，
 * 与路演物料「3000+ 语料全部可检索」的 chunk 口径对齐（见 content/agent/09 语料口径清算）。
 * <p>
 * 无标点超长串（代码/公式残留）按目标长度硬切；过短尾块并入前块，避免噪声单元。
 */
@Service
public class ChunkingService {

    /** 目标块长（字符） */
    public static final int TARGET = 150;
    /** 块间重叠（字符），保留跨块上下文 */
    public static final int OVERLAP = 30;
    /** 短于该长度的尾块并入前块 */
    private static final int MIN_TAIL = 40;

    /**
     * 切分正文为检索单元列表；空内容返回空列表。
     */
    public List<String> chunk(String md) {
        String text = normalize(md);
        if (text.isEmpty()) {
            return List.of();
        }
        if (text.length() <= TARGET) {
            return List.of(text);
        }
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String seg : splitSentences(text)) {
            if (seg.length() > TARGET) {
                // 无标点超长串：先落地当前块，再硬切
                if (!cur.isEmpty()) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                for (int i = 0; i < seg.length(); i += TARGET - OVERLAP) {
                    int end = Math.min(i + TARGET, seg.length());
                    out.add(seg.substring(i, end));
                    if (end >= seg.length()) {
                        break;
                    }
                }
                continue;
            }
            if (cur.length() + seg.length() > TARGET) {
                out.add(cur.toString());
                String tail = cur.length() > OVERLAP ? cur.substring(cur.length() - OVERLAP) : cur.toString();
                cur = new StringBuilder(tail).append(seg);
            } else {
                cur.append(seg);
            }
        }
        if (!cur.isEmpty()) {
            String last = cur.toString();
            if (last.length() < MIN_TAIL && !out.isEmpty()) {
                out.set(out.size() - 1, out.get(out.size() - 1) + last);
            } else {
                out.add(last);
            }
        }
        return out;
    }

    /** 去 Markdown 标记、压缩空白（与 SearchService.stripMd 同规则，检索文本与索引一致） */
    private String normalize(String md) {
        if (md == null) {
            return "";
        }
        return md.replaceAll("[#>*`\\-\\[\\]()!]", " ").replaceAll("\\s+", " ").trim();
    }

    /** 按中文句界切句，保留句末标点 */
    private List<String> splitSentences(String text) {
        List<String> segs = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(c);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '!' || c == '?' || c == ';' || c == '\n') {
                segs.add(sb.toString());
                sb.setLength(0);
            }
        }
        if (!sb.isEmpty()) {
            segs.add(sb.toString());
        }
        return segs;
    }
}
