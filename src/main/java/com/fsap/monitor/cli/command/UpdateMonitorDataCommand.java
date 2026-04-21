package com.fsap.monitor.cli.command;

import org.springframework.stereotype.Component;

import com.fsap.monitor.core.monitor.MonitorDataExportService;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "update-monitor-data", mixinStandardHelpOptions = true, description = "Export monitor data directly from DuckDB")
public class UpdateMonitorDataCommand implements Runnable {

    @Option(names = "--config", description = "Override monitor config path")
    String configPath;

    private final MonitorDataExportService monitorDataExportService;

    public UpdateMonitorDataCommand(MonitorDataExportService monitorDataExportService) {
        this.monitorDataExportService = monitorDataExportService;
    }

    @Override
    public void run() {
        var result = monitorDataExportService.export(configPath);
        long successCount = result.taskResults().stream().filter(MonitorDataExportService.TaskResult::success).count();
        long emptyCount = result.taskResults().stream().filter(MonitorDataExportService.TaskResult::empty).count();
        System.out.printf(
                "Monitor data export completed: tasks=%d success=%d empty=%d failure=%d%n",
                result.taskResults().size(),
                successCount,
                emptyCount,
                result.failures().size()
        );
        System.out.println("Config: " + result.configPath());
        System.out.println("Output: " + result.outputDirectory());
    }
}
