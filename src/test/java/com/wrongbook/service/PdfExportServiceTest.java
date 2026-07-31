package com.wrongbook.service;

import com.wrongbook.entity.WrongQuestion;
import com.wrongbook.entity.WrongQuestionFile;
import com.wrongbook.repository.WrongQuestionFileRepository;
import com.wrongbook.repository.WrongQuestionRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfExportServiceTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @TempDir
    Path tempDir;

    private WrongQuestionRepository questionRepository;
    private WrongQuestionFileRepository fileRepository;
    private WrongQuestionFileService fileService;
    private PdfExportService exportService;
    private String fontPath;

    @BeforeEach
    void setUp() {
        questionRepository = mock(WrongQuestionRepository.class);
        fileRepository = mock(WrongQuestionFileRepository.class);
        fileService = mock(WrongQuestionFileService.class);
        fontPath = System.getenv().getOrDefault(
                "PDF_FONT_PATH",
                "/System/Library/Fonts/Supplemental/Arial Unicode.ttf"
        );
        exportService = new PdfExportService(
                questionRepository,
                fileRepository,
                fileService,
                fontPath
        );
    }

    @Test
    void exportsQuestionsInRequestedOrder() throws Exception {
        assumeTrue(Files.isRegularFile(Path.of(fontPath)), "需要通过 PDF_FONT_PATH 提供中文字体");

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

        byte[] pdf = exportService.buildPaper(List.of(2L, 1L), false);

        assertTrue(pdf.length > 0);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.indexOf("1. 来源：第二来源") < text.indexOf("2. 来源：第一来源"));
            assertTrue(document.getNumberOfPages() >= 1);
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
        assumeTrue(Files.isRegularFile(Path.of(fontPath)), "需要通过 PDF_FONT_PATH 提供中文字体");

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

        byte[] pdf = exportService.buildPaper(List.of(1L), true);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("答案："));
            assertTrue(text.contains("这是答案文字内容"));
            assertTrue(document.getNumberOfPages() >= 1);
        }
    }

    @Test
    void skipsAnswerSectionWhenQuestionHasNoAnswer() throws Exception {
        assumeTrue(Files.isRegularFile(Path.of(fontPath)), "需要通过 PDF_FONT_PATH 提供中文字体");

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

        byte[] pdf = exportService.buildPaper(List.of(1L), true);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertFalse(text.contains("答案："));
            assertTrue(document.getNumberOfPages() >= 1);
        }
    }

    @Test
    void rendersPageNumbersOnEveryPage() throws Exception {
        assumeTrue(Files.isRegularFile(Path.of(fontPath)), "需要通过 PDF_FONT_PATH 提供中文字体");

        WrongQuestion first = question(1L, "来源1", "1");
        WrongQuestion second = question(2L, "来源2", "2");
        Path firstImage = createImage("p1.png", 800, 400);
        Path secondImage = createImage("p2.png", 800, 1200);
        WrongQuestionFile firstFile = questionFile(11L, "p1.png");
        firstFile.setWrongQuestion(first);
        WrongQuestionFile secondFile = questionFile(12L, "p2.png");
        secondFile.setWrongQuestion(second);

        when(questionRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(first, second));
        when(fileRepository.findByWrongQuestionIdInAndFileTypeOrderByWrongQuestionIdAscIdAsc(
                eq(List.of(1L, 2L)), eq(WrongQuestionFile.FileType.QUESTION)))
                .thenReturn(List.of(firstFile, secondFile));
        when(fileService.resolveFilePath(firstFile)).thenReturn(firstImage);
        when(fileService.resolveFilePath(secondFile)).thenReturn(secondImage);

        byte[] pdf = exportService.buildPaper(List.of(1L, 2L), false);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            int total = document.getNumberOfPages();
            assertTrue(total >= 1, "expected at least one page");
            for (int i = 1; i <= total; i++) {
                String label = "第 " + i + " 页 / 共 " + total + " 页";
                assertTrue(text.contains(label),
                        "expected page label '" + label + "' in extracted text, got:\n" + text);
            }
        }
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
        file.setFileType(WrongQuestionFile.FileType.QUESTION);
        return file;
    }

    private WrongQuestionFile answerFile(Long id, String name) {
        WrongQuestionFile file = new WrongQuestionFile();
        file.setId(id);
        file.setOriginalName(name);
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
