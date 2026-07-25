package com.wrongbook.controller;

import com.wrongbook.entity.WrongQuestion;
import com.wrongbook.service.PdfExportService;
import com.wrongbook.service.WrongQuestionFileService;
import com.wrongbook.service.WrongQuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WrongQuestionController.class)
class WrongQuestionControllerPdfTest {
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

    @Test
    void returnsPdfForValidSelection() throws Exception {
        byte[] pdf = "%PDF-1.7 test".getBytes();
        when(pdfExportService.buildPaper(List.of(2L, 1L))).thenReturn(pdf);

        mockMvc.perform(post("/api/wrong-questions/export-pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wrongQuestionIds\":[2,1]}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdf));
    }

    @Test
    void rejectsEmptySelection() throws Exception {
        mockMvc.perform(post("/api/wrong-questions/export-pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wrongQuestionIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void keepsQuestionUpdateEndpointAvailable() throws Exception {
        WrongQuestion updated = new WrongQuestion();
        updated.setId(1L);
        when(wrongQuestionService.update(eq(1L), any(WrongQuestion.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/wrong-questions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
