package com.version1.recognition.nomination.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.version1.recognition.nomination.model.AwardCategory;
import com.version1.recognition.nomination.model.CoreValue;
import com.version1.recognition.nomination.model.Nomination;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvaluatorSelectorTest {

    @Mock
    private GroqNominationEvaluator groq;

    @Mock
    private MockNominationEvaluator mock;

    private Nomination nomination() {
        return new Nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com",
                "Cloud Engineering", "Dublin", AwardCategory.CUSTOMER_IMPACT, CoreValue.CUSTOMER_FIRST,
                "Delivered the migration", "Helped the client through a tight release", null);
    }

    @Test
    void modeAutoUsesGroqWhenGroqIsAvailable() throws AiEvaluationException {
        lenient().when(groq.isAvailable()).thenReturn(true);
        EvaluatorSelector selector = new EvaluatorSelector(groq, mock, "auto");
        AiEvaluationResult groqResult = new AiEvaluationResult(90, "groq says so", java.util.List.of(), "v1");
        when(groq.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(groqResult);

        assertThat(selector.isAvailable()).isTrue();
        assertThat(selector.evaluate(nomination())).isEqualTo(groqResult);
        assertThat(selector.describeActive()).isEqualTo("Groq (live model)");
    }

    @Test
    void modeAutoFallsBackToMockWhenGroqIsNotAvailable() throws AiEvaluationException {
        when(groq.isAvailable()).thenReturn(false);
        when(mock.isAvailable()).thenReturn(true);
        EvaluatorSelector selector = new EvaluatorSelector(groq, mock, "auto");
        AiEvaluationResult mockResult = new AiEvaluationResult(85, "mock says so", java.util.List.of(), "mock");
        when(mock.evaluate(org.mockito.ArgumentMatchers.any())).thenReturn(mockResult);

        assertThat(selector.isAvailable()).isTrue();
        assertThat(selector.evaluate(nomination())).isEqualTo(mockResult);
        assertThat(selector.describeActive()).contains("no GROQ_API_KEY set");
    }

    @Test
    void modeMockAlwaysUsesMockEvenWhenGroqIsAvailable() throws AiEvaluationException {
        when(mock.isAvailable()).thenReturn(true);
        EvaluatorSelector selector = new EvaluatorSelector(groq, mock, "mock");

        assertThat(selector.isAvailable()).isTrue();
        selector.evaluate(nomination());

        verify(mock).evaluate(org.mockito.ArgumentMatchers.any());
        assertThat(selector.describeActive()).contains("mock (rule-of-thumb, no network)");
    }

    @Test
    void modeGroqAlwaysUsesGroqEvenWhenUnavailable() {
        when(groq.isAvailable()).thenReturn(false);
        EvaluatorSelector selector = new EvaluatorSelector(groq, mock, "groq");

        assertThat(selector.isAvailable()).isFalse();
        assertThat(selector.describeActive()).isEqualTo("Groq (live model)");
    }

    @Test
    void blankModeDefaultsToAuto() {
        lenient().when(groq.isAvailable()).thenReturn(true);
        EvaluatorSelector selector = new EvaluatorSelector(groq, mock, null);

        assertThat(selector.describeActive()).isEqualTo("Groq (live model)");
    }
}
