package com.version1.recognition.nomination.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.version1.recognition.nomination.model.AuditAction;
import com.version1.recognition.nomination.model.AuditLogEntry;
import com.version1.recognition.nomination.model.AwardCategory;
import com.version1.recognition.nomination.model.CoreValue;
import com.version1.recognition.nomination.model.Nomination;
import com.version1.recognition.nomination.repository.AuditLogRepository;
import com.version1.recognition.nomination.repository.NominationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ActivityController.class)
class ActivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @MockBean
    private NominationRepository nominationRepository;

    @Test
    void allReturnsEveryEntryWithTheNomineeNameJoinedIn() throws Exception {
        UUID nominationId = UUID.randomUUID();
        Nomination nom = new Nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com",
                "Cloud Engineering", "Dublin", AwardCategory.CUSTOMER_IMPACT, CoreValue.CUSTOMER_FIRST,
                "Delivered the migration", "Helped the client through a tight release", null);
        ReflectionTestUtils.setField(nom, "id", nominationId);

        AuditLogEntry entry = new AuditLogEntry(nominationId, "colette@company.com", AuditAction.APPROVED, null);
        ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());

        when(nominationRepository.findAll()).thenReturn(List.of(nom));
        when(auditLogRepository.findAll()).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nomineeName").value("Alex Rivera"))
                .andExpect(jsonPath("$[0].action").value("APPROVED"));
    }

    @Test
    void allShowsAPlaceholderWhenTheNominationBehindAnEntryIsGone() throws Exception {
        UUID nominationId = UUID.randomUUID();
        AuditLogEntry entry = new AuditLogEntry(nominationId, "colette@company.com", AuditAction.REJECTED, "Thin");
        ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());

        when(nominationRepository.findAll()).thenReturn(List.of());
        when(auditLogRepository.findAll()).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nomineeName").value("(deleted nomination)"));
    }
}
