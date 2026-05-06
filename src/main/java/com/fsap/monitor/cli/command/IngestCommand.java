package com.fsap.monitor.cli.command;

import org.springframework.stereotype.Component;

import com.fsap.monitor.core.ingest.IngestService;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
/**
 * Excel -> JSONL.GZ 匯入步驟的 CLI 入口。
 */
@Command(name = "ingest", mixinStandardHelpOptions = true, description = "Convert Excel input into JSONL.gz source lake files")
public class IngestCommand implements Runnable {

    @Option(names = "--force", description = "Force rebuild existing output")
    boolean force;

    @Option(names = "--limit", description = "Process latest N files only")
    Integer limit;

    @Option(names = "--date", description = "Process a single date in YYYYMMDD format")
    String date;

    private final IngestService ingestService;

    public IngestCommand(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Override
    public void run() {
        var result = ingestService.ingest(force, limit, date);
        System.out.printf(
                "Ingest completed: processed=%d skipped=%d outputs=%d failure=%d%n",
                result.processedFiles(),
                result.skippedFiles(),
                result.writtenFiles(),
                result.failures().size()
        );
    }
}
