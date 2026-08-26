package com.version1.recognition.nomination.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.version1.recognition.nomination.model.AwardCategory;
import com.version1.recognition.nomination.model.CoreValue;
import com.version1.recognition.nomination.model.Nomination;
import com.version1.recognition.nomination.repository.NominationRepository;
import com.version1.recognition.nomination.service.NominationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuarterController.class)
class QuarterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NominationService service;

    @MockBean
    private NominationRepository repository;

    @Test
    void currentWithoutEmailReportsHasNotSubmitted() throws Exception {
        mockMvc.perform(get("/api/quarters/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.hasSubmitted").value(false));
    }

    @Test
    void currentWithEmailAndNoSubmissionReportsHasNotSubmitted() throws Exception {
        when(service.findCurrentQuarterNomination("calvin@company.com")).thenReturn(null);

        mockMvc.perform(get("/api/quarters/current").param("email", "calvin@company.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSubmitted").value(false));
    }

    @Test
    void currentWithEmailAndAnExistingSubmissionIncludesIt() throws Exception {
        Nomination nom = new Nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com",
                "Cloud Engineering", "Dublin", AwardCategory.CUSTOMER_IMPACT, CoreValue.CUSTOMER_FIRST,
                "Delivered the migration", "Helped the client through a tight release", null);
        ReflectionTestUtils.setField(nom, "id", UUID.randomUUID());
        when(service.findCurrentQuarterNomination("calvin@company.com")).thenReturn(nom);

        mockMvc.perform(get("/api/quarters/current").param("email", "calvin@company.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSubmitted").value(true))
                .andExpect(jsonPath("$.submission.nomineeName").value("Alex Rivera"));
    }

    @Test
    void historyAlwaysIncludesTheCurrentQuarterEvenWithNoData() throws Exception {
        when(repository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/quarters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isCurrent").value(true))
                .andExpect(jsonPath("$[0].totalNominations").value(0));
    }

    @Test
    void nowReturnsAServerTimestamp() throws Exception {
        mockMvc.perform(get("/api/quarters/now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverTime").exists());
    }
}
