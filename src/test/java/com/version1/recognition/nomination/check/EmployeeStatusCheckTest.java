package com.version1.recognition.nomination.check;

import static org.assertj.core.api.Assertions.assertThat;

import com.version1.recognition.nomination.model.AiFlag;
import com.version1.recognition.nomination.model.AwardCategory;
import com.version1.recognition.nomination.model.CoreValue;
import com.version1.recognition.nomination.model.Nomination;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * This check is a documented placeholder (no HR data source wired up yet) that
 * always returns {@code Optional.empty()}. Locking that in with a test means a
 * future real implementation swap changes this file with an intentional, visible
 * red test - rather than the behavior silently changing unnoticed.
 */
class EmployeeStatusCheckTest {

    private final EmployeeStatusCheck check = new EmployeeStatusCheck();

    @Test
    void alwaysPassesUntilAnHrDataSourceIsWiredUp() {
        Nomination nom = new Nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com",
                "Cloud Engineering", "Dublin", AwardCategory.CUSTOMER_IMPACT, CoreValue.CUSTOMER_FIRST,
                "Delivered the migration", "Helped the client through a tight release", null);

        assertThat(check.evaluate(nom, List.of())).isEmpty();
    }

    @Test
    void flagIsNomineeNotActiveEmployee() {
        assertThat(check.flag()).isEqualTo(AiFlag.NOMINEE_NOT_ACTIVE_EMPLOYEE);
    }
}
