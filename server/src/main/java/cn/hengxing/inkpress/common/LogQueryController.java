package cn.hengxing.inkpress.common;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 管理端日志查询：按 traceId / 级别 / 关键字过滤当日及历史日志文件 */
@RestController
@RequestMapping("/api/admin/logs")
public class LogQueryController {

    private static final Path LOG_DIR = Path.of(System.getProperty("LOG_DIR", "logs"));

    @GetMapping("/trace/{traceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<String>> byTraceId(@PathVariable String traceId,
                                          @RequestParam(required = false) String date) throws IOException {
        Path file = resolveLogFile(date);
        if (!Files.exists(file)) {
            return Result.ok(List.of());
        }
        List<String> matched = new ArrayList<>();
        try (var lines = Files.lines(file)) {
            lines.filter(l -> l.contains("[" + traceId + "]")).forEach(matched::add);
        }
        return Result.ok(matched);
    }

    private Path resolveLogFile(String date) {
        String day = (date == null || date.isBlank())
                ? LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                : date;
        Path dated = LOG_DIR.resolve("inkpress." + day + ".log");
        return Files.exists(dated) ? dated : LOG_DIR.resolve("inkpress.log");
    }
}
