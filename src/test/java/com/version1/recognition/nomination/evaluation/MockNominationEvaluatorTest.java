package com.version1.recognition.nomination.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.version1.recognition.nomination.model.AiFlag;
import com.version1.recognition.nomination.model.AwardCategory;
import com.version1.recognition.nomination.model.CoreValue;
import com.version1.recognition.nomination.model.Nomination;
import org.junit.jupiter.api.Test;

class MockNominationEvaluatorTest {

    private final MockNominationEvaluator evaluator = new MockNominationEvaluator();

    private Nomination nomination(String whatText, String howText) {
        return new Nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com",
                "Cloud Engineering", "Dublin", AwardCategory.CUSTOMER_IMPACT, CoreValue.CUSTOMER_FIRST,
                whatText, howText, null);
    }

    @Test
    void isAlwaysAvailable() {
        assertThat(evaluator.isAvailable()).isTrue();
    }

    @Test
    void scoresFullMarksWithNoConcerns() {
        Nomination nom = nomination(
                "Redesigned the onboarding flow end to end to reduce ramp time.",
                "She carefully redesigned the onboarding flow to reduce ramp time significantly.");

        AiEvaluationResult result = evaluator.evaluate(nom);

        assertThat(result.getScore()).isEqualTo(85);
        assertThat(result.getFlags()).isEmpty();
        assertThat(result.getRationale()).isEqualTo("No language concerns detected by the mock evaluator.");
        assertThat(result.getPromptVersion()).isEqualTo("mock");
    }

    @Test
    void flagsRoutineTaskLanguageWhenWhatContainsARoutinePhrase() {
        Nomination nom = nomination(
                "Attended the meeting and gave input for the project timeline.",
                "She carefully redesigned the onboarding flow to reduce ramp time significantly.");

        AiEvaluationResult result = evaluator.evaluate(nom);

        assertThat(result.getFlags()).containsExactly(AiFlag.ROUTINE_TASK_LANGUAGE);
        assertThat(result.getScore()).isEqualTo(60);
    }

    @Test
    void flagsWeakJustificationWhenHowContainsAWeakPhrase() {
        Nomination nom = nomination(
                "Redesigned the onboarding flow end to end to reduce ramp time.",
                "Always helpful and a great teammate to everyone on the team.");

        AiEvaluationResult result = evaluator.evaluate(nom);

        assertThat(result.getFlags()).containsExactly(AiFlag.WEAK_JUSTIFICATION);
        assertThat(result.getScore()).isEqualTo(60);
    }

    @Test
    void flagsWeakJustificationWhenHowIsUnderEightWords() {
        Nomination nom = nomination(
                "Redesigned the onboarding flow end to end to reduce ramp time.",
                "Very helpful.");

        AiEvaluationResult result = evaluator.evaluate(nom);

        assertThat(result.getFlags()).containsExactly(AiFlag.WEAK_JUSTIFICATION);
    }

    @Test
    void bothFlagsCombineToTheLowestScore() {
        Nomination nom = nomination(
                "Attended the meeting and gave input for the project timeline.",
                "Always helpful and a great teammate to everyone on the team.");

        AiEvaluationResult result = evaluator.evaluate(nom);

        assertThat(result.getFlags()).containsExactlyInAnyOrder(
                AiFlag.ROUTINE_TASK_LANGUAGE, AiFlag.WEAK_JUSTIFICATION);
        assertThat(result.getScore()).isEqualTo(35);
        assertThat(result.getRationale()).contains("Mock evaluator flagged");
    }
}
