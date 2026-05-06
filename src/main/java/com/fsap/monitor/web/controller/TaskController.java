package com.fsap.monitor.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.fsap.monitor.core.artifact.ArtifactBrowseService;
import com.fsap.monitor.core.ingest.InputUploadService;
import com.fsap.monitor.core.task.TaskExecutionService;
import com.fsap.monitor.infra.config.FsapProperties;
import com.fsap.monitor.web.dto.GenerateReportTaskRequest;
import com.fsap.monitor.web.dto.IngestTaskRequest;
import com.fsap.monitor.web.dto.MonitorExportTaskRequest;
import com.fsap.monitor.web.dto.SyncViewsTaskRequest;

@Validated
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/tasks")
/**
 * 提供任務執行與任務相關產物查詢的 HTTP 端點。
 *
 * <p>這個 controller 刻意保持精簡，只負責：
 * - readonly 模式的阻擋
 * - 例外轉成適當 HTTP 狀態碼
 *
 * <p>真正的 workflow 順序與執行規則都集中在 {@link TaskExecutionService}。
 */
public class TaskController {

    private final TaskExecutionService taskExecutionService;
    private final FsapProperties properties;
    private final InputUploadService inputUploadService;
    private final ArtifactBrowseService artifactBrowseService;

    public TaskController(
            TaskExecutionService taskExecutionService,
            FsapProperties properties,
            InputUploadService inputUploadService,
            ArtifactBrowseService artifactBrowseService
    ) {
        this.taskExecutionService = taskExecutionService;
        this.properties = properties;
        this.inputUploadService = inputUploadService;
        this.artifactBrowseService = artifactBrowseService;
    }

    /**
     * UI task dashboard 輪詢狀態用的端點。
     */
    @GetMapping
    public TaskExecutionService.TaskDashboard dashboard() {
        return taskExecutionService.dashboard(12);
    }

    @PostMapping("/doctor")
    public TaskExecutionService.TaskSnapshot startDoctor() {
        ensureWritable();
        return launch(() -> taskExecutionService.startDoctor());
    }

    @PostMapping("/sync-views")
    public TaskExecutionService.TaskSnapshot startSyncViews(@RequestBody(required = false) SyncViewsTaskRequest request) {
        ensureWritable();
        SyncViewsTaskRequest resolved = request == null ? new SyncViewsTaskRequest(null, false) : request;
        return launch(() -> taskExecutionService.startSyncViews(resolved.maxRounds(), resolved.failFast()));
    }

    @PostMapping("/ingest")
    public TaskExecutionService.TaskSnapshot startIngest(@RequestBody(required = false) IngestTaskRequest request) {
        ensureWritable();
        IngestTaskRequest resolved = request == null ? new IngestTaskRequest(false, null, null) : request;
        return launch(() -> taskExecutionService.startIngest(resolved.force(), resolved.limit(), resolved.date()));
    }

    @PostMapping("/generate-report")
    public TaskExecutionService.TaskSnapshot startGenerateReport(@RequestBody(required = false) GenerateReportTaskRequest request) {
        ensureWritable();
        GenerateReportTaskRequest resolved = request == null ? GenerateReportTaskRequest.empty() : request;
        return launch(() -> taskExecutionService.startGenerateReport(resolved.toReportGenerationRequest()));
    }

    @PostMapping("/update-monitor-data")
    public TaskExecutionService.TaskSnapshot startUpdateMonitorData(@RequestBody(required = false) MonitorExportTaskRequest request) {
        ensureWritable();
        MonitorExportTaskRequest resolved = request == null ? new MonitorExportTaskRequest(null) : request;
        return launch(() -> taskExecutionService.startUpdateMonitorData(resolved.configPath()));
    }

    @PostMapping("/ingest/upload")
    public InputUploadService.UploadBatchResult uploadIngestFiles(@RequestParam("files") MultipartFile[] files) {
        ensureWritable();
        try {
            return inputUploadService.uploadExcelFiles(files);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), exception);
        }
    }

    @GetMapping("/ingest/files")
    public java.util.List<ArtifactBrowseService.FileView> ingestFiles() {
        return artifactBrowseService.loadInputExcelFiles();
    }

    @GetMapping("/report-batches")
    public java.util.List<ArtifactBrowseService.ReportBatchView> reportBatches() {
        return artifactBrowseService.loadRecentReportBatches(3);
    }

    private void ensureWritable() {
        if (properties.getWeb().isReadonly()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Web task actions are disabled in readonly mode");
        }
    }

    private TaskExecutionService.TaskSnapshot launch(TaskStarter starter) {
        try {
            return starter.start();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @FunctionalInterface
    private interface TaskStarter {
        TaskExecutionService.TaskSnapshot start();
    }
}
