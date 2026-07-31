package com.wrongbook.controller;

import com.wrongbook.service.PdfExportService;
import com.wrongbook.service.WordExportService;
import com.wrongbook.service.WrongQuestionFileService;
import com.wrongbook.service.WrongQuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WrongQuestionController.class)
class WrongQuestionControllerWordTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WrongQuestionService wrongQuestionService;

    @MockBean
    private WrongQuestionFileService fileService;

    @MockBean
    private PdfExportService pdfExportService;

    @MockBean
    private WordExportService wordExportService;

    @Test
    void returnsDocxForValidSelection() throws Exception {
        byte[] docx = new byte[]{(byte) 0x50, (byte) 0x4B, 0x03, 0x04};
        when(wordExportService.buildPaper(eq(List.of(2L, 1L)), eq(false))).thenReturn(docx);

        mockMvc.perform(post("/api/wrong-questions/export-word")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wrongQuestionIds\":[2,1]}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(content().bytes(docx));
    }

    @Test
    void rejectsEmptySelection() throws Exception {
        mockMvc.perform(post("/api/wrong-questions/export-word")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wrongQuestionIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsIncludeAnswersTrue() throws Exception {
        byte[] docx = new byte[]{1, 2, 3};
        when(wordExportService.buildPaper(eq(List.of(1L)), eq(true))).thenReturn(docx);

        mockMvc.perform(post("/api/wrong-questions/export-word")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wrongQuestionIds\":[1],\"includeAnswers\":true}"))
                .andExpect(status().isOk());

        verify(wordExportService).buildPaper(eq(List.of(1L)), eq(true));
    }

    @Test
    void acceptsOmittedIncludeAnswers() throws Exception {
        byte[] docx = new byte[]{1};
        when(wordExportService.buildPaper(anyList(), eq(false))).thenReturn(docx);

        mockMvc.perform(post("/api/wrong-questions/export-word")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wrongQuestionIds\":[1]}"))
                .andExpect(status().isOk());

        verify(wordExportService).buildPaper(eq(List.of(1L)), eq(false));
    }
}
