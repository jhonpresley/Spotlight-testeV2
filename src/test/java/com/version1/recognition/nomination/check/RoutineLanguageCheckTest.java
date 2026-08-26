package com.version1.recognition.nomination.check;

import static org.assertj.core.api.Assertions.assertThat;

import com.version1.recognition.nomination.model.AiFlag;
import com.version1.recognition.nomination.model.AwardCategory;
import com.version1.recognition.nomination.model.CoreValue;
import com.version1.recognition.nomination.model.Nomination;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RoutineLanguageCheckTest {

    private final RoutineLanguageCheck check = new RoutineLanguageCheck();

    private Nomination nomination(String whatText, String howText) {
        return new Nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com",
                "Cloud Engineering", "Dublin", AwardCategory.CUSTOMER_IMPACT, CoreValue.CUSTOMER_FIRST,
                whatText, howText, null);
    }

    @Test
    void flagsRoutineDutyPhraseOnly() {
        Nomination nom = nomination("She completed on time as expected.", "Nothing special.");

        Optional<String> result = check.evaluate(nom, List.of());

        assertThat(result).isPresent();
        assertThat(result.get()).contains("routine duties");
        assertThat(result.get()).doesNotContain("generic praise");
    }

    @Test
    void flagsGenericPraisePhraseOnly() {
        Nomination nom = nomination("Such a great teammate this quarter.", "Really appreciated by everyone.");

        Optional<String> result = check.evaluate(nom, List.of());

        assertThat(result).isPresent();
        assertThat(result.get()).contains("generic praise");
        assertThat(result.get()).doesNotContain("routine duties");
    }

    @Test
    void flagsWhenBothRoutineAndGenericPhrasesArePresent() {
        Nomination nom = nomination("Completed on time as always.", "Such a great teammate.");

        Optional<String> result = check.evaluate(nom, List.of());

        assertThat(result).isPresent();
        assertThat(result.get()).contains("routine duties");
        assertThat(result.get()).contains("generic praise");
    }

    @Test
    void passesWhenNeitherPhraseFamilyIsPresent() {
        Nomination nom = nomination("Redesigned the onboarding flow end to end.",
                "Cut new-hire ramp time by half.");

        assertThat(check.evaluate(nom, List.of())).isEmpty();
    }

    @Test
    void matchingIsCaseInsensitive() {
        Nomination nom = nomination("TEAM PLAYER through and through.", "N/A");

        assertThat(check.evaluate(nom, List.of())).isPresent();
    }

    @Test
    void flagIsRoutineTaskLanguage() {
        assertThat(check.flag()).isEqualTo(AiFlag.ROUTINE_TASK_LANGUAGE);
    }
}
