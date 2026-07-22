package com.wrongbook.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "wrong_question")
public class WrongQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String grade;

    @Column(length = 50)
    private String subject;

    @Column(length = 100)
    private String source;

    @Column(length = 50)
    private String questionNo;

    @Column(length = 20)
    private String category;

    @Column(name = "wrong_date")
    private LocalDate wrongDate;

    @Column(length = 20)
    private String status;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "wrongQuestion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RetryRecord> retryRecords;

    @OneToMany(mappedBy = "wrongQuestion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WrongQuestionFile> files;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getQuestionNo() { return questionNo; }
    public void setQuestionNo(String questionNo) { this.questionNo = questionNo; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDate getWrongDate() { return wrongDate; }
    public void setWrongDate(LocalDate wrongDate) { this.wrongDate = wrongDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<RetryRecord> getRetryRecords() { return retryRecords; }
    public void setRetryRecords(List<RetryRecord> retryRecords) { this.retryRecords = retryRecords; }

    public List<WrongQuestionFile> getFiles() { return files; }
    public void setFiles(List<WrongQuestionFile> files) { this.files = files; }
}
