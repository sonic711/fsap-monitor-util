package com.fsap.monitor.core.task;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import com.fsap.monitor.core.report.ReportGenerationService;
import com.fsap.monitor.core.service.EnvironmentCheckService;
import com.fsap.monitor.core.service.EnvironmentCheckService.CheckResult;
import com.fsap.monitor.core.viewsync.ViewSyncService;

@Service
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

    public TaskDashboard dashboard(int limit) {
        synchronized (lock) {
            List<TaskSnapshot> snapshots = history.stream()
                    .limit(Math.max(limit, 1))
                    .map(this::toSnapshot)
                    .toList();
            return new TaskDashboard(
                    runningTask == null ? null : toSnapshot(runningTask),
                    snapshots
            );
        }
    }

    public TaskSnapshot startDoctor() {
        return submit("doctor", "Doctor", "Run environment health checks", () -> {
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
        return submit("sync-views", "Sync Views", parameters, () -> {
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
        return submit("ingest", "Ingest", parameters, () -> {
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

    public TaskSnapshot startGenerateReport(String timestamp, boolean continueOnError) {
        String parameters = joinParameters(
                timestamp == null || timestamp.isBlank() ? null : "timestamp=" + timestamp.trim(),
                "continueOnError=" + continueOnError
        );
        return submit("generate-report", "Generate Report", parameters, () -> {
            ReportGenerationService.ReportGenerationResult result = reportGenerationService.generate(timestamp, continueOnError);
            long successCount = result.reportResults().stream()
                    .filter(ReportGenerationService.ReportFileResult::success)
                    .count();
            List<String> details = new ArrayList<>();
            details.add("timestamp=" + result.timestamp());
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

    private TaskSnapshot submit(String taskType, String displayName, String parameterSummary, TaskAction action) {
        TaskRun taskRun;
        synchronized (lock) {
            if (runningTask != null && runningTask.status == TaskStatus.RUNNING) {
                throw new IllegalStateException("Task already running: " + runningTask.displayName);
            }
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

    private record TaskOutcome(boolean success, String summary, String outputPath, List<String> detailLines) {

        private static TaskOutcome success(String summary, String outputPath, List<String> detailLines) {
            return new TaskOutcome(true, summary, outputPath, detailLines == null ? List.of() : detailLines);
        }

        private static TaskOutcome failure(String summary, String outputPath, List<String> detailLines) {
            return new TaskOutcome(false, summary, outputPath, detailLines == null ? List.of() : detailLines);
        }
    }

    public record TaskDashboard(TaskSnapshot runningTask, List<TaskSnapshot> recentTasks) { }

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
