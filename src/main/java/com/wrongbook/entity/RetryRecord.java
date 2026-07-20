package com.wrongbook.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "retry_record")
public class RetryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wrong_question_id", nullable = false)
    @JsonIgnore
    private WrongQuestion wrongQuestion;

    @Column(name = "retry_date")
    private LocalDate retryDate;

    @Column(length = 20)
    private String result;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public WrongQuestion getWrongQuestion() { return wrongQuestion; }
    public void setWrongQuestion(WrongQuestion wrongQuestion) { this.wrongQuestion = wrongQuestion; }

    public LocalDate getRetryDate() { return retryDate; }
    public void setRetryDate(LocalDate retryDate) { this.retryDate = retryDate; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
