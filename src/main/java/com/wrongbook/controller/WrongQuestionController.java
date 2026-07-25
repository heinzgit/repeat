package com.wrongbook.controller;

import com.wrongbook.entity.WrongQuestion;
import com.wrongbook.service.PdfExportService;
import com.wrongbook.service.WrongQuestionFileService;
import com.wrongbook.service.WrongQuestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wrong-questions")
@CrossOrigin(origins = "*")
public class WrongQuestionController {

    @Autowired
    private WrongQuestionService wrongQuestionService;

    @Autowired
    private WrongQuestionFileService fileService;

    @Autowired
    private PdfExportService pdfExportService;

    @GetMapping
    public List<WrongQuestion> getAll() {
        return wrongQuestionService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<WrongQuestion> getById(@PathVariable Long id) {
        return wrongQuestionService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WrongQuestion create(
            @RequestParam("grade") String grade,
            @RequestParam("subject") String subject,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "questionNo", required = false) String questionNo,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam("wrongDate") String wrongDate,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "answerText", required = false) String answerText,
            @RequestParam(value = "questionFiles", required = false) MultipartFile[] questionFiles,
            @RequestParam(value = "answerFiles", required = false) MultipartFile[] answerFiles) {

        WrongQuestion wq = new WrongQuestion();
        wq.setGrade(grade);
        wq.setSubject(subject);
        wq.setSource(source);
        wq.setQuestionNo(questionNo);
        wq.setCategory(category);
        wq.setWrongDate(LocalDate.parse(wrongDate));
        wq.setStatus(status != null && !status.isEmpty() ? status : "错误");
        wq.setAnswerText(answerText);

        WrongQuestion saved = wrongQuestionService.save(wq);

        if (questionFiles != null) {
            for (MultipartFile file : questionFiles) {
                if (!file.isEmpty()) {
                    fileService.upload(saved.getId(), file, "question");
                }
            }
        }
        if (answerFiles != null) {
            for (MultipartFile file : answerFiles) {
                if (!file.isEmpty()) {
                    fileService.upload(saved.getId(), file, "answer");
                }
            }
        }

        return saved;
    }

    @PostMapping("/import")
    public List<WrongQuestion> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        return wrongQuestionService.importFromCsv(content);
    }

    @PutMapping("/{id}")
    public WrongQuestion update(@PathVariable Long id, @RequestBody WrongQuestion wrongQuestion) {
        return wrongQuestionService.update(id, wrongQuestion);
    }

    @PostMapping(value = "/export-pdf", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> exportPdf(@Valid @RequestBody ExportPdfRequest request) {
        try {
            byte[] pdf = pdfExportService.buildPaper(request.getWrongQuestionIds());
            String filename = "wrong-question-paper-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                    + ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setCacheControl("no-store");
            return ResponseEntity.ok().headers(headers).body(pdf);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (PdfExportService.PdfExportException e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        wrongQuestionService.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("导出参数无效");
        return error(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("message", message == null ? "请求失败" : message));
    }

    public static class ExportPdfRequest {
        @NotEmpty(message = "请至少选择一道错题")
        @Size(
                max = PdfExportService.MAX_QUESTIONS,
                message = "一次最多生成 " + PdfExportService.MAX_QUESTIONS + " 道错题")
        private List<Long> wrongQuestionIds;

        public List<Long> getWrongQuestionIds() {
            return wrongQuestionIds;
        }

        public void setWrongQuestionIds(List<Long> wrongQuestionIds) {
            this.wrongQuestionIds = wrongQuestionIds;
        }
    }
}
