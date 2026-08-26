package com.version1.recognition.nomination.service;

import com.version1.recognition.nomination.comms.NotificationService;
import com.version1.recognition.nomination.comms.SentEmail;
import com.version1.recognition.nomination.evaluation.NominationEvaluator;
import com.version1.recognition.nomination.exception.InvalidReviewStateException;
import com.version1.recognition.nomination.exception.QuarterLimitReachedException;
import com.version1.recognition.nomination.model.AwardCategory;
import com.version1.recognition.nomination.model.CoreValue;
import com.version1.recognition.nomination.model.Nomination;
import com.version1.recognition.nomination.model.NominationStatus;
import com.version1.recognition.nomination.repository.AuditLogRepository;
import com.version1.recognition.nomination.repository.NominationRepository;
import com.version1.recognition.nomination.web.ApproveRequest;
import com.version1.recognition.nomination.web.NominationRequest;
import com.version1.recognition.nomination.web.ReviewDecisionRequest;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NominationServiceTest {

    @Mock
    private NominationRepository repository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private TaggingService taggingService;

    @Mock
    private NominationEvaluator evaluator;

    @InjectMocks
    private NominationService service;

    @Test
    void submitAllowsSingleResubmissionForCurrentQuarter() {
        UUID originalId = UUID.randomUUID();
        Nomination original = new Nomination(
                "Calvin Ho",
                "calvin.ho@version1.com",
                "Alex Rivera",
                "alex.rivera@version1.com",
                "Cloud Engineering",
                "Dublin",
                AwardCategory.CUSTOMER_IMPACT,
                CoreValue.CUSTOMER_FIRST,
                "Delivered the migration",
                "Helped the client through a tight release",
                null);
        ReflectionTestUtils.setField(original, "id", originalId);
        original.setStatus(NominationStatus.NEEDS_RESUBMISSION);
        ReflectionTestUtils.setField(original, "submittedAt", Instant.now());

        when(repository.findAll()).thenReturn(List.of(original));
        when(taggingService.tag(any(), anyList())).thenReturn(Collections.emptyList());
        when(evaluator.isAvailable()).thenReturn(false);
        when(repository.save(any(Nomination.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NominationRequest request = new NominationRequest();
        request.setNominatorName("Calvin Ho");
        request.setNominatorEmail("calvin.ho@version1.com");
        request.setNomineeName("Alex Rivera");
        request.setNomineeEmail("alex.rivera@version1.com");
        request.setPractice("Cloud Engineering");
        request.setLocation("Dublin");
        request.setCategory(AwardCategory.CUSTOMER_IMPACT);
        request.setWhatText("Delivered the migration");
        request.setHowText("Helped the client through a tight release");
        request.setOriginalNominationId(originalId);

        assertDoesNotThrow(() -> service.submit(request));
    }

    @Test
    void submitRejectsSecondResubmissionForSameOriginalNomination() {
        UUID originalId = UUID.randomUUID();
        Nomination original = new Nomination(
                "Calvin Ho",
                "calvin.ho@version1.com",
                "Alex Rivera",
                "alex.rivera@version1.com",
                "Cloud Engineering",
                "Dublin",
                AwardCategory.CUSTOMER_IMPACT,
                CoreValue.CUSTOMER_FIRST,
                "Delivered the migration",
                "Helped the client through a tight release",
                null);
        ReflectionTestUtils.setField(original, "id", originalId);
        original.setStatus(NominationStatus.NEEDS_RESUBMISSION);
        ReflectionTestUtils.setField(original, "submittedAt", Instant.now());

        Nomination existingResubmission = new Nomination(
                "Calvin Ho",
                "calvin.ho@version1.com",
                "Alex Rivera",
                "alex.rivera@version1.com",
                "Cloud Engineering",
                "Dublin",
                AwardCategory.CUSTOMER_IMPACT,
                CoreValue.CUSTOMER_FIRST,
                "Delivered the migration with more detail",
                "Improved the client comms and support in the release",
                originalId);
        ReflectionTestUtils.setField(existingResubmission, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(existingResubmission, "submittedAt", Instant.now());
        existingResubmission.setStatus(NominationStatus.PENDING_REVIEW);

        when(repository.findAll()).thenReturn(List.of(original, existingResubmission));

        NominationRequest request = new NominationRequest();
        request.setNominatorName("Calvin Ho");
        request.setNominatorEmail("calvin.ho@version1.com");
        request.setNomineeName("Alex Rivera");
        request.setNomineeEmail("alex.rivera@version1.com");
        request.setPractice("Cloud Engineering");
        request.setLocation("Dublin");
        request.setCategory(AwardCategory.CUSTOMER_IMPACT);
        request.setWhatText("Delivered the migration with final detail");
        request.setHowText("Improved the client comms and support in the release");
        request.setOriginalNominationId(originalId);

        assertThrows(QuarterLimitReachedException.class, () -> service.submit(request));
    }

    private Nomination pendingNomination(UUID id) {
        Nomination nomination = new Nomination(
                "Calvin Ho", "calvin.ho@version1.com", "Alex Rivera", "alex.rivera@version1.com",
                "Cloud Engineering", "Dublin", AwardCategory.CUSTOMER_IMPACT, CoreValue.CUSTOMER_FIRST,
                "Delivered the migration", "Helped the client through a tight release", null);
        ReflectionTestUtils.setField(nomination, "id", id);
        return nomination;
    }

    @Test
    void approveSetsStatusApprovedAndSendsCommsToBothNominatorAndNominee() {
        UUID id = UUID.randomUUID();
        Nomination nomination = pendingNomination(id);

        when(repository.findById(id)).thenReturn(java.util.Optional.of(nomination));
        when(repository.save(any(Nomination.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationService.sendApprovalComms(any(), any()))
                .thenReturn(new SentEmail("calvin.ho@version1.com", "Approved", "body"));
        when(notificationService.sendNomineeAwardComms(any()))
                .thenReturn(new SentEmail("alex.rivera@version1.com", "Congratulations", "body"));

        ApproveRequest request = new ApproveRequest();
        request.setCoordinatorEmail("colette@version1.com");
        request.setComment("Well deserved");

        Nomination result = service.approve(id, request);

        assertThat(result.getStatus()).isEqualTo(NominationStatus.APPROVED);
        assertThat(result.getCoordinatorEmail()).isEqualTo("colette@version1.com");
        verify(notificationService).sendApprovalComms(nomination, "Well deserved");
        verify(notificationService).sendNomineeAwardComms(nomination);
        verify(auditLogRepository).save(any());
    }

    @Test
    void approveThrowsWhenNominationIsNotPendingReview() {
        UUID id = UUID.randomUUID();
        Nomination nomination = pendingNomination(id);
        nomination.setStatus(NominationStatus.APPROVED);

        when(repository.findById(id)).thenReturn(java.util.Optional.of(nomination));

        ApproveRequest request = new ApproveRequest();
        request.setCoordinatorEmail("colette@version1.com");

        assertThrows(InvalidReviewStateException.class, () -> service.approve(id, request));
    }

    @Test
    void rejectSetsStatusRejectedAndSendsCommsOnlyToTheNominator() {
        UUID id = UUID.randomUUID();
        Nomination nomination = pendingNomination(id);

        when(repository.findById(id)).thenReturn(java.util.Optional.of(nomination));
        when(repository.save(any(Nomination.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationService.sendDeclineComms(any(), any()))
                .thenReturn(new SentEmail("calvin.ho@version1.com", "Not this time", "body"));

        ReviewDecisionRequest request = new ReviewDecisionRequest();
        request.setCoordinatorEmail("colette@version1.com");
        request.setReason("Describes routine duties rather than a standout contribution.");

        Nomination result = service.reject(id, request);

        assertThat(result.getStatus()).isEqualTo(NominationStatus.REJECTED);
        assertThat(result.getRejectionReason())
                .isEqualTo("Describes routine duties rather than a standout contribution.");
        verify(notificationService).sendDeclineComms(nomination, null);
        verify(notificationService, org.mockito.Mockito.never()).sendNomineeAwardComms(any());
    }

    @Test
    void rejectThrowsWhenNominationIsNotPendingReview() {
        UUID id = UUID.randomUUID();
        Nomination nomination = pendingNomination(id);
        nomination.setStatus(NominationStatus.NEEDS_RESUBMISSION);

        when(repository.findById(id)).thenReturn(java.util.Optional.of(nomination));

        ReviewDecisionRequest request = new ReviewDecisionRequest();
        request.setCoordinatorEmail("colette@version1.com");
        request.setReason("Not enough detail.");

        assertThrows(InvalidReviewStateException.class, () -> service.reject(id, request));
    }

    @Test
    void requestResubmissionSetsStatusNeedsResubmissionAndRecordsTheReason() {
        UUID id = UUID.randomUUID();
        Nomination nomination = pendingNomination(id);

        when(repository.findById(id)).thenReturn(java.util.Optional.of(nomination));
        when(repository.save(any(Nomination.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationService.sendResubmissionRequestedComms(any(), any()))
                .thenReturn(new SentEmail("calvin.ho@version1.com", "Pending", "body"));

        ReviewDecisionRequest request = new ReviewDecisionRequest();
        request.setCoordinatorEmail("colette@version1.com");
        request.setReason("A specific example with a measurable outcome.");

        Nomination result = service.requestResubmission(id, request);

        assertThat(result.getStatus()).isEqualTo(NominationStatus.NEEDS_RESUBMISSION);
        assertThat(result.getRejectionReason()).isEqualTo("A specific example with a measurable outcome.");
        verify(auditLogRepository).save(any());
    }
}
