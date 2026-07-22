package com.wrongbook.controller;

import com.wrongbook.entity.WrongQuestionFile;
import com.wrongbook.service.WrongQuestionFileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class WrongQuestionFileController {

    @Autowired
    private WrongQuestionFileService fileService;

    @GetMapping("/wrong-questions/{id}/files")
    public List<WrongQuestionFile> list(@PathVariable Long id) {
        return fileService.findByWrongQuestionId(id);
    }

    @PostMapping(value = "/wrong-questions/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WrongQuestionFile upload(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) {
        return fileService.upload(id, file, type);
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> delete(@PathVariable Long fileId) {
        fileService.delete(fileId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/files/{fileId}/content")
    public ResponseEntity<Resource> getContent(@PathVariable Long fileId) {
        WrongQuestionFile file = fileService.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found: " + fileId));
        Path filePath = fileService.resolveFilePath(file);
        Resource resource = new FileSystemResource(filePath);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (file.getContentType() != null) {
            try {
                mediaType = MediaType.parseMediaType(file.getContentType());
            } catch (Exception ignored) { }
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeFileName(file.getOriginalName()) + "\"")
                .body(resource);
    }

    private String safeFileName(String name) {
        if (name == null) return "file";
        return name.replaceAll("[\\r\\n\"]", "_");
    }
}
