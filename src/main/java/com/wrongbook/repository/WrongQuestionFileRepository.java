package com.wrongbook.repository;

import com.wrongbook.entity.WrongQuestionFile;
import com.wrongbook.entity.WrongQuestionFile.FileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WrongQuestionFileRepository extends JpaRepository<WrongQuestionFile, Long> {
    List<WrongQuestionFile> findByWrongQuestionId(Long wrongQuestionId);
    List<WrongQuestionFile> findByWrongQuestionIdAndFileType(Long wrongQuestionId, FileType fileType);
    List<WrongQuestionFile> findByWrongQuestionIdInAndFileTypeOrderByWrongQuestionIdAscIdAsc(
            List<Long> wrongQuestionIds, FileType fileType);
}
