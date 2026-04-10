package com.campus.stats.controller;

import com.alibaba.excel.EasyExcel;
import com.campus.common.api.ApiResponse;
import com.campus.stats.model.StatsExportRow;
import com.campus.stats.model.StatsGranularity;
import com.campus.stats.service.StartupHealthCheckService;
import com.campus.stats.service.StatsAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsAnalyticsService statsAnalyticsService;
    private final StartupHealthCheckService startupHealthCheckService;

    public StatsController(StatsAnalyticsService statsAnalyticsService,
                           StartupHealthCheckService startupHealthCheckService) {
        this.statsAnalyticsService = statsAnalyticsService;
        this.startupHealthCheckService = startupHealthCheckService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(@RequestParam(defaultValue = "DAY") String granularity,
                                                      @RequestParam(defaultValue = "14") int size) {
        StatsGranularity parsed = StatsGranularity.from(granularity);
        return ApiResponse.success(statsAnalyticsService.dashboard(parsed, size));
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.success(statsAnalyticsService.overview());
    }

    @GetMapping("/health/startup")
    public ApiResponse<Map<String, Object>> startupHealth(HttpServletRequest request) {
        return ApiResponse.success(startupHealthCheckService.check(request));
    }

    @GetMapping("/trends")
    public ApiResponse<Map<String, Object>> trends(@RequestParam(defaultValue = "DAY") String granularity,
                                                   @RequestParam(defaultValue = "14") int size) {
        StatsGranularity parsed = StatsGranularity.from(granularity);
        return ApiResponse.success(statsAnalyticsService.trends(parsed, size));
    }

    @GetMapping("/rankings")
    public ApiResponse<Map<String, Object>> rankings() {
        return ApiResponse.success(statsAnalyticsService.rankings());
    }

    @GetMapping("/export")
    public void export(@RequestParam(defaultValue = "DAY") String granularity,
                       @RequestParam(defaultValue = "14") int size,
                       HttpServletResponse response) throws Exception {
        StatsGranularity parsed = StatsGranularity.from(granularity);
        List<StatsExportRow> rows = statsAnalyticsService.exportRows(parsed, size);

        String fileName = URLEncoder.encode(
                "campus-stats-" + LocalDate.now(),
                StandardCharsets.UTF_8
        ).replace("+", "%20");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        EasyExcel.write(response.getOutputStream(), StatsExportRow.class)
                .sheet("Statistics")
                .doWrite(rows);
    }
}
