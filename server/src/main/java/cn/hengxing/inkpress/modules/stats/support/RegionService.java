package cn.hengxing.inkpress.modules.stats.support;

import jakarta.annotation.PostConstruct;
import org.lionsoul.ip2region.xdb.Searcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/** M6 地域解析：ip2region 离线库（全量缓存进堆），内网 IP 与解析失败兜底"内网"/"未知" */
@Service
public class RegionService {

    private static final Logger log = LoggerFactory.getLogger(RegionService.class);
    public static final String UNKNOWN = "未知";
    public static final String INTRANET = "内网";

    private volatile Searcher searcher;

    @PostConstruct
    public void init() {
        try {
            byte[] buff = new ClassPathResource("ip2region.xdb").getInputStream().readAllBytes();
            searcher = Searcher.newWithBuffer(buff);
            log.info("ip2region 离线库加载完成，体积 {} MB", buff.length / 1024 / 1024);
        } catch (Exception e) {
            log.warn("ip2region 离线库加载失败，地域解析将全部返回未知", e);
        }
    }

    /** 归属地摘要：国家/省/市拼接（0 值跳过），如"中国·广东省·深圳市"、"美国" */
    public String regionOf(String ip) {
        if (ip == null || ip.isBlank()) {
            return UNKNOWN;
        }
        if (isIntranet(ip)) {
            return INTRANET;
        }
        Searcher s = searcher;
        if (s == null) {
            return UNKNOWN;
        }
        try {
            String raw = s.search(ip);
            if (raw == null || raw.isBlank()) {
                return UNKNOWN;
            }
            String[] parts = raw.split("\\|", -1);
            StringBuilder sb = new StringBuilder();
            // 0/2/3 位：国家 | 区域 | 省 | 市 | ISP，取国家/省/市
            for (int i : new int[]{0, 2, 3}) {
                String part = i < parts.length ? parts[i] : "";
                if (part.isBlank() || "0".equals(part)) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('·');
                }
                sb.append(part);
            }
            return sb.length() == 0 ? UNKNOWN : sb.toString();
        } catch (Exception e) {
            return UNKNOWN;
        }
    }

    private boolean isIntranet(String ip) {
        if (ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")
                || ip.startsWith("169.254.") || ip.startsWith("0.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
}
