package com.wrongbook.service;

import com.wrongbook.entity.WrongQuestion;
import com.wrongbook.entity.WrongQuestionFile;
import com.wrongbook.entity.WrongQuestionFile.FileType;
import com.wrongbook.repository.WrongQuestionFileRepository;
import com.wrongbook.repository.WrongQuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WrongQuestionFileService {

    @Autowired
    private WrongQuestionFileRepository fileRepository;

    @Autowired
    private WrongQuestionRepository wrongQuestionRepository;

    @Value("${app.files.dir}")
    private String filesDir;

    public List<WrongQuestionFile> findByWrongQuestionId(Long wrongQuestionId) {
        return fileRepository.findByWrongQuestionId(wrongQuestionId);
    }

    public Optional<WrongQuestionFile> findById(Long id) {
        return fileRepository.findById(id);
    }

    public Path getBaseDir() {
        return Paths.get(filesDir).toAbsolutePath().normalize();
    }

    public Path resolveFilePath(WrongQuestionFile file) {
        return getBaseDir().resolve(file.getStoredPath()).normalize();
    }

    @Transactional
    public WrongQuestionFile upload(Long wrongQuestionId, MultipartFile multipartFile, String typeStr) {
        WrongQuestion wq = wrongQuestionRepository.findById(wrongQuestionId)
                .orElseThrow(() -> new RuntimeException("WrongQuestion not found with id: " + wrongQuestionId));

        FileType fileType;
        try {
            fileType = FileType.valueOf(typeStr.toUpperCase());
        } catch (Exception e) {
            throw new RuntimeException("Invalid file type: " + typeStr + ", must be 'question' or 'answer'");
        }

        String contentType = multipartFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new RuntimeException("Only image files are allowed");
        }

        String originalName = multipartFile.getOriginalFilename();
        String ext = getExtension(originalName);
        String uuid = UUID.randomUUID().toString().substring(0, 8);

        String relativeDir = String.join("/",
                sanitize(wq.getGrade()),
                sanitize(wq.getSubject()),
                sanitize(wq.getSource()));

        String filename = String.format("%d_%s_%s_%s_%s_%s_%s_%s%s",
                wq.getId(),
                sanitize(wq.getGrade()),
                sanitize(wq.getSubject()),
                sanitize(wq.getSource()),
                sanitize(wq.getQuestionNo()),
                sanitize(wq.getCategory()),
                fileType.name().toLowerCase(),
                uuid,
                ext);

        Path dirPath = getBaseDir().resolve(relativeDir);
        Path filePath = dirPath.resolve(filename);

        try {
            Files.createDirectories(dirPath);
            multipartFile.transferTo(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file: " + e.getMessage(), e);
        }

        String storedPath = relativeDir + "/" + filename;

        WrongQuestionFile entity = new WrongQuestionFile();
        entity.setWrongQuestion(wq);
        entity.setFileType(fileType);
        entity.setOriginalName(originalName);
        entity.setStoredPath(storedPath);
        entity.setContentType(contentType);
        entity.setSizeBytes(multipartFile.getSize());

        return fileRepository.save(entity);
    }

    @Transactional
    public void delete(Long fileId) {
        WrongQuestionFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found with id: " + fileId));

        Path filePath = resolveFilePath(file);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // 记录日志但继续删除数据库记录
            System.err.println("Failed to delete file on disk: " + e.getMessage());
        }

        fileRepository.delete(file);
    }

    @Transactional
    public void deleteAllForQuestion(Long wrongQuestionId) {
        List<WrongQuestionFile> files = fileRepository.findByWrongQuestionId(wrongQuestionId);
        for (WrongQuestionFile file : files) {
            Path filePath = resolveFilePath(file);
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                System.err.println("Failed to delete file on disk: " + e.getMessage());
            }
        }
        fileRepository.deleteAll(files);
    }

    /**
     * 当错题的年级/科目/来源变化时,把该错题的所有附件搬到新目录
     */
    @Transactional
    public void moveFilesForQuestion(WrongQuestion wq, String oldGrade, String oldSubject, String oldSource) {
        List<WrongQuestionFile> files = fileRepository.findByWrongQuestionId(wq.getId());
        if (files.isEmpty()) return;

        String newRelativeDir = buildRelativeDir(wq.getGrade(), wq.getSubject(), wq.getSource());
        String oldRelativeDir = buildRelativeDir(oldGrade, oldSubject, oldSource);

        if (newRelativeDir.equals(oldRelativeDir)) return;

        Path newDirPath = getBaseDir().resolve(newRelativeDir);
        Path oldDirPath = getBaseDir().resolve(oldRelativeDir);

        for (WrongQuestionFile file : files) {
            Path oldFilePath = getBaseDir().resolve(file.getStoredPath()).normalize();
            if (!Files.exists(oldFilePath)) {
                // 文件已不在磁盘上,直接更新数据库路径
                file.setStoredPath(newRelativeDir + "/" + oldFilePath.getFileName());
                fileRepository.save(file);
                continue;
            }

            String filename = oldFilePath.getFileName().toString();
            Path newFilePath = newDirPath.resolve(filename);
            if (oldFilePath.equals(newFilePath)) continue;

            try {
                Files.createDirectories(newDirPath);
                Files.move(oldFilePath, newFilePath, StandardCopyOption.REPLACE_EXISTING);
                file.setStoredPath(newRelativeDir + "/" + filename);
                fileRepository.save(file);
            } catch (IOException e) {
                throw new RuntimeException("Failed to move file " + filename + ": " + e.getMessage(), e);
            }
        }

        // 旧目录(即使空了)保留不动
    }

    private String buildRelativeDir(String grade, String subject, String source) {
        return String.join("/", sanitize(grade), sanitize(subject), sanitize(source));
    }

    private String sanitize(String s) {
        if (s == null || s.trim().isEmpty()) return "未分类";
        return s.trim().replaceAll("[/\\\\:*?\"<>|]", "_");
    }

    private String getExtension(String filename) {
        if (filename == null) return ".png";
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return ".png";
        String ext = filename.substring(dot).toLowerCase();
        // 限制扩展名长度,避免恶意文件名
        if (ext.length() > 6) return ".png";
        return ext;
    }
}
