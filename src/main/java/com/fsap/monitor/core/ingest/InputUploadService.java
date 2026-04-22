package com.fsap.monitor.core.ingest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fsap.monitor.core.service.ProjectPathService;

@Service
public class InputUploadService {

    private final ProjectPathService projectPathService;

    public InputUploadService(ProjectPathService projectPathService) {
        this.projectPathService = projectPathService;
    }

    public UploadBatchResult uploadExcelFiles(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No files uploaded");
        }

        Path inputDir = projectPathService.inputDir();
        List<UploadedFile> uploadedFiles = new ArrayList<>();

        try {
            Files.createDirectories(inputDir);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to prepare input directory: " + inputDir, exception);
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }

            String filename = sanitizeFilename(file.getOriginalFilename());
            validateExcelFilename(filename);

            Path target = inputDir.resolve(filename);
            Path temp = inputDir.resolve(filename + ".uploading");
            try {
                file.transferTo(temp);
                moveIntoPlace(temp, target);
                uploadedFiles.add(new UploadedFile(filename, target.toString(), file.getSize()));
            } catch (Exception exception) {
                cleanupTemp(temp);
                throw new IllegalStateException("Unable to store uploaded file: " + filename, exception);
            }
        }

        if (uploadedFiles.isEmpty()) {
            throw new IllegalArgumentException("No valid .xlsx files uploaded");
        }

        return new UploadBatchResult(inputDir.toString(), uploadedFiles);
    }

    private void moveIntoPlace(Path temp, Path target) throws Exception {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void cleanupTemp(Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (Exception ignored) {
            // Ignore cleanup failures for temp upload artifacts.
        }
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Uploaded file is missing a filename");
        }
        String normalized = Path.of(originalFilename).getFileName().toString().trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Uploaded file is missing a filename");
        }
        return normalized;
    }

    private void validateExcelFilename(String filename) {
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Only .xlsx files are allowed: " + filename);
        }
    }

    public record UploadBatchResult(String inputDirectory, List<UploadedFile> files) { }

    public record UploadedFile(String filename, String storedPath, long sizeBytes) { }
}
