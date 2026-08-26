package com.version1.recognition.nomination.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.version1.recognition.nomination.exception.QuarterLimitReachedException;
import com.version1.recognition.nomination.model.AuditLogEntry;
import com.version1.recognition.nomination.model.AwardCategory;
import com.version1.recognition.nomination.model.Nomination;
import com.version1.recognition.nomination.model.NominationStatus;
import com.version1.recognition.nomination.repository.AuditLogRepository;
import com.version1.recognition.nomination.repository.NominationRepository;
import com.version1.recognition.nomination.service.NominationService;
import com.version1.recognition.nomination.web.ApproveRequest;
import com.version1.recognition.nomination.web.NominationRequest;
import com.version1.recognition.nomination.web.ReviewDecisionRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the full stack wires together against a real, Liquibase-backed MySQL
 * schema (recognitiondb_test - see application-test.properties): submission,
 * real (not mocked) rule tagging, and every review decision. This is the gap
 * the README calls out - the review-decision workflow was previously only
 * tested with a mocked repository.
 *
 * <p>Each test rolls back at the end (class-level @Transactional), so tests are
 * order-independent and never touch the dev/demo database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NominationReviewWorkflowIntegrationTest {

    @Autowired
    private NominationService service;

    @Autowired
    private NominationRepository nominationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private NominationRequest request(String nominatorEmail, String nomineeEmail) {
        NominationRequest request = new NominationRequest();
        request.setNominatorName("Calvin Ho");
        request.setNominatorEmail(nominatorEmail);
        request.setNomineeName("Alex Rivera");
        request.setNomineeEmail(nomineeEmail);
        request.setPractice("Cloud Engineering");
        request.setLocation("Dublin");
        request.setCategory(AwardCategory.CUSTOMER_IMPACT);
        request.setWhatText("Led the automation of the client onboarding pipeline, cutting a "
                + "five-day manual process down to under four hours for the whole team.");
        request.setHowText("She proactively took ownership of the migration without being "
                + "asked, showing real drive to get it done for the client, putting the "
                + "customer first throughout.");
        return request;
    }

    @Test
    void submitPersistsAsPendingReviewWithRealTagging() {
        Nomination saved = service.submit(request("integration.a1@company.com", "integration.a2@company.com"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(NominationStatus.PENDING_REVIEW);

        Nomination reloaded = nominationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getNomineeEmail()).isEqualTo("integration.a2@company.com");
    }

    @Test
    void approveTransitionsToApprovedAndPersistsAuditWithTwoComms() {
        Nomination submitted = service.submit(request("integration.b1@company.com", "integration.b2@company.com"));

        ApproveRequest approve = new ApproveRequest();
        approve.setCoordinatorEmail("colette@company.com");
        approve.setComment("Well deserved");

        Nomination approved = service.approve(submitted.getId(), approve);

        assertThat(approved.getStatus()).isEqualTo(NominationStatus.APPROVED);
        assertThat(approved.getDecisionDate()).isNotNull();
        assertThat(approved.getCommsSentDate()).isNotNull();

        List<AuditLogEntry> log = auditLogRepository.findByNominationIdOrderByOccurredAtAsc(submitted.getId());
        assertThat(log).hasSize(1);
        assertThat(log.get(0).getComms()).hasSize(2);
    }

    @Test
    void rejectTransitionsToRejectedAndPersistsAuditWithOneComm() {
        Nomination submitted = service.submit(request("integration.c1@company.com", "integration.c2@company.com"));

        ReviewDecisionRequest reject = new ReviewDecisionRequest();
        reject.setCoordinatorEmail("colette@company.com");
        reject.setReason("Describes routine duties rather than a standout contribution.");

        Nomination rejected = service.reject(submitted.getId(), reject);

        assertThat(rejected.getStatus()).isEqualTo(NominationStatus.REJECTED);
        assertThat(rejected.getRejectionReason())
                .isEqualTo("Describes routine duties rather than a standout contribution.");

        List<AuditLogEntry> log = auditLogRepository.findByNominationIdOrderByOccurredAtAsc(submitted.getId());
        assertThat(log).hasSize(1);
        assertThat(log.get(0).getComms()).hasSize(1);
    }

    @Test
    void requestResubmissionAllowsExactlyOneRevisionForTheSameOriginal() {
        Nomination submitted = service.submit(request("integration.d1@company.com", "integration.d2@company.com"));

        ReviewDecisionRequest requestMoreDetail = new ReviewDecisionRequest();
        requestMoreDetail.setCoordinatorEmail("colette@company.com");
        requestMoreDetail.setReason("A specific example with a measurable outcome.");

        Nomination sentBack = service.requestResubmission(submitted.getId(), requestMoreDetail);
        assertThat(sentBack.getStatus()).isEqualTo(NominationStatus.NEEDS_RESUBMISSION);

        NominationRequest revision = request("integration.d1@company.com", "integration.d2@company.com");
        revision.setOriginalNominationId(submitted.getId());
        Nomination resubmitted = service.submit(revision);

        assertThat(resubmitted.getId()).isNotEqualTo(submitted.getId());
        assertThat(resubmitted.getStatus()).isEqualTo(NominationStatus.PENDING_REVIEW);

        NominationRequest secondRevision = request("integration.d1@company.com", "integration.d2@company.com");
        secondRevision.setOriginalNominationId(submitted.getId());

        assertThrows(QuarterLimitReachedException.class, () -> service.submit(secondRevision));
    }
}
