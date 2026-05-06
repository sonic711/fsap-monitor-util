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
/**
 * 處理瀏覽器上傳的 Excel 來源檔，供 ingest 步驟使用。
 *
 * <p>上傳流程會先寫入暫存檔，等傳輸成功後才搬到正式檔名，
 * 以避免後續 ingest 掃到半套的 workbook。
 */
public class InputUploadService {

    private final ProjectPathService projectPathService;

    public InputUploadService(ProjectPathService projectPathService) {
        this.projectPathService = projectPathService;
    }

    /**
     * 將一個或多個上傳的 Excel 檔案存入 {@code 01_excel_input}。
     */
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
            // 有些檔案系統或情境不支援 atomic move，這時退回一般 replace 仍可保證正確性。
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void cleanupTemp(Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (Exception ignored) {
            // 暫存檔清理失敗不影響主流程，因此直接忽略。
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

    /**
     * 一批上傳完成後回傳給 UI 的摘要。
     */
    public record UploadBatchResult(String inputDirectory, List<UploadedFile> files) { }

    /**
     * 單一已保存檔案的中繼資料。
     */
    public record UploadedFile(String filename, String storedPath, long sizeBytes) { }
}
