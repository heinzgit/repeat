package com.wrongbook.repository;

import com.wrongbook.entity.WrongQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WrongQuestionRepository extends JpaRepository<WrongQuestion, Long> {
    List<WrongQuestion> findByGrade(String grade);
    List<WrongQuestion> findBySubject(String subject);
    List<WrongQuestion> findByStatus(String status);
}
