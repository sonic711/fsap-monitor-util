package com.fsap.monitor.core.artifact;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.fsap.monitor.core.service.ProjectPathService;

/**
 * Enumerates downloadable artifacts shown by the UI.
 *
 * <p>This service keeps filesystem traversal and download path validation out
 * of controllers, which makes it the central place to adjust artifact exposure.
 */
@Service
public class ArtifactBrowseService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProjectPathService projectPathService;

    public ArtifactBrowseService(ProjectPathService projectPathService) {
        this.projectPathService = projectPathService;
    }

    public List<ReportBatchView> loadRecentReportBatches(int limit) {
        Path outputDir = projectPathService.reportOutputDir();
        if (!Files.isDirectory(outputDir)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(outputDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> !"monitor-data".equals(path.getFileName().toString()))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .limit(limit)
                    .map(this::toBatchView)
                    .collect(Collectors.toList());
        } catch (Exception exception) {
            return List.of();
        }
    }

    public List<FileView> loadMonitorDataFiles() {
        Path monitorDir = projectPathService.reportOutputDir().resolve("monitor-data");
        return listFiles(monitorDir, 20);
    }

    public List<FileView> loadInputExcelFiles() {
        Path inputDir = projectPathService.inputDir();
        if (!Files.isDirectory(inputDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(inputDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".xlsx"))
                    .filter(path -> !path.getFileName().toString().startsWith("~$"))
                    .sorted(Comparator
                            .comparing((Path path) -> path.getFileName().toString(), Comparator.reverseOrder())
                            .thenComparing(this::safeLastModified, Comparator.reverseOrder()))
                    .map(path -> toFileView(path, inputDir))
                    .collect(Collectors.toList());
        } catch (Exception exception) {
            return List.of();
        }
    }

    public List<FileView> loadLatestReportFiles(Path batchDir, int limit) {
        return listFiles(batchDir, limit);
    }

    public Path resolveDownloadableFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Path must not be blank");
        }
        Path reportRoot = projectPathService.reportOutputDir().toAbsolutePath().normalize();
        Path resolved = reportRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(reportRoot)) {
            throw new IllegalArgumentException("Path is outside report output directory");
        }
        if (!Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("File not found: " + relativePath);
        }
        return resolved;
    }

    private ReportBatchView toBatchView(Path batchDir) {
        return new ReportBatchView(
                batchDir.getFileName().toString(),
                projectPathService.reportOutputDir().relativize(batchDir).toString().replace("\\", "/"),
                formatTimestamp(batchDir),
                loadLatestReportFiles(batchDir, 8)
        );
    }

    private List<FileView> listFiles(Path directory, int limit) {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith("~$"))
                    .sorted(Comparator.comparing(this::safeLastModified).reversed()
                            .thenComparing(path -> path.getFileName().toString()))
                    .limit(limit)
                    .map(path -> toFileView(path, projectPathService.reportOutputDir()))
                    .collect(Collectors.toList());
        } catch (Exception exception) {
            return List.of();
        }
    }

    private FileView toFileView(Path file, Path relativeRoot) {
        try {
            return new FileView(
                    file.getFileName().toString(),
                    relativeRoot.relativize(file).toString().replace("\\", "/"),
                    Files.size(file),
                    formatTimestamp(file)
            );
        } catch (Exception exception) {
            return new FileView(file.getFileName().toString(), file.getFileName().toString(), 0L, "-");
        }
    }

    private String formatTimestamp(Path path) {
        try {
            return TIME_FORMAT.format(Files.getLastModifiedTime(path).toInstant().atZone(ZoneId.systemDefault()));
        } catch (Exception exception) {
            return "-";
        }
    }

    private FileTime safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (Exception exception) {
            return FileTime.fromMillis(0);
        }
    }

    public record ReportBatchView(String batchName, String relativePath, String modifiedAt, List<FileView> files) { }

    public record FileView(String filename, String relativePath, long sizeBytes, String modifiedAt) { }
}
