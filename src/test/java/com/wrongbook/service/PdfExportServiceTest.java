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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
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
                List.of(2L, 1L), WrongQuestionFile.FileType.QUESTION))
                .thenReturn(List.of(firstFile, secondFile));
        when(fileService.resolveFilePath(secondFile)).thenReturn(secondImage);
        when(fileService.resolveFilePath(firstFile)).thenReturn(firstImage);

        byte[] pdf = exportService.buildPaper(List.of(2L, 1L));

        assertTrue(pdf.length > 0);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.indexOf("1. 来源：第二来源") < text.indexOf("2. 来源：第一来源"));
            assertTrue(document.getNumberOfPages() >= 1);
        }
    }

    @Test
    void rejectsEmptySelection() {
        assertThrows(IllegalArgumentException.class, () -> exportService.buildPaper(List.of()));
    }

    @Test
    void rejectsQuestionWithoutImage() {
        WrongQuestion question = question(1L, "没有图片", "1");
        when(questionRepository.findAllById(List.of(1L))).thenReturn(List.of(question));
        when(fileRepository.findByWrongQuestionIdInAndFileTypeOrderByWrongQuestionIdAscIdAsc(
                List.of(1L), WrongQuestionFile.FileType.QUESTION))
                .thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> exportService.buildPaper(List.of(1L)));
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

    private Path createImage(String name, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Path path = tempDir.resolve(name);
        ImageIO.write(image, "png", path.toFile());
        return path;
    }
}
