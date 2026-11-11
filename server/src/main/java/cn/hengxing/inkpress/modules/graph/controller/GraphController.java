package cn.hengxing.inkpress.modules.graph.controller;

import cn.hengxing.inkpress.common.Result;
import cn.hengxing.inkpress.modules.graph.service.GraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** M8 图谱查询：独立 /graph 页的力导向数据 + 文章页相关阅读（均游客可读） */
@RestController
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    /** 力导向图谱：文章/标签节点 + 标签共现与手动关联边 */
    @GetMapping("/api/graph")
    public Result<Map<String, Object>> graph() {
        return Result.ok(graphService.graph());
    }

    /** 相关阅读：manual 参数仅提示来源；visitorId 携带时按访客偏好重排序 */
    @GetMapping("/api/articles/{id}/related")
    public Result<List<Map<String, Object>>> related(@PathVariable Long id,
                                                     @RequestParam(required = false) String visitorId,
                                                     @RequestParam(defaultValue = "6") int limit) {
        return Result.ok(graphService.related(id, visitorId, limit));
    }
}
