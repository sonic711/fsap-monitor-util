package com.fsap.monitor.cli.command;

import org.springframework.stereotype.Component;

import com.fsap.monitor.core.viewsync.ViewSyncService;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
/**
 * 重新建立 DuckDB views 的 CLI 入口。
 */
@Command(name = "sync-views", mixinStandardHelpOptions = true, description = "Load SQL views into DuckDB")
public class SyncViewsCommand implements Runnable {

    @Option(names = "--max-rounds", description = "Override view retry rounds")
    Integer maxRounds;

    @Option(names = "--fail-fast", description = "Stop on the first view load error")
    boolean failFast;

    private final ViewSyncService viewSyncService;

    public SyncViewsCommand(ViewSyncService viewSyncService) {
        this.viewSyncService = viewSyncService;
    }

    @Override
    public void run() {
        var result = viewSyncService.syncViews(maxRounds, failFast);
        System.out.printf("View sync completed: success=%d failure=%d%n", result.successCount(), result.failureCount());
        result.failures().forEach(failure -> System.out.println(" - " + failure));
        if (result.failureCount() > 0) {
            throw new IllegalStateException("View sync finished with failures");
        }
    }
}
