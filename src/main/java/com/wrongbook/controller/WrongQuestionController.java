package com.wrongbook.controller;

import com.wrongbook.entity.WrongQuestion;
import com.wrongbook.service.WrongQuestionFileService;
import com.wrongbook.service.WrongQuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/wrong-questions")
@CrossOrigin(origins = "*")
public class WrongQuestionController {

    @Autowired
    private WrongQuestionService wrongQuestionService;

    @Autowired
    private WrongQuestionFileService fileService;

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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        wrongQuestionService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
