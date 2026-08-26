package com.version1.recognition.nomination.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.version1.recognition.nomination.exception.InvalidReviewStateException;
import com.version1.recognition.nomination.exception.QuarterLimitReachedException;
import com.version1.recognition.nomination.exception.SelfNominationException;
import com.version1.recognition.nomination.model.AwardCategory;
import com.version1.recognition.nomination.model.CoreValue;
import com.version1.recognition.nomination.model.Nomination;
import com.version1.recognition.nomination.model.NominationStatus;
import com.version1.recognition.nomination.service.CompletenessService;
import com.version1.recognition.nomination.service.NominationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NominationController.class)
class NominationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NominationService service;

    @MockBean
    private CompletenessService completenessService;

    private Nomination nomination(UUID id) {
        Nomination nom = new Nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com",
                "Cloud Engineering", "Dublin", AwardCategory.CUSTOMER_IMPACT, CoreValue.CUSTOMER_FIRST,
                "Delivered the migration", "Helped the client through a tight release", null);
        ReflectionTestUtils.setField(nom, "id", id);
        return nom;
    }

    private Map<String, Object> validRequestBody() {
        return Map.of(
                "nominatorName", "Calvin Ho",
                "nominatorEmail", "calvin@company.com",
                "nomineeName", "Alex Rivera",
                "nomineeEmail", "alex@company.com",
                "practice", "Cloud Engineering",
                "location", "Dublin",
                "category", "CUSTOMER_IMPACT",
                "whatText", "Delivered the migration",
                "howText", "Helped the client through a tight release");
    }

    @Test
    void submitReturns201WithTheCreatedNomination() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.submit(any())).thenReturn(nomination(id));

        mockMvc.perform(post("/api/nominations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequestBody())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    void submitReturns400WhenRequiredFieldsAreMissing() throws Exception {
        mockMvc.perform(post("/api/nominations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.nominatorName").exists());
    }

    @Test
    void submitReturns400WhenNominatorNominatesThemself() throws Exception {
        when(service.submit(any())).thenThrow(new SelfNominationException("You can't nominate yourself."));

        mockMvc.perform(post("/api/nominations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequestBody())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("You can't nominate yourself."));
    }

    @Test
    void submitReturns409WhenTheQuarterLimitIsReached() throws Exception {
        when(service.submit(any()))
                .thenThrow(new QuarterLimitReachedException("Already submitted this quarter.", "2026-Q1"));

        mockMvc.perform(post("/api/nominations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequestBody())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reason").value("QUARTER_LIMIT"))
                .andExpect(jsonPath("$.quarter").value("2026-Q1"));
    }

    @Test
    void getByIdReturns200WhenFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenReturn(nomination(id));

        mockMvc.perform(get("/api/nominations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nomineeName").value("Alex Rivera"));
    }

    @Test
    void getByIdReturns404WhenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id)).thenThrow(new java.util.NoSuchElementException("No nomination found with id " + id));

        mockMvc.perform(get("/api/nominations/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllFiltersByStatusWhenProvided() throws Exception {
        when(service.findAll(NominationStatus.PENDING_REVIEW)).thenReturn(List.of(nomination(UUID.randomUUID())));

        mockMvc.perform(get("/api/nominations").param("status", "PENDING_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void approveReturns200WithTheUpdatedNomination() throws Exception {
        UUID id = UUID.randomUUID();
        Nomination approved = nomination(id);
        approved.setStatus(NominationStatus.APPROVED);
        when(service.approve(eq(id), any())).thenReturn(approved);

        mockMvc.perform(post("/api/nominations/{id}/approve", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coordinatorEmail\":\"colette@company.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approveReturns409WhenNominationIsNotPendingReview() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.approve(eq(id), any())).thenThrow(
                new InvalidReviewStateException("Nomination " + id + " is already APPROVED."));

        mockMvc.perform(post("/api/nominations/{id}/approve", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coordinatorEmail\":\"colette@company.com\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectReturns400WhenReasonIsMissing() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/nominations/{id}/reject", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coordinatorEmail\":\"colette@company.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").exists());
    }

    @Test
    void requestResubmissionReturns200WithTheUpdatedNomination() throws Exception {
        UUID id = UUID.randomUUID();
        Nomination needsResubmission = nomination(id);
        needsResubmission.setStatus(NominationStatus.NEEDS_RESUBMISSION);
        when(service.requestResubmission(eq(id), any())).thenReturn(needsResubmission);

        mockMvc.perform(post("/api/nominations/{id}/request-resubmission", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coordinatorEmail\":\"colette@company.com\",\"reason\":\"Needs more detail\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_RESUBMISSION"));
    }
}
