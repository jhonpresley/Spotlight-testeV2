package com.version1.recognition.nomination.check;

import static org.assertj.core.api.Assertions.assertThat;

import com.version1.recognition.nomination.model.AiFlag;
import com.version1.recognition.nomination.model.AwardCategory;
import com.version1.recognition.nomination.model.CoreValue;
import com.version1.recognition.nomination.model.Nomination;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WeakJustificationCheckTest {

    private final WeakJustificationCheck check = new WeakJustificationCheck();

    private Nomination nomination(String whatText, String howText) {
        return new Nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com",
                "Cloud Engineering", "Dublin", AwardCategory.CUSTOMER_IMPACT, CoreValue.CUSTOMER_FIRST,
                whatText, howText, null);
    }

    @Test
    void passesWhenOnlyOneOfThreeSignalsFails() {
        // Long (>=150 chars combined) and names a core value ("customer first"),
        // but has no digits - exactly one failing signal.
        Nomination nom = nomination(
                "Delivered the client migration ahead of schedule, coordinating several teams across "
                        + "many time zones to make sure the go-live date never moved, always putting the "
                        + "customer first throughout the whole project timeline.",
                "N/A");

        assertThat(check.evaluate(nom, List.of())).isEmpty();
    }

    @Test
    void flagsWhenExactlyTwoOfThreeSignalsFail() {
        // Short and no digits, but names a core value - two failing signals.
        Nomination nom = nomination("Helped smoothly with the account.",
                "Went beyond expectations for the client, showing customer first thinking.");

        Optional<String> result = check.evaluate(nom, List.of());

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Thin on 2 of 3 signals");
    }

    @Test
    void flagsWhenAllThreeSignalsFail() {
        // Short, no digits, and no core value named.
        Nomination nom = nomination("Did well.", "Nice effort overall.");

        Optional<String> result = check.evaluate(nom, List.of());

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Thin on 3 of 3 signals");
    }

    @Test
    void flagIsWeakJustification() {
        assertThat(check.flag()).isEqualTo(AiFlag.WEAK_JUSTIFICATION);
    }
}
