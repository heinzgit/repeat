package com.wrongbook.service;

import com.wrongbook.entity.WrongQuestion;
import com.wrongbook.entity.WrongQuestionFile;
import com.wrongbook.repository.WrongQuestionFileRepository;
import com.wrongbook.repository.WrongQuestionRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WordExportServiceTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @TempDir
    Path tempDir;

    private WrongQuestionRepository questionRepository;
    private WrongQuestionFileRepository fileRepository;
    private WrongQuestionFileService fileService;
    private WordExportService exportService;

    @BeforeEach
    void setUp() {
        questionRepository = mock(WrongQuestionRepository.class);
        fileRepository = mock(WrongQuestionFileRepository.class);
        fileService = mock(WrongQuestionFileService.class);
        exportService = new WordExportService(
                questionRepository,
                fileRepository,
                fileService,
                "Arial Unicode MS"
        );
    }

    @Test
    void exportsQuestionsInRequestedOrder() throws Exception {
        Path firstImage = createImage("first.png", 800, 400);
        Path secondImage = createImage("second.png", 800, 400);
        WrongQuestion first = question(1L, "第一来源", "1");
        WrongQuestion second = question(2L, "第二来源", "2");
        WrongQuestionFile firstFile = questionFile(11L, "first.png");
        firstFile.setWrongQuestion(first);
        WrongQuestionFile secondFile = questionFile(12L, "second.png");
        secondFile.setWrongQuestion(second);

        when(questionRepository.findAllById(List.of(2L, 1L))).thenReturn(List.of(first, second));
        when(fileRepository.findByWrongQuestionIdInAndFileTypeOrderByWrongQuestionIdAscIdAsc(
                eq(List.of(2L, 1L)), eq(WrongQuestionFile.FileType.QUESTION)))
                .thenReturn(List.of(firstFile, secondFile));
        when(fileService.resolveFilePath(secondFile)).thenReturn(secondImage);
        when(fileService.resolveFilePath(firstFile)).thenReturn(firstImage);

        byte[] docx = exportService.buildPaper(List.of(2L, 1L), false);

        assertTrue(docx.length > 0);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            String text = allText(doc);
            assertTrue(text.indexOf("1. 来源：第二来源") < text.indexOf("2. 来源：第一来源"));
            assertFalse(text.contains("答案："));
        }
    }

    @Test
    void rejectsEmptySelection() {
        assertThrows(IllegalArgumentException.class, () -> exportService.buildPaper(List.of(), false));
    }

    @Test
    void rejectsQuestionWithoutImage() {
        WrongQuestion question = question(1L, "没有图片", "1");
        when(questionRepository.findAllById(List.of(1L))).thenReturn(List.of(question));
        when(fileRepository.findByWrongQuestionIdInAndFileTypeOrderByWrongQuestionIdAscIdAsc(
                eq(List.of(1L)), eq(WrongQuestionFile.FileType.QUESTION)))
                .thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> exportService.buildPaper(List.of(1L), false));
    }

    @Test
    void includesAnswerTextAndImagesWhenRequested() throws Exception {
        Path questionImage = createImage("q.png", 800, 400);
        Path answerImage = createImage("a.png", 800, 400);
        WrongQuestion question = question(1L, "来源A", "1");
        question.setAnswerText("这是答案文字内容");
        WrongQuestionFile questionFileEntity = questionFile(11L, "q.png");
        questionFileEntity.setWrongQuestion(question);
        WrongQuestionFile answerFileEntity = answerFile(21L, "a.png");
        answerFileEntity.setWrongQuestion(question);

        when(questionRepository.findAllById(List.of(1L))).thenReturn(List.of(question));
        when(fileRepository.findByWrongQuestionIdInAndFileTypeOrderByWrongQuestionIdAscIdAsc(
                eq(List.of(1L)), eq(WrongQuestionFile.FileType.QUESTION)))
                .thenReturn(List.of(questionFileEntity));
        when(fileRepository.findByWrongQuestionIdInAndFileTypeOrderByWrongQuestionIdAscIdAsc(
                eq(List.of(1L)), eq(WrongQuestionFile.FileType.ANSWER)))
                .thenReturn(List.of(answerFileEntity));
        when(fileService.resolveFilePath(questionFileEntity)).thenReturn(questionImage);
        when(fileService.resolveFilePath(answerFileEntity)).thenReturn(answerImage);

        byte[] docx = exportService.buildPaper(List.of(1L), true);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            String text = allText(doc);
            assertTrue(text.contains("答案："));
            assertTrue(text.contains("这是答案文字内容"));
        }
    }

    @Test
    void skipsAnswerSectionWhenQuestionHasNoAnswer() throws Exception {
        Path questionImage = createImage("q.png", 800, 400);
        WrongQuestion question = question(1L, "无答案来源", "1");
        WrongQuestionFile questionFileEntity = questionFile(11L, "q.png");
        questionFileEntity.setWrongQuestion(question);

        when(questionRepository.findAllById(List.of(1L))).thenReturn(List.of(question));
        when(fileRepository.findByWrongQuestionIdInAndFileTypeOrderByWrongQuestionIdAscIdAsc(
                eq(List.of(1L)), eq(WrongQuestionFile.FileType.QUESTION)))
                .thenReturn(List.of(questionFileEntity));
        when(fileRepository.findByWrongQuestionIdInAndFileTypeOrderByWrongQuestionIdAscIdAsc(
                eq(List.of(1L)), eq(WrongQuestionFile.FileType.ANSWER)))
                .thenReturn(List.of());
        when(fileService.resolveFilePath(questionFileEntity)).thenReturn(questionImage);

        byte[] docx = exportService.buildPaper(List.of(1L), true);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            String text = allText(doc);
            assertFalse(text.contains("答案："));
        }
    }

    @Test
    void rendersPageFooterWithPageFields() throws Exception {
        Path image = createImage("p.png", 800, 400);
        WrongQuestion question = question(1L, "来源", "1");
        WrongQuestionFile qFile = questionFile(11L, "p.png");
        qFile.setWrongQuestion(question);

        when(questionRepository.findAllById(List.of(1L))).thenReturn(List.of(question));
        when(fileRepository.findByWrongQuestionIdInAndFileTypeOrderByWrongQuestionIdAscIdAsc(
                eq(List.of(1L)), eq(WrongQuestionFile.FileType.QUESTION)))
                .thenReturn(List.of(qFile));
        when(fileService.resolveFilePath(qFile)).thenReturn(image);

        byte[] docx = exportService.buildPaper(List.of(1L), false);

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFFooter footer : doc.getFooterList()) {
                for (XWPFParagraph para : footer.getParagraphs()) {
                    for (XWPFRun run : para.getRuns()) {
                        sb.append(run.getText(0));
                    }
                }
            }
            String footerText = sb.toString();
            assertTrue(footerText.contains("第 "));
            assertTrue(footerText.contains("页 / 共 "));
            assertTrue(footerText.contains("页"));
            String footerXml = doc.getFooterList().get(0)._getHdrFtr().toString();
            assertTrue(footerXml.contains("PAGE"));
            assertTrue(footerXml.contains("NUMPAGES"));
        }
    }

    private String allText(XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph para : doc.getParagraphs()) {
            for (XWPFRun run : para.getRuns()) {
                sb.append(run.getText(0));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private WrongQuestion question(Long id, String source, String questionNo) {
        WrongQuestion question = new WrongQuestion();
        question.setId(id);
        question.setSource(source);
        question.setQuestionNo(questionNo);
        return question;
    }

    private WrongQuestionFile questionFile(Long id, String name) {
        WrongQuestionFile file = new WrongQuestionFile();
        file.setId(id);
        file.setOriginalName(name);
        file.setContentType("image/png");
        file.setFileType(WrongQuestionFile.FileType.QUESTION);
        return file;
    }

    private WrongQuestionFile answerFile(Long id, String name) {
        WrongQuestionFile file = new WrongQuestionFile();
        file.setId(id);
        file.setOriginalName(name);
        file.setContentType("image/png");
        file.setFileType(WrongQuestionFile.FileType.ANSWER);
        return file;
    }

    private Path createImage(String name, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Path path = tempDir.resolve(name);
        ImageIO.write(image, "png", path.toFile());
        return path;
    }
}
