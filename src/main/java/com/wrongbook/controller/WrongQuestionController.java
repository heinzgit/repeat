package com.wrongbook.controller;

import com.wrongbook.entity.WrongQuestion;
import com.wrongbook.service.WrongQuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/wrong-questions")
@CrossOrigin(origins = "*")
public class WrongQuestionController {

    @Autowired
    private WrongQuestionService wrongQuestionService;

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

    @PostMapping
    public WrongQuestion create(@RequestBody WrongQuestion wrongQuestion) {
        return wrongQuestionService.save(wrongQuestion);
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
