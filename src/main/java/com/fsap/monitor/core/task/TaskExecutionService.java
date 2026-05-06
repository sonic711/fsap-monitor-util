package com.fsap.monitor.core.task;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fsap.monitor.core.ingest.IngestService;
import com.fsap.monitor.core.monitor.MonitorDataExportService;
import com.fsap.monitor.core.report.ReportGenerationRequest;
import com.fsap.monitor.core.report.ReportGenerationService;
import com.fsap.monitor.core.service.EnvironmentCheckService;
import com.fsap.monitor.core.service.EnvironmentCheckService.CheckResult;
import com.fsap.monitor.core.viewsync.ViewSyncService;

@Service
/**
 * UI 觸發之操作型任務的後端協調中心。
 *
 * <p>UI 雖然會呈現引導式 workflow，但真正的保護機制在這裡：
 * - 任務必須序列化執行
 * - 任務歷程必須被記錄
 * - 必須阻止錯誤順序，例如 doctor / ingest / sync views 尚未完成就先產報表
 */
public class TaskExecutionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecutionService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_HISTORY = 20;

    private final EnvironmentCheckService environmentCheckService;
    private final ViewSyncService viewSyncService;
    private final IngestService ingestService;
    private final ReportGenerationService reportGenerationService;
    private final MonitorDataExportService monitorDataExportService;
    private final ExecutorService executorService;
    private final AtomicLong sequence = new AtomicLong();
    private final Object lock = new Object();
    private final List<TaskRun> history = new ArrayList<>();
    private final EnumMap<WorkflowStep, LocalDateTime> completedSteps = new EnumMap<>(WorkflowStep.class);
    private final EnumMap<WorkflowStep, String> failedSteps = new EnumMap<>(WorkflowStep.class);

    private TaskRun runningTask;

    public TaskExecutionService(
            EnvironmentCheckService environmentCheckService,
            ViewSyncService viewSyncService,
            IngestService ingestService,
            ReportGenerationService reportGenerationService,
            MonitorDataExportService monitorDataExportService
    ) {
        this.environmentCheckService = environmentCheckService;
        this.viewSyncService = viewSyncService;
        this.ingestService = ingestService;
        this.reportGenerationService = reportGenerationService;
        this.monitorDataExportService = monitorDataExportService;
        this.executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fsap-task-runner");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 回傳目前執行中的任務、近期歷程與 workflow 狀態，供 dashboard 輪詢。
     */
    public TaskDashboard dashboard(int limit) {
        synchronized (lock) {
            List<TaskSnapshot> snapshots = history.stream()
                    .limit(Math.max(limit, 1))
                    .map(this::toSnapshot)
                    .toList();
            return new TaskDashboard(
                    runningTask == null ? null : toSnapshot(runningTask),
                    snapshots,
                    buildWorkflowStatus()
            );
        }
    }

    public TaskSnapshot startDoctor() {
        return submit(WorkflowStep.DOCTOR, "Run environment health checks", () -> {
            EnvironmentCheckService.DoctorReport report = environmentCheckService.runChecks();
            List<String> details = report.checks().stream()
                    .map(check -> "[%s] %s -> %s (%s)".formatted(
                            check.ok() ? "OK" : "FAIL",
                            check.name(),
                            check.target(),
                            check.message()))
                    .toList();
            if (!report.healthy()) {
                return TaskOutcome.failure("Environment checks reported failures", null, details);
            }
            return TaskOutcome.success("Environment checks passed", null, details);
        });
    }

    public TaskSnapshot startSyncViews(Integer maxRounds, boolean failFast) {
        String parameters = joinParameters(
                maxRounds == null ? null : "maxRounds=" + maxRounds,
                "failFast=" + failFast
        );
        return submit(WorkflowStep.SYNC_VIEWS, parameters, () -> {
            ViewSyncService.ViewSyncResult result = viewSyncService.syncViews(maxRounds, failFast);
            List<String> details = new ArrayList<>();
            details.add("success=" + result.successCount());
            details.add("failure=" + result.failureCount());
            details.addAll(result.failures());
            if (result.failureCount() > 0) {
                return TaskOutcome.failure("View sync finished with failures", null, details);
            }
            return TaskOutcome.success("View sync completed", null, details);
        });
    }

    public TaskSnapshot startIngest(boolean force, Integer limit, String date) {
        String parameters = joinParameters(
                "force=" + force,
                limit == null ? null : "limit=" + limit,
                date == null || date.isBlank() ? null : "date=" + date.trim()
        );
        return submit(WorkflowStep.INGEST, parameters, () -> {
            IngestService.IngestResult result = ingestService.ingest(force, limit, date);
            List<String> details = List.of(
                    "processed=" + result.processedFiles(),
                    "skipped=" + result.skippedFiles(),
                    "outputs=" + result.writtenFiles(),
                    "failures=" + result.failures().size()
            );
            if (!result.failures().isEmpty()) {
                List<String> failureDetails = new ArrayList<>(details);
                failureDetails.addAll(result.failures());
                return TaskOutcome.failure("Ingest completed with failures", null, failureDetails);
            }
            return TaskOutcome.success("Ingest completed", null, details);
        });
    }

    public TaskSnapshot startGenerateReport(ReportGenerationRequest request) {
        ReportGenerationRequest normalizedRequest = request == null ? ReportGenerationRequest.empty() : request.normalize();
        String parameters = String.join(", ", normalizedRequest.summaryParts());
        return submit(WorkflowStep.GENERATE_REPORT, parameters, () -> {
            ReportGenerationService.ReportGenerationResult result = reportGenerationService.generate(normalizedRequest);
            long successCount = result.reportResults().stream()
                    .filter(ReportGenerationService.ReportFileResult::success)
                    .count();
            List<String> details = new ArrayList<>();
            details.add("timestamp=" + result.timestamp());
            details.add("params=" + result.parametersFile());
            details.addAll(result.effectiveRequest().summaryParts());
            details.add("success=" + successCount);
            details.add("failure=" + result.failures().size());
            result.reportResults().forEach(report -> details.add(
                    report.reportName() + " -> " + (report.success() ? "OK rows=" + report.rowCount() : "FAIL " + report.errorMessage())
            ));
            if (!result.failures().isEmpty()) {
                return TaskOutcome.failure("Report generation completed with failures", result.workbookPath().toString(), details);
            }
            return TaskOutcome.success("Report generation completed", result.workbookPath().toString(), details);
        });
    }

    public TaskSnapshot startUpdateMonitorData(String configPath) {
        String parameters = joinParameters(configPath == null || configPath.isBlank() ? null : "config=" + configPath.trim());
        return submit("update-monitor-data", "Update Monitor Data", parameters, () -> {
            MonitorDataExportService.MonitorExportResult result = monitorDataExportService.export(configPath);
            long successCount = result.taskResults().stream().filter(MonitorDataExportService.TaskResult::success).count();
            long emptyCount = result.taskResults().stream().filter(MonitorDataExportService.TaskResult::empty).count();
            List<String> details = new ArrayList<>();
            details.add("tasks=" + result.taskResults().size());
            details.add("success=" + successCount);
            details.add("empty=" + emptyCount);
            details.add("failure=" + result.failures().size());
            result.taskResults().forEach(task -> details.add(
                    task.filename() + " <- " + task.viewName() + " (" + task.rowCount() + " rows" + (task.empty() ? ", empty" : "") + ")"
            ));
            if (!result.failures().isEmpty()) {
                details.addAll(result.failures());
                return TaskOutcome.failure("Monitor data export completed with failures", result.outputDirectory().toString(), details);
            }
            return TaskOutcome.success("Monitor data export completed", result.outputDirectory().toString(), details);
        });
    }

    private TaskSnapshot submit(WorkflowStep workflowStep, String parameterSummary, TaskAction action) {
        synchronized (lock) {
            validateWorkflowExecution(workflowStep);
        }
        return submit(workflowStep.taskType, workflowStep.displayName, parameterSummary, () -> {
            TaskOutcome outcome = action.run();
            synchronized (lock) {
                if (outcome.success) {
                    markWorkflowSuccess(workflowStep);
                } else {
                    markWorkflowFailure(workflowStep, outcome.summary);
                }
            }
            return outcome;
        });
    }

    private TaskSnapshot submit(String taskType, String displayName, String parameterSummary, TaskAction action) {
        TaskRun taskRun;
        synchronized (lock) {
            if (runningTask != null && runningTask.status == TaskStatus.RUNNING) {
                throw new IllegalStateException("Task already running: " + runningTask.displayName);
            }
            // 這裡刻意只允許單執行緒。因為所有任務共用同一批檔案路徑與 DuckDB，
            // 若平行執行會導致 race condition 與 workflow 狀態不一致。
            taskRun = new TaskRun(sequence.incrementAndGet(), taskType, displayName, emptyIfBlank(parameterSummary), LocalDateTime.now());
            runningTask = taskRun;
            history.add(0, taskRun);
            trimHistory();
        }

        executorService.submit(() -> executeTask(taskRun, action));
        return toSnapshot(taskRun);
    }

    private void executeTask(TaskRun taskRun, TaskAction action) {
        LOGGER.info("Starting UI task {}#{}", taskRun.taskType, taskRun.id);
        try {
            TaskOutcome outcome = Objects.requireNonNull(action.run(), "Task outcome must not be null");
            finishTask(taskRun, outcome);
        } catch (Exception exception) {
            LOGGER.error("UI task {}#{} failed", taskRun.taskType, taskRun.id, exception);
            TaskOutcome outcome = TaskOutcome.failure(
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                    null,
                    List.of()
            );
            finishTask(taskRun, outcome);
        }
    }

    private void finishTask(TaskRun taskRun, TaskOutcome outcome) {
        synchronized (lock) {
            taskRun.status = outcome.success ? TaskStatus.SUCCESS : TaskStatus.FAILED;
            taskRun.summary = outcome.summary;
            taskRun.outputPath = outcome.outputPath;
            taskRun.errorMessage = outcome.success ? null : outcome.summary;
            taskRun.detailLines = List.copyOf(outcome.detailLines);
            taskRun.finishedAt = LocalDateTime.now();
            if (runningTask == taskRun) {
                runningTask = null;
            }
        }
    }

    private void validateWorkflowExecution(WorkflowStep workflowStep) {
        WorkflowStep prerequisite = workflowStep.prerequisite;
        if (prerequisite == null) {
            return;
        }
        if (!completedSteps.containsKey(prerequisite)) {
            throw new IllegalStateException("Workflow order required: run " + prerequisite.displayName + " before " + workflowStep.displayName);
        }
    }

    private void markWorkflowSuccess(WorkflowStep workflowStep) {
        LocalDateTime now = LocalDateTime.now();
        completedSteps.put(workflowStep, now);
        failedSteps.remove(workflowStep);
        switch (workflowStep) {
            case DOCTOR -> {
                // doctor 一旦重新成功，代表新的檢查基準成立，下游步驟必須重新取得成功狀態，
                // 因為它們可能依賴剛剛修正過的環境或設定。
                completedSteps.remove(WorkflowStep.INGEST);
                completedSteps.remove(WorkflowStep.SYNC_VIEWS);
                completedSteps.remove(WorkflowStep.GENERATE_REPORT);
                failedSteps.remove(WorkflowStep.INGEST);
                failedSteps.remove(WorkflowStep.SYNC_VIEWS);
                failedSteps.remove(WorkflowStep.GENERATE_REPORT);
            }
            case INGEST -> {
                // 來源資料一旦更新，下游所有衍生結果都應視為失效。
                completedSteps.remove(WorkflowStep.SYNC_VIEWS);
                completedSteps.remove(WorkflowStep.GENERATE_REPORT);
                failedSteps.remove(WorkflowStep.SYNC_VIEWS);
                failedSteps.remove(WorkflowStep.GENERATE_REPORT);
            }
            case SYNC_VIEWS -> {
                // view 重新整理後，報表結果可能立刻改變，因此報表完成狀態要失效。
                completedSteps.remove(WorkflowStep.GENERATE_REPORT);
                failedSteps.remove(WorkflowStep.GENERATE_REPORT);
            }
            case GENERATE_REPORT -> {
                // 最終步驟，後面沒有其他相依節點需要清除。
            }
        }
    }

    private void markWorkflowFailure(WorkflowStep workflowStep, String summary) {
        failedSteps.put(workflowStep, summary == null || summary.isBlank() ? "Task failed" : summary);
    }

    private WorkflowStatus buildWorkflowStatus() {
        List<WorkflowStepView> steps = new ArrayList<>();
        for (WorkflowStep workflowStep : WorkflowStep.values()) {
            steps.add(toWorkflowStepView(workflowStep));
        }
        return new WorkflowStatus(steps);
    }

    private WorkflowStepView toWorkflowStepView(WorkflowStep workflowStep) {
        if (runningTask != null && workflowStep.taskType.equals(runningTask.taskType)) {
            return new WorkflowStepView(
                    workflowStep.taskType,
                    workflowStep.displayName,
                    workflowStep.order,
                    "RUNNING",
                    "In progress",
                    false
            );
        }

        if (completedSteps.containsKey(workflowStep)) {
            return new WorkflowStepView(
                    workflowStep.taskType,
                    workflowStep.displayName,
                    workflowStep.order,
                    "COMPLETED",
                    "Completed at " + formatTime(completedSteps.get(workflowStep)),
                    true
            );
        }

        if (workflowStep.prerequisite != null && !completedSteps.containsKey(workflowStep.prerequisite)) {
            return new WorkflowStepView(
                    workflowStep.taskType,
                    workflowStep.displayName,
                    workflowStep.order,
                    "LOCKED",
                    "Requires " + workflowStep.prerequisite.displayName,
                    false
            );
        }

        String failureMessage = failedSteps.get(workflowStep);
        if (failureMessage != null && !failureMessage.isBlank()) {
            return new WorkflowStepView(
                    workflowStep.taskType,
                    workflowStep.displayName,
                    workflowStep.order,
                    "FAILED",
                    failureMessage,
                    true
            );
        }

        String message = workflowStep.prerequisite == null ? "Start here" : "Ready";
        return new WorkflowStepView(
                workflowStep.taskType,
                workflowStep.displayName,
                workflowStep.order,
                "READY",
                message,
                true
        );
    }

    private void trimHistory() {
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
    }

    private TaskSnapshot toSnapshot(TaskRun taskRun) {
        LocalDateTime finishedAt = taskRun.finishedAt;
        long durationMillis = Duration.between(taskRun.startedAt, finishedAt != null ? finishedAt : LocalDateTime.now()).toMillis();
        return new TaskSnapshot(
                taskRun.id,
                taskRun.taskType,
                taskRun.displayName,
                taskRun.status.name(),
                taskRun.parameterSummary,
                taskRun.summary,
                taskRun.outputPath,
                taskRun.errorMessage,
                formatTime(taskRun.startedAt),
                finishedAt == null ? null : formatTime(finishedAt),
                durationMillis,
                taskRun.detailLines
        );
    }

    private String formatTime(LocalDateTime value) {
        return value.format(TIME_FORMAT);
    }

    private String joinParameters(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value);
            }
        }
        return String.join(", ", parts);
    }

    private String emptyIfBlank(String value) {
        return value == null ? "" : value;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private interface TaskAction {
        TaskOutcome run() throws Exception;
    }

    private static final class TaskRun {
        private final long id;
        private final String taskType;
        private final String displayName;
        private final String parameterSummary;
        private final LocalDateTime startedAt;
        private TaskStatus status;
        private String summary;
        private String outputPath;
        private String errorMessage;
        private LocalDateTime finishedAt;
        private List<String> detailLines = List.of();

        private TaskRun(long id, String taskType, String displayName, String parameterSummary, LocalDateTime startedAt) {
            this.id = id;
            this.taskType = taskType;
            this.displayName = displayName;
            this.parameterSummary = parameterSummary;
            this.startedAt = startedAt;
            this.status = TaskStatus.RUNNING;
            this.summary = "Task started";
        }
    }

    private enum TaskStatus {
        RUNNING,
        SUCCESS,
        FAILED
    }

    private enum WorkflowStep {
        DOCTOR(1, "doctor", "Doctor", null),
        INGEST(2, "ingest", "Ingest", DOCTOR),
        SYNC_VIEWS(3, "sync-views", "Sync Views", INGEST),
        GENERATE_REPORT(4, "generate-report", "Generate Report", SYNC_VIEWS);

        private final int order;
        private final String taskType;
        private final String displayName;
        private final WorkflowStep prerequisite;

        WorkflowStep(int order, String taskType, String displayName, WorkflowStep prerequisite) {
            this.order = order;
            this.taskType = taskType;
            this.displayName = displayName;
            this.prerequisite = prerequisite;
        }
    }

    private record TaskOutcome(boolean success, String summary, String outputPath, List<String> detailLines) {

        private static TaskOutcome success(String summary, String outputPath, List<String> detailLines) {
            return new TaskOutcome(true, summary, outputPath, detailLines == null ? List.of() : detailLines);
        }

        private static TaskOutcome failure(String summary, String outputPath, List<String> detailLines) {
            return new TaskOutcome(false, summary, outputPath, detailLines == null ? List.of() : detailLines);
        }
    }

    public record TaskDashboard(TaskSnapshot runningTask, List<TaskSnapshot> recentTasks, WorkflowStatus workflowStatus) { }

    public record WorkflowStatus(List<WorkflowStepView> steps) { }

    public record WorkflowStepView(
            String taskType,
            String displayName,
            int order,
            String status,
            String message,
            boolean executable
    ) { }

    public record TaskSnapshot(
            long id,
            String taskType,
            String displayName,
            String status,
            String parameterSummary,
            String summary,
            String outputPath,
            String errorMessage,
            String startedAt,
            String finishedAt,
            long durationMillis,
            List<String> detailLines
    ) { }
}
