package com.version1.recognition.nomination.check;

import static org.assertj.core.api.Assertions.assertThat;

import com.version1.recognition.nomination.model.AiFlag;
import com.version1.recognition.nomination.model.AwardCategory;
import com.version1.recognition.nomination.model.CoreValue;
import com.version1.recognition.nomination.model.Nomination;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SelfNominationCheckTest {

    private final SelfNominationCheck check = new SelfNominationCheck();

    private Nomination nomination(String nominatorName, String nominatorEmail,
                                   String nomineeName, String nomineeEmail) {
        return new Nomination(nominatorName, nominatorEmail, nomineeName, nomineeEmail,
                "Cloud Engineering", "Dublin", AwardCategory.CUSTOMER_IMPACT, CoreValue.CUSTOMER_FIRST,
                "Delivered the migration", "Helped the client through a tight release", null);
    }

    @Test
    void flagsWhenNominatorAndNomineeShareAnEmailAddress() {
        Nomination nom = nomination("Alex Rivera", "alex@company.com", "Different Name", "alex@company.com");

        Optional<String> result = check.evaluate(nom, List.of());

        assertThat(result).isPresent();
        assertThat(result.get()).contains("same email address");
    }

    @Test
    void flagsWhenNominatorAndNomineeShareANameOnDifferentEmails() {
        Nomination nom = nomination("Jamie Fox", "jamie.a@company.com", "Jamie Fox", "jamie.b@company.com");

        Optional<String> result = check.evaluate(nom, List.of());

        assertThat(result).isPresent();
        assertThat(result.get()).contains("same name");
    }

    @Test
    void passesWhenNominatorAndNomineeAreDifferentPeople() {
        Nomination nom = nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com");

        assertThat(check.evaluate(nom, List.of())).isEmpty();
    }

    @Test
    void flagIsSelfNomination() {
        assertThat(check.flag()).isEqualTo(AiFlag.SELF_NOMINATION);
    }
}
