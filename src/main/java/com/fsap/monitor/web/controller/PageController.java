package com.fsap.monitor.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.fsap.monitor.core.artifact.ArtifactBrowseService;
import com.fsap.monitor.core.query.QueryHistoryService;
import com.fsap.monitor.core.report.ReportParameterDefaultsService;
import com.fsap.monitor.core.query.SchemaBrowseService;
import com.fsap.monitor.core.service.ProjectPathService;
import com.fsap.monitor.core.task.TaskExecutionService;
import com.fsap.monitor.infra.config.FsapProperties;

/**
 * 準備主畫面 Thymeleaf 所需的 dashboard model。
 */
@Controller
public class PageController {

    private final ProjectPathService projectPathService;
    private final FsapProperties properties;
    private final ArtifactBrowseService artifactBrowseService;
    private final QueryHistoryService queryHistoryService;
    private final SchemaBrowseService schemaBrowseService;
    private final TaskExecutionService taskExecutionService;
    private final ReportParameterDefaultsService reportParameterDefaultsService;

    public PageController(
            ProjectPathService projectPathService,
            FsapProperties properties,
            ArtifactBrowseService artifactBrowseService,
            QueryHistoryService queryHistoryService,
            SchemaBrowseService schemaBrowseService,
            TaskExecutionService taskExecutionService,
            ReportParameterDefaultsService reportParameterDefaultsService
    ) {
        this.projectPathService = projectPathService;
        this.properties = properties;
        this.artifactBrowseService = artifactBrowseService;
        this.queryHistoryService = queryHistoryService;
        this.schemaBrowseService = schemaBrowseService;
        this.taskExecutionService = taskExecutionService;
        this.reportParameterDefaultsService = reportParameterDefaultsService;
    }

    @GetMapping("/")
    public String queryPage(Model model) {
        // 首頁初始化時先把 dashboard 需要的資料一次備齊，避免首屏渲染還要再發出
        // 多個額外 API 請求才看得到完整畫面。
        model.addAttribute("databaseFile", projectPathService.databaseFile());
        model.addAttribute("inputDirectory", projectPathService.inputDir());
        model.addAttribute("readonly", properties.getWeb().isReadonly());
        model.addAttribute("taskDashboard", taskExecutionService.dashboard(10));
        model.addAttribute("inputFiles", artifactBrowseService.loadInputExcelFiles());
        model.addAttribute("recentQueries", queryHistoryService.loadRecent(10));
        model.addAttribute("recentReportBatches", artifactBrowseService.loadRecentReportBatches(3));
        model.addAttribute("monitorFiles", artifactBrowseService.loadMonitorDataFiles());
        model.addAttribute("reportDefaults", reportParameterDefaultsService.defaultRequest());
        try {
            model.addAttribute("schemaTables", schemaBrowseService.loadSchemaSnapshot());
        } catch (Exception exception) {
            model.addAttribute("schemaTables", java.util.List.of());
            model.addAttribute("schemaError", exception.getMessage());
        }
        return "query";
    }
}
