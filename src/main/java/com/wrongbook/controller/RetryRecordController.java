package com.wrongbook.controller;

import com.wrongbook.entity.RetryRecord;
import com.wrongbook.service.RetryRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RetryRecordController {

    @Autowired
    private RetryRecordService retryRecordService;

    @GetMapping("/wrong-questions/{id}/retry-records")
    public List<RetryRecord> getByWrongQuestionId(@PathVariable Long id) {
        return retryRecordService.findByWrongQuestionId(id);
    }

    @PostMapping("/retry-records")
    public RetryRecord create(@RequestBody RetryRecordRequest request) {
        RetryRecord record = new RetryRecord();
        record.setRetryDate(LocalDate.parse(request.getRetryDate()));
        record.setResult(request.getResult());
        return retryRecordService.save(record, request.getWrongQuestionId());
    }

    @DeleteMapping("/retry-records/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        retryRecordService.deleteById(id);
        return ResponseEntity.ok().build();
    }

    public static class RetryRecordRequest {
        private Long wrongQuestionId;
        private String retryDate;
        private String result;

        public Long getWrongQuestionId() { return wrongQuestionId; }
        public void setWrongQuestionId(Long wrongQuestionId) { this.wrongQuestionId = wrongQuestionId; }

        public String getRetryDate() { return retryDate; }
        public void setRetryDate(String retryDate) { this.retryDate = retryDate; }

        public String getResult() { return result; }
        public void setResult(String result) { this.result = result; }
    }
}
