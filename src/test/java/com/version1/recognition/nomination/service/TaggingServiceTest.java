package com.version1.recognition.nomination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.version1.recognition.nomination.check.NominationCheck;
import com.version1.recognition.nomination.model.AiFlag;
import com.version1.recognition.nomination.model.AwardCategory;
import com.version1.recognition.nomination.model.CoreValue;
import com.version1.recognition.nomination.model.FlagSource;
import com.version1.recognition.nomination.model.Nomination;
import com.version1.recognition.nomination.model.NominationFlag;
import com.version1.recognition.nomination.repository.NominationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaggingServiceTest {

    @Mock
    private NominationRepository repository;

    private Nomination nomination() {
        return new Nomination("Calvin Ho", "calvin@company.com", "Alex Rivera", "alex@company.com",
                "Cloud Engineering", "Dublin", AwardCategory.CUSTOMER_IMPACT, CoreValue.CUSTOMER_FIRST,
                "Delivered the migration", "Helped the client through a tight release", null);
    }

    private NominationCheck alwaysFlags(AiFlag flag, String reason) {
        return new NominationCheck() {
            @Override
            public AiFlag flag() {
                return flag;
            }

            @Override
            public Optional<String> evaluate(Nomination nomination, List<Nomination> allNominations) {
                return Optional.of(reason);
            }
        };
    }

    private NominationCheck neverFlags(AiFlag flag) {
        return new NominationCheck() {
            @Override
            public AiFlag flag() {
                return flag;
            }

            @Override
            public Optional<String> evaluate(Nomination nomination, List<Nomination> allNominations) {
                return Optional.empty();
            }
        };
    }

    private NominationCheck alwaysThrows(AiFlag flag) {
        return new NominationCheck() {
            @Override
            public AiFlag flag() {
                return flag;
            }

            @Override
            public Optional<String> evaluate(Nomination nomination, List<Nomination> allNominations) {
                throw new IllegalStateException("boom");
            }
        };
    }

    @Test
    void tagCollectsAFlagFromEveryCheckThatRaisesOne() {
        TaggingService service = new TaggingService(
                List.of(alwaysFlags(AiFlag.SELF_NOMINATION, "self"),
                        neverFlags(AiFlag.RECIPROCAL_NOMINATION)),
                repository);

        List<NominationFlag> flags = service.tag(nomination(), List.of());

        assertThat(flags).hasSize(1);
        assertThat(flags.get(0).getFlag()).isEqualTo(AiFlag.SELF_NOMINATION);
        assertThat(flags.get(0).getSource()).isEqualTo(FlagSource.RULE);
    }

    @Test
    void aCheckThatThrowsDoesNotCostTheOtherChecksTheirFlags() {
        TaggingService service = new TaggingService(
                List.of(alwaysThrows(AiFlag.SELF_NOMINATION),
                        alwaysFlags(AiFlag.WEAK_JUSTIFICATION, "thin")),
                repository);

        List<NominationFlag> flags = service.tag(nomination(), List.of());

        assertThat(flags).hasSize(1);
        assertThat(flags.get(0).getFlag()).isEqualTo(AiFlag.WEAK_JUSTIFICATION);
    }

    @Test
    void retagAllReplacesRuleFlagsButPreservesUnmatchedAiFlags() {
        Nomination nom = nomination();
        NominationFlag existingAiFlag = new NominationFlag(
                AiFlag.NOMINEE_NOT_ACTIVE_EMPLOYEE, FlagSource.AI, "from a previous AI evaluation");
        nom.setAiFlags(List.of(existingAiFlag));
        when(repository.findAll()).thenReturn(List.of(nom));

        TaggingService service = new TaggingService(
                List.of(alwaysFlags(AiFlag.SELF_NOMINATION, "self")), repository);

        int flaggedCount = service.retagAll();

        assertThat(flaggedCount).isEqualTo(1);
        assertThat(nom.getAiFlags()).extracting(NominationFlag::getFlag)
                .containsExactlyInAnyOrder(AiFlag.SELF_NOMINATION, AiFlag.NOMINEE_NOT_ACTIVE_EMPLOYEE);
        verify(repository).saveAll(List.of(nom));
    }

    @Test
    void retagAllDropsAnAiFlagWhenARuleNowRaisesTheSameOne() {
        Nomination nom = nomination();
        NominationFlag existingAiFlag = new NominationFlag(
                AiFlag.SELF_NOMINATION, FlagSource.AI, "an earlier AI-raised self-nomination flag");
        nom.setAiFlags(List.of(existingAiFlag));
        when(repository.findAll()).thenReturn(List.of(nom));

        TaggingService service = new TaggingService(
                List.of(alwaysFlags(AiFlag.SELF_NOMINATION, "rule now says the same thing")), repository);

        service.retagAll();

        assertThat(nom.getAiFlags()).hasSize(1);
        assertThat(nom.getAiFlags().get(0).getSource()).isEqualTo(FlagSource.RULE);
    }
}
