package com.wrongbook.repository;

import com.wrongbook.entity.WrongQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WrongQuestionRepository extends JpaRepository<WrongQuestion, Long> {
    List<WrongQuestion> findByGrade(String grade);
    List<WrongQuestion> findBySubject(String subject);
    List<WrongQuestion> findByStatus(String status);

    @Modifying
    @Query("UPDATE WrongQuestion w SET w.paperGenerated = true WHERE w.id IN :ids")
    void markPaperGenerated(@Param("ids") List<Long> ids);

    @Modifying
    @Query("UPDATE WrongQuestion w SET w.paperGenerated = false WHERE w.id = :id")
    void clearPaperGenerated(@Param("id") Long id);
}
