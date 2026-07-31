package com.wrongbook.service;

import com.wrongbook.entity.WrongQuestion;
import com.wrongbook.entity.WrongQuestionFile;
import com.wrongbook.repository.WrongQuestionFileRepository;
import com.wrongbook.repository.WrongQuestionRepository;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveSize2D;
import org.openxmlformats.schemas.drawingml.x2006.picture.CTPicture;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHdrFtrRef;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSimpleField;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STHdrFtr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
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
public class WordExportService {
    public static final int MAX_QUESTIONS = 200;

    private static final int A4_WIDTH_TWIPS = 11906;
    private static final int A4_HEIGHT_TWIPS = 16838;
    private static final int MARGIN_TWIPS = 1440;
    private static final long CONTENT_WIDTH_EMU = 5943600L;
    private static final int DPI = 96;
    private static final float HEADER_FONT_SIZE = 12;
    private static final float ANSWER_FONT_SIZE = 11;

    private final WrongQuestionRepository wrongQuestionRepository;
    private final WrongQuestionFileRepository fileRepository;
    private final WrongQuestionFileService fileService;
    private final String fontName;

    public WordExportService(
            WrongQuestionRepository wrongQuestionRepository,
            WrongQuestionFileRepository fileRepository,
            WrongQuestionFileService fileService,
            @Value("${app.export.word.font-name}") String fontName) {
        this.wrongQuestionRepository = wrongQuestionRepository;
        this.fileRepository = fileRepository;
        this.fileService = fileService;
        this.fontName = fontName;
    }

    @Transactional(readOnly = true)
    public byte[] buildPaper(List<Long> orderedIds, boolean includeAnswers) {
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

        Map<Long, List<WrongQuestionFile>> answerFilesByQuestionId = Map.of();
        if (includeAnswers) {
            List<WrongQuestionFile> answerFiles = fileRepository
                    .findByWrongQuestionIdInAndFileTypeOrderByWrongQuestionIdAscIdAsc(
                            orderedIds, WrongQuestionFile.FileType.ANSWER);
            answerFilesByQuestionId = new HashMap<>();
            for (WrongQuestionFile file : answerFiles) {
                answerFilesByQuestionId
                        .computeIfAbsent(file.getWrongQuestion().getId(), ignored -> new ArrayList<>())
                        .add(file);
            }
        }

        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            setupPage(doc);
            int sequence = 1;
            for (Long id : orderedIds) {
                WrongQuestion question = questionsById.get(id);
                renderQuestion(doc, sequence, question,
                        filesByQuestionId.get(id),
                        answerFilesByQuestionId.getOrDefault(id, List.of()),
                        includeAnswers);
                sequence++;
            }
            addPageFooter(doc);
            doc.write(output);
            return output.toByteArray();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (WordExportException e) {
            throw e;
        } catch (Exception e) {
            throw new WordExportException("Word 生成失败", e);
        }
    }

    private void setupPage(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().addNewSectPr();
        CTPageSz pgSz = sectPr.addNewPgSz();
        pgSz.setW(A4_WIDTH_TWIPS);
        pgSz.setH(A4_HEIGHT_TWIPS);
        CTPageMar pgMar = sectPr.addNewPgMar();
        pgMar.setTop(MARGIN_TWIPS);
        pgMar.setRight(MARGIN_TWIPS);
        pgMar.setBottom(MARGIN_TWIPS);
        pgMar.setLeft(MARGIN_TWIPS);
        pgMar.setHeader(720);
        pgMar.setFooter(720);
        pgMar.setGutter(0);
    }

    private void renderQuestion(XWPFDocument doc, int sequence,
                                WrongQuestion question,
                                List<WrongQuestionFile> questionFiles,
                                List<WrongQuestionFile> answerFiles,
                                boolean includeAnswers) {
        XWPFParagraph headerPara = doc.createParagraph();
        XWPFRun headerRun = headerPara.createRun();
        headerRun.setBold(true);
        headerRun.setFontSize(HEADER_FONT_SIZE);
        applyFont(headerRun);
        headerRun.setText(sequence + ". 来源：" + display(question.getSource())
                + "  原题号：" + display(question.getQuestionNo()));

        for (WrongQuestionFile file : questionFiles) {
            XWPFParagraph imagePara = doc.createParagraph();
            try {
                addImage(imagePara.createRun(), file);
            } catch (IOException e) {
                throw new WordExportException("图片无法读取: " + file.getOriginalName(), e);
            }
        }

        if (includeAnswers && hasAnswer(question.getAnswerText(), answerFiles)) {
            XWPFParagraph answerLabelPara = doc.createParagraph();
            XWPFRun labelRun = answerLabelPara.createRun();
            labelRun.setBold(true);
            labelRun.setFontSize(ANSWER_FONT_SIZE);
            applyFont(labelRun);
            labelRun.setText("答案：");

            String answerText = question.getAnswerText();
            if (answerText != null && !answerText.isBlank()) {
                XWPFParagraph answerTextPara = doc.createParagraph();
                XWPFRun textRun = answerTextPara.createRun();
                textRun.setFontSize(ANSWER_FONT_SIZE);
                applyFont(textRun);
                textRun.setText(answerText);
            }

            for (WrongQuestionFile answerFile : answerFiles) {
                XWPFParagraph imagePara = doc.createParagraph();
                try {
                    addImage(imagePara.createRun(), answerFile);
                } catch (IOException e) {
                    throw new WordExportException("图片无法读取: " + answerFile.getOriginalName(), e);
                }
            }
        }
    }

    private void addImage(XWPFRun run, WrongQuestionFile file) throws IOException {
        Path path = fileService.resolveFilePath(file);
        byte[] bytes = Files.readAllBytes(path);
        int pictureType = pictureTypeOf(file.getContentType());

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IOException("无法解码图片: " + file.getOriginalName());
        }

        long widthEmu = (long) image.getWidth() * 914400L / DPI;
        long heightEmu = (long) image.getHeight() * 914400L / DPI;
        if (widthEmu > CONTENT_WIDTH_EMU) {
            double scale = (double) CONTENT_WIDTH_EMU / widthEmu;
            widthEmu = CONTENT_WIDTH_EMU;
            heightEmu = (long) (heightEmu * scale);
        }

        try {
            run.addPicture(
                    new ByteArrayInputStream(bytes),
                    pictureType,
                    file.getOriginalName() == null ? "image" : file.getOriginalName(),
                    (int) widthEmu,
                    (int) heightEmu);
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IOException("无法嵌入图片: " + file.getOriginalName(), e);
        }
    }

    private void addPageFooter(XWPFDocument doc) {
        XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph para = footer.getParagraphArray(0);
        if (para == null) {
            para = footer.createParagraph();
        }
        para.setAlignment(ParagraphAlignment.CENTER);

        addRunWithFont(para, "第 ");
        appendField(para, " PAGE   \\* MERGEFORMAT ");
        addRunWithFont(para, " 页 / 共 ");
        appendField(para, " NUMPAGES   \\* MERGEFORMAT ");
        addRunWithFont(para, " 页");

        XWPFHeaderFooterPolicy policy = doc.getHeaderFooterPolicy();
        if (policy != null) {
            try {
                String footerRid = findFooterRelationshipId(doc, footer);
                if (footerRid != null) {
                    CTSectPr sectPr = doc.getDocument().getBody().getSectPr();
                    CTHdrFtrRef footerRef = sectPr.addNewFooterReference();
                    footerRef.setType(STHdrFtr.DEFAULT);
                    footerRef.setId(footerRid);
                }
            } catch (Exception ignored) {
                // 页码已写入页脚，未挂接引用时 Word 会忽略；不影响主要内容生成
            }
        }
    }

    private String findFooterRelationshipId(XWPFDocument doc, XWPFFooter footer) {
        try {
            org.apache.poi.openxml4j.opc.PackagePart sourcePart = doc.getPackagePart();
            for (PackageRelationship rel : sourcePart.getRelationships()) {
                if (rel.getTargetURI().equals(footer.getPackagePart().getPartName())) {
                    return rel.getId();
                }
            }
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException ignored) {
            // 关联失败时页脚仍然写入 XML,Word 可能忽略引用,不影响主流程
        }
        return null;
    }

    private void addRunWithFont(XWPFParagraph para, String text) {
        XWPFRun run = para.createRun();
        applyFont(run);
        run.setText(text);
    }

    private void appendField(XWPFParagraph para, String instruction) {
        CTSimpleField field = para.getCTP().addNewFldSimple();
        field.setInstr(instruction);
        CTR innerRun = field.addNewR();
        applyCtrFont(innerRun);
        innerRun.addNewT().setStringValue("1");
    }

    private void applyFont(XWPFRun run) {
        run.setFontFamily(fontName);
        if (run.getCTR().getRPr() == null) {
            run.getCTR().addNewRPr();
        }
        if (run.getCTR().getRPr().getRFontsList().isEmpty()) {
            run.getCTR().getRPr().addNewRFonts();
        }
        run.getCTR().getRPr().getRFontsArray(0).setEastAsia(fontName);
    }

    private void applyCtrFont(CTR run) {
        if (run.getRPr() == null) {
            run.addNewRPr();
        }
        if (run.getRPr().getRFontsList().isEmpty()) {
            run.getRPr().addNewRFonts();
        }
        run.getRPr().getRFontsArray(0).setAscii(fontName);
        run.getRPr().getRFontsArray(0).setHAnsi(fontName);
        run.getRPr().getRFontsArray(0).setEastAsia(fontName);
    }

    private int pictureTypeOf(String contentType) {
        if (contentType == null) {
            return XWPFDocument.PICTURE_TYPE_PNG;
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> XWPFDocument.PICTURE_TYPE_JPEG;
            case "image/gif" -> XWPFDocument.PICTURE_TYPE_GIF;
            case "image/bmp" -> XWPFDocument.PICTURE_TYPE_BMP;
            default -> XWPFDocument.PICTURE_TYPE_PNG;
        };
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

    private boolean hasAnswer(String answerText, List<WrongQuestionFile> answerFiles) {
        return (answerText != null && !answerText.isBlank()) || !answerFiles.isEmpty();
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public static class WordExportException extends RuntimeException {
        public WordExportException(String message) {
            super(message);
        }

        public WordExportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
