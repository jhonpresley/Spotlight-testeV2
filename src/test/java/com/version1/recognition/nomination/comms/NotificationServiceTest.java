package com.version1.recognition.nomination.comms;

import static org.assertj.core.api.Assertions.assertThat;

import com.version1.recognition.nomination.model.AwardCategory;
import com.version1.recognition.nomination.model.CoreValue;
import com.version1.recognition.nomination.model.Nomination;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationServiceTest {

    private final NotificationService service = new NotificationService();

    private Nomination nomination() {
        return new Nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com",
                "Cloud Engineering", "Dublin", AwardCategory.CUSTOMER_IMPACT, CoreValue.CUSTOMER_FIRST,
                "Delivered the migration", "Helped the client through a tight release", null);
    }

    @Test
    void approvalCommsGoToTheNominatorAndIncludeTheWriteUp() {
        Nomination nom = nomination();

        SentEmail email = service.sendApprovalComms(nom, null);

        assertThat(email.getRecipient()).isEqualTo("calvin@company.com");
        assertThat(email.getSubject()).contains("Alex Rivera");
        assertThat(email.getBody()).contains("Delivered the migration");
        assertThat(email.getBody()).contains("Helped the client through a tight release");
    }

    @Test
    void approvalCommsIncludeTheCoordinatorCommentOnlyWhenNonBlank() {
        Nomination nom = nomination();

        SentEmail withComment = service.sendApprovalComms(nom, "Well deserved!");
        SentEmail withoutComment = service.sendApprovalComms(nom, "   ");

        assertThat(withComment.getBody()).contains("Well deserved!");
        assertThat(withoutComment.getBody()).doesNotContain("A note from the recognition team");
    }

    @Test
    void nomineeAwardCommsGoToTheNomineeAndQuoteTheNomination() {
        Nomination nom = nomination();

        SentEmail email = service.sendNomineeAwardComms(nom);

        assertThat(email.getRecipient()).isEqualTo("alex@company.com");
        assertThat(email.getBody()).contains("Calvin Ho recognised you");
        assertThat(email.getBody()).contains("Delivered the migration");
    }

    @Test
    void declineCommsGoOnlyToTheNominatorAndIncludeTheReason() {
        Nomination nom = nomination();
        nom.setRejectionReason("Describes routine duties rather than a standout contribution.");

        SentEmail email = service.sendDeclineComms(nom, null);

        assertThat(email.getRecipient()).isEqualTo("calvin@company.com");
        assertThat(email.getRecipient()).isNotEqualTo(nom.getNomineeEmail());
        assertThat(email.getBody()).contains("Describes routine duties rather than a standout contribution.");
    }

    @Test
    void declineCommsIncludeTheCoordinatorCommentOnlyWhenNonBlank() {
        Nomination nom = nomination();
        nom.setRejectionReason("Not enough detail.");

        SentEmail withComment = service.sendDeclineComms(nom, "Please resubmit next quarter.");
        SentEmail withoutComment = service.sendDeclineComms(nom, "");

        assertThat(withComment.getBody()).contains("Please resubmit next quarter.");
        assertThat(withoutComment.getBody()).doesNotContain("Additional note:");
    }

    @Test
    void resubmissionRequestedCommsGoToTheNominatorAndQuoteTheOriginalWording() {
        Nomination nom = nomination();
        nom.setRejectionReason("A specific example with a measurable outcome.");

        SentEmail email = service.sendResubmissionRequestedComms(nom, null);

        assertThat(email.getRecipient()).isEqualTo("calvin@company.com");
        assertThat(email.getBody()).contains("A specific example with a measurable outcome.");
        assertThat(email.getBody()).contains("Delivered the migration");
        assertThat(email.getBody()).contains("Helped the client through a tight release");
    }

    @Test
    void everyCommsMessageReferencesTheNominationId() {
        Nomination nom = nomination();
        ReflectionTestUtils.setField(nom, "id", java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"));

        SentEmail email = service.sendApprovalComms(nom, null);

        assertThat(email.getBody()).contains("Reference: 11111111-1111-1111-1111-111111111111");
    }
}
