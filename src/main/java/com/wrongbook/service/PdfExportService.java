package com.wrongbook.service;

import com.wrongbook.entity.WrongQuestion;
import com.wrongbook.entity.WrongQuestionFile;
import com.wrongbook.repository.WrongQuestionFileRepository;
import com.wrongbook.repository.WrongQuestionRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PdfExportService {
    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float MARGIN_TOP = 48;
    private static final float MARGIN_BOTTOM = 48;
    private static final float MARGIN_LEFT = 54;
    private static final float MARGIN_RIGHT = 54;
    private static final float FONT_SIZE = 11;
    private static final float LINE_HEIGHT = 16;
    private static final float QUESTION_GAP = 10;
    public static final int MAX_QUESTIONS = 200;

    private final WrongQuestionRepository wrongQuestionRepository;
    private final WrongQuestionFileRepository fileRepository;
    private final WrongQuestionFileService fileService;
    private final String fontPath;

    public PdfExportService(
            WrongQuestionRepository wrongQuestionRepository,
            WrongQuestionFileRepository fileRepository,
            WrongQuestionFileService fileService,
            @Value("${app.export.pdf.font-path}") String fontPath) {
        this.wrongQuestionRepository = wrongQuestionRepository;
        this.fileRepository = fileRepository;
        this.fileService = fileService;
        this.fontPath = fontPath;
    }

    @Transactional(readOnly = true)
    public byte[] buildPaper(List<Long> orderedIds) {
        validateIds(orderedIds);

        List<WrongQuestion> questions = wrongQuestionRepository.findAllById(orderedIds);
        Map<Long, WrongQuestion> questionsById = new HashMap<>();
        for (WrongQuestion question : questions) {
            questionsById.put(question.getId(), question);
        }
        if (questionsById.size() != orderedIds.size()) {
            throw new IllegalArgumentException("部分错题已不存在，请刷新列表后重试");
        }

        List<WrongQuestionFile> questionFiles = fileRepository
                .findByWrongQuestionIdInAndFileTypeOrderByWrongQuestionIdAscIdAsc(
                        orderedIds, WrongQuestionFile.FileType.QUESTION);
        Map<Long, List<WrongQuestionFile>> filesByQuestionId = new HashMap<>();
        for (WrongQuestionFile file : questionFiles) {
            filesByQuestionId.computeIfAbsent(file.getWrongQuestion().getId(), ignored -> new ArrayList<>())
                    .add(file);
        }
        for (Long id : orderedIds) {
            if (!filesByQuestionId.containsKey(id)) {
                WrongQuestion question = questionsById.get(id);
                throw new IllegalArgumentException("错题“" + display(question.getSource()) + "”没有题目图片");
            }
        }

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont font = loadFont(document);
            PdfWriter writer = new PdfWriter(document, font);
            int sequence = 1;

            for (Long id : orderedIds) {
                WrongQuestion question = questionsById.get(id);
                List<WrongQuestionFile> files = filesByQuestionId.get(id);
                List<String> headerLines = wrapText(
                        sequence + ". 来源：" + display(question.getSource()) + "  原题号：" + display(question.getQuestionNo()),
                        font,
                        writer.contentWidth()
                );
                writer.addText(headerLines);
                for (WrongQuestionFile file : files) {
                    writer.addImage(loadImage(document, file));
                }
                writer.addGap(QUESTION_GAP);
                sequence++;
            }

            writer.close();
            document.save(output);
            return output.toByteArray();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (PdfExportException e) {
            throw e;
        } catch (Exception e) {
            throw new PdfExportException("PDF 生成失败", e);
        }
    }

    private void validateIds(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一道错题");
        }
        if (orderedIds.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("一次最多生成 " + MAX_QUESTIONS + " 道错题");
        }
        if (orderedIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("错题编号无效");
        }
        Set<Long> uniqueIds = new HashSet<>(orderedIds);
        if (uniqueIds.size() != orderedIds.size()) {
            throw new IllegalArgumentException("错题列表中包含重复题目");
        }
    }

    private PDFont loadFont(PDDocument document) throws IOException {
        Path path = Path.of(fontPath);
        if (!Files.isRegularFile(path)) {
            throw new IOException("字体文件不存在: " + path);
        }
        return PDType0Font.load(document, path.toFile());
    }

    private PDImageXObject loadImage(PDDocument document, WrongQuestionFile file) {
        Path path = fileService.resolveFilePath(file);
        try {
            return PDImageXObject.createFromFileByContent(path.toFile(), document);
        } catch (IOException | IllegalArgumentException e) {
            throw new PdfExportException("题目图片无法读取: " + file.getOriginalName(), e);
        }
    }

    private List<String> wrapText(String text, PDFont font, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        float lineWidth = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            float characterWidth = font.getStringWidth(String.valueOf(character)) / 1000 * FONT_SIZE;
            if (line.length() > 0 && lineWidth + characterWidth > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                lineWidth = 0;
            }
            line.append(character);
            lineWidth += characterWidth;
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public static class PdfExportException extends RuntimeException {
        public PdfExportException(String message) {
            super(message);
        }

        public PdfExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class PdfWriter {
        private final PDDocument document;
        private final PDFont font;
        private final float contentWidth;
        private final float contentHeight;
        private PDPageContentStream stream;
        private float y;

        private PdfWriter(PDDocument document, PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            this.contentWidth = PAGE_SIZE.getWidth() - MARGIN_LEFT - MARGIN_RIGHT;
            this.contentHeight = PAGE_SIZE.getHeight() - MARGIN_TOP - MARGIN_BOTTOM;
            newPage();
        }

        private float contentWidth() {
            return contentWidth;
        }

        private void addText(List<String> lines) throws IOException {
            for (String line : lines) {
                ensureSpace(LINE_HEIGHT);
                stream.beginText();
                stream.setFont(font, FONT_SIZE);
                stream.newLineAtOffset(MARGIN_LEFT, y - FONT_SIZE);
                stream.showText(line);
                stream.endText();
                y -= LINE_HEIGHT;
            }
            y -= 6;
        }

        private void addImage(PDImageXObject image) throws IOException {
            float scale = Math.min(1, contentWidth / image.getWidth());
            float imageHeight = image.getHeight() * scale;
            if (imageHeight > remainingHeight()) {
                newPage();
                scale = Math.min(1, Math.min(contentWidth / image.getWidth(), contentHeight / image.getHeight()));
                imageHeight = image.getHeight() * scale;
            }
            stream.drawImage(image, MARGIN_LEFT, y - imageHeight, image.getWidth() * scale, imageHeight);
            y -= imageHeight + 8;
        }

        private void addGap(float gap) {
            y = Math.max(MARGIN_BOTTOM, y - gap);
        }

        private float remainingHeight() {
            return y - MARGIN_BOTTOM;
        }

        private void ensureSpace(float height) throws IOException {
            if (remainingHeight() < height) {
                newPage();
            }
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_SIZE.getHeight() - MARGIN_TOP;
        }

        private void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }
    }
}
