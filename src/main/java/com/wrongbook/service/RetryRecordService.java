package com.wrongbook.service;

import com.wrongbook.entity.RetryRecord;
import com.wrongbook.entity.WrongQuestion;
import com.wrongbook.repository.RetryRecordRepository;
import com.wrongbook.repository.WrongQuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RetryRecordService {

    @Autowired
    private RetryRecordRepository retryRecordRepository;

    @Autowired
    private WrongQuestionRepository wrongQuestionRepository;

    public List<RetryRecord> findByWrongQuestionId(Long wrongQuestionId) {
        return retryRecordRepository.findByWrongQuestionId(wrongQuestionId);
    }

    @Transactional
    public RetryRecord save(RetryRecord retryRecord, Long wrongQuestionId) {
        Optional<WrongQuestion> wq = wrongQuestionRepository.findById(wrongQuestionId);
        if (wq.isEmpty()) {
            throw new RuntimeException("WrongQuestion not found with id: " + wrongQuestionId);
        }
        retryRecord.setWrongQuestion(wq.get());

        // Update wrong question status based on retry result
        WrongQuestion wrongQuestion = wq.get();
        String result = retryRecord.getResult();

        // Check if there's already a wrong retry record
        List<RetryRecord> existingRecords = retryRecordRepository.findByWrongQuestionId(wrongQuestionId);
        boolean hasWrongRecord = existingRecords.stream()
                .anyMatch(r -> "错误".equals(r.getResult()));

        if ("通过".equals(result)) {
            if (hasWrongRecord && "通过".equals(wrongQuestion.getStatus())) {
                // Already has a wrong record and status is "通过", change to "反复错后通过"
                wrongQuestion.setStatus("反复错后通过");
            } else if ("反复错".equals(wrongQuestion.getStatus())) {
                wrongQuestion.setStatus("反复错后通过");
            } else {
                wrongQuestion.setStatus("通过");
            }
        } else if ("错误".equals(result)) {
            if ("错误".equals(wrongQuestion.getStatus())) {
                // 错题状态为"错误"且重做结果仍为"错误",改为"反复错"
                wrongQuestion.setStatus("反复错");
            } else if ("通过".equals(wrongQuestion.getStatus()) || "反复错后通过".equals(wrongQuestion.getStatus())) {
                wrongQuestion.setStatus("反复错");
            } else if (!"反复错".equals(wrongQuestion.getStatus())) {
                wrongQuestion.setStatus("错误");
            }
        }
        wrongQuestionRepository.save(wrongQuestion);

        return retryRecordRepository.save(retryRecord);
    }

    public void deleteById(Long id) {
        retryRecordRepository.deleteById(id);
    }

    @Transactional
    public List<RetryRecord> saveBatch(List<Long> wrongQuestionIds, LocalDate retryDate, String result) {
        List<RetryRecord> saved = new ArrayList<>();
        for (Long id : wrongQuestionIds) {
            RetryRecord record = new RetryRecord();
            record.setRetryDate(retryDate);
            record.setResult(result);
            saved.add(save(record, id));
        }
        return saved;
    }
}
