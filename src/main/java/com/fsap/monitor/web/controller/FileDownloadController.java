package com.fsap.monitor.web.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.net.URLEncoder;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.fsap.monitor.core.artifact.ArtifactBrowseService;

@RestController
public class FileDownloadController {

    private final ArtifactBrowseService artifactBrowseService;

    public FileDownloadController(ArtifactBrowseService artifactBrowseService) {
        this.artifactBrowseService = artifactBrowseService;
    }

    @GetMapping("/downloads/file")
    public ResponseEntity<Resource> downloadFile(@RequestParam("path") String path) {
        try {
            Path file = artifactBrowseService.resolveDownloadableFile(path);
            Resource resource = new FileSystemResource(file);
            String filename = file.getFileName().toString();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(filename))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    private String buildContentDisposition(String filename) {
        String asciiFallback = filename
                .replaceAll("[^\\x20-\\x7E]", "_")
                .replace("\"", "_");
        if (asciiFallback.isBlank()) {
            asciiFallback = "download";
        }
        return "attachment; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encodeUtf8(filename);
    }

    private String encodeUtf8(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
