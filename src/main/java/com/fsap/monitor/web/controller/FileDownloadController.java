package com.fsap.monitor.web.controller;

import java.nio.file.Path;

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
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }
}
