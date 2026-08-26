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

class RepeatNominationCheckTest {

    private final RepeatNominationCheck check = new RepeatNominationCheck();

    private Nomination nomination(String nominatorName, String nominatorEmail,
                                   String nomineeName, String nomineeEmail) {
        return new Nomination(nominatorName, nominatorEmail, nomineeName, nomineeEmail,
                "Cloud Engineering", "Dublin", AwardCategory.CUSTOMER_IMPACT, CoreValue.CUSTOMER_FIRST,
                "Delivered the migration", "Helped the client through a tight release", null);
    }

    @Test
    void flagsWhenSameNomineeWasNominatedInTheImmediatelyPrecedingQuarter() {
        // Checked nomination submitted in Q2 2024; other nomination for the same
        // nominee submitted in Q1 2024 - the immediately preceding quarter.
        Nomination nom = nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com");
        ReflectionTestUtils.setField(nom, "submittedAt", Instant.parse("2024-05-15T00:00:00Z"));

        Nomination other = nomination("Jamie Fox", "jamie@company.com", "Alex Rivera", "alex@company.com");
        ReflectionTestUtils.setField(other, "submittedAt", Instant.parse("2024-02-10T00:00:00Z"));

        Optional<String> result = check.evaluate(nom, List.of(other));

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Q1 2024");
    }

    @Test
    void doesNotFlagWhenTheOtherNominationWasTwoQuartersAgo() {
        // Checked nomination submitted in Q3 2024; other nomination submitted in
        // Q1 2024 - two quarters back, not the immediately preceding one.
        Nomination nom = nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com");
        ReflectionTestUtils.setField(nom, "submittedAt", Instant.parse("2024-08-15T00:00:00Z"));

        Nomination other = nomination("Jamie Fox", "jamie@company.com", "Alex Rivera", "alex@company.com");
        ReflectionTestUtils.setField(other, "submittedAt", Instant.parse("2024-02-10T00:00:00Z"));

        assertThat(check.evaluate(nom, List.of(other))).isEmpty();
    }

    @Test
    void shortCircuitsWhenSubmittedAtIsNull() {
        Nomination nom = nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com");
        ReflectionTestUtils.setField(nom, "submittedAt", null);

        Nomination other = nomination("Jamie Fox", "jamie@company.com", "Alex Rivera", "alex@company.com");
        ReflectionTestUtils.setField(other, "submittedAt", Instant.parse("2024-02-10T00:00:00Z"));

        assertThat(check.evaluate(nom, List.of(other))).isEmpty();
    }

    @Test
    void shortCircuitsWhenNomineeEmailIsNull() {
        Nomination nom = nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com");
        ReflectionTestUtils.setField(nom, "submittedAt", Instant.parse("2024-05-15T00:00:00Z"));
        ReflectionTestUtils.setField(nom, "nomineeEmail", null);

        assertThat(check.evaluate(nom, List.of())).isEmpty();
    }

    @Test
    void flagIsRepeatNominationConsecutiveQuarter() {
        assertThat(check.flag()).isEqualTo(AiFlag.REPEAT_NOMINATION_CONSECUTIVE_QUARTER);
    }
}
