package com.wrongbook.service;

import com.wrongbook.entity.WrongQuestion;
import com.wrongbook.repository.WrongQuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class WrongQuestionService {

    @Autowired
    private WrongQuestionRepository wrongQuestionRepository;

    public List<WrongQuestion> findAll() {
        return wrongQuestionRepository.findAll();
    }

    public Optional<WrongQuestion> findById(Long id) {
        return wrongQuestionRepository.findById(id);
    }

    public WrongQuestion save(WrongQuestion wrongQuestion) {
        return wrongQuestionRepository.save(wrongQuestion);
    }

    @Transactional
    public WrongQuestion update(Long id, WrongQuestion wrongQuestion) {
        Optional<WrongQuestion> existing = wrongQuestionRepository.findById(id);
        if (existing.isPresent()) {
            WrongQuestion updated = existing.get();
            updated.setGrade(wrongQuestion.getGrade());
            updated.setSubject(wrongQuestion.getSubject());
            updated.setSource(wrongQuestion.getSource());
            updated.setQuestionNo(wrongQuestion.getQuestionNo());
            updated.setCategory(wrongQuestion.getCategory());
            updated.setWrongDate(wrongQuestion.getWrongDate());
            updated.setStatus(wrongQuestion.getStatus());
            return wrongQuestionRepository.save(updated);
        }
        throw new RuntimeException("WrongQuestion not found with id: " + id);
    }

    public void deleteById(Long id) {
        wrongQuestionRepository.deleteById(id);
    }

    @Transactional
    public List<WrongQuestion> importFromCsv(String csvContent) {
        List<WrongQuestion> imported = new ArrayList<>();
        String[] lines = csvContent.split("\n");

        for (int i = 1; i < lines.length; i++) { // Skip header
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split(",");
            if (parts.length >= 6) {
                WrongQuestion wq = new WrongQuestion();
                wq.setGrade(parts[0].trim());
                wq.setSubject(parts[1].trim());
                wq.setSource(parts[2].trim());
                wq.setQuestionNo(parts[3].trim());
                wq.setCategory(parts[4].trim());
                wq.setWrongDate(LocalDate.parse(parts[5].trim()));
                wq.setStatus("错误");
                imported.add(wrongQuestionRepository.save(wq));
            }
        }
        return imported;
    }
}
