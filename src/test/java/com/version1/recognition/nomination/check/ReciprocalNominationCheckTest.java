package com.version1.recognition.nomination.check;

import static org.assertj.core.api.Assertions.assertThat;

import com.version1.recognition.nomination.model.AiFlag;
import com.version1.recognition.nomination.model.AwardCategory;
import com.version1.recognition.nomination.model.CoreValue;
import com.version1.recognition.nomination.model.Nomination;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReciprocalNominationCheckTest {

    private final ReciprocalNominationCheck check = new ReciprocalNominationCheck();

    private Nomination nomination(String nominatorName, String nominatorEmail,
                                   String nomineeName, String nomineeEmail) {
        return new Nomination(nominatorName, nominatorEmail, nomineeName, nomineeEmail,
                "Cloud Engineering", "Dublin", AwardCategory.CUSTOMER_IMPACT, CoreValue.CUSTOMER_FIRST,
                "Delivered the migration", "Helped the client through a tight release", null);
    }

    @Test
    void flagsWhenTheNomineeAlsoNominatedTheNominator() {
        Nomination nom = nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com");
        Nomination other = nomination("Alex Rivera", "alex@company.com", "Calvin Ho", "calvin@company.com");
        ReflectionTestUtils.setField(other, "submittedAt", Instant.parse("2025-01-10T00:00:00Z"));

        Optional<String> result = check.evaluate(nom, List.of(other));

        assertThat(result).isPresent();
        assertThat(result.get()).contains("also nominated");
    }

    @Test
    void excludesTheNominationItselfFromTheComparison() {
        Nomination nom = nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com");

        // The list contains only the nomination being evaluated (same reference).
        assertThat(check.evaluate(nom, List.of(nom))).isEmpty();
    }

    @Test
    void comparesUnsavedRecordsByIdentityRatherThanNullIds() {
        // Neither record has been persisted, so both ids are null. The check must
        // still tell them apart (by object identity) instead of treating two
        // different unsaved rows as "the same record" because null == null.
        Nomination nom = nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com");
        Nomination other = nomination("Alex Rivera", "alex@company.com", "Calvin Ho", "calvin@company.com");

        assertThat(nom.getId()).isNull();
        assertThat(other.getId()).isNull();

        assertThat(check.evaluate(nom, List.of(other))).isPresent();
    }

    @Test
    void passesWhenNoReciprocalNominationExists() {
        Nomination nom = nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com");
        Nomination unrelated = nomination("Jamie Fox", "jamie@company.com", "Sarah Byrne", "sarah@company.com");

        assertThat(check.evaluate(nom, List.of(unrelated))).isEmpty();
    }

    @Test
    void flagIsReciprocalNomination() {
        assertThat(check.flag()).isEqualTo(AiFlag.RECIPROCAL_NOMINATION);
    }
}
