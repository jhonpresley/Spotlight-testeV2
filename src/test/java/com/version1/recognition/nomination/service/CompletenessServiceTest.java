package com.version1.recognition.nomination.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.version1.recognition.nomination.model.AwardCategory;
import com.version1.recognition.nomination.model.CoreValue;
import com.version1.recognition.nomination.model.Nomination;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompletenessServiceTest {

    private final CompletenessService service = new CompletenessService();

    @Test
    void everyCriterionPassesForAWellFormedNomination() {
        Nomination nom = new Nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com",
                "Cloud Engineering", "Dublin", AwardCategory.CUSTOMER_IMPACT, CoreValue.CUSTOMER_FIRST,
                "Led the automation of the client onboarding pipeline, cutting a five-day manual "
                        + "process down to under four hours for the whole team.",
                "She proactively took ownership of the migration without being asked, showing "
                        + "real drive to get it done for the client.",
                null);

        Map<CompletenessCriterion, Boolean> results = service.assess(nom);

        assertThat(results.values()).allMatch(Boolean::booleanValue);
        assertThat(service.failing(nom)).isEmpty();
        assertThat(service.toResubmissionMessage(nom)).isEmpty();
    }

    @Test
    void everyCriterionFailsForAThinNomination() {
        Nomination nom = new Nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com",
                "Cloud Engineering", "Dublin", null, CoreValue.CUSTOMER_FIRST,
                "Did stuff.", "Completed on time as always.", null);

        List<CompletenessCriterion> failed = service.failing(nom);

        assertThat(failed).containsExactly(
                CompletenessCriterion.WHAT_HAS_DETAIL,
                CompletenessCriterion.WHAT_HAS_IMPACT,
                CompletenessCriterion.HOW_HAS_DETAIL,
                CompletenessCriterion.HOW_NAMES_VALUE,
                CompletenessCriterion.CATEGORY_SELECTED,
                CompletenessCriterion.NOT_ROUTINE_LANGUAGE);
    }

    @Test
    void resubmissionMessageListsEveryFailureWithItsRemedy() {
        Nomination nom = new Nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com",
                "Cloud Engineering", "Dublin", null, CoreValue.CUSTOMER_FIRST,
                "Did stuff.", "Completed on time as always.", null);

        String message = service.toResubmissionMessage(nom);

        assertThat(message).contains("are 6 things");
        assertThat(message).contains("1. " + CompletenessCriterion.WHAT_HAS_DETAIL.getLabel());
        assertThat(message).contains(CompletenessCriterion.WHAT_HAS_DETAIL.getRemedy());
    }
}
