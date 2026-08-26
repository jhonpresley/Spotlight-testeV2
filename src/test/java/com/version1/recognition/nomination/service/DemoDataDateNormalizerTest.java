package com.version1.recognition.nomination.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.version1.recognition.nomination.model.Quarter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * The seed's dates are hardcoded absolute values, correct only for the
 * quarter they were authored in (Q3 2026) - see the class under test's own
 * Javadoc. These tests inject a fake "current quarter" rather than waiting
 * for the calendar to actually reach one, which is the whole reason the
 * package-private constructor exists.
 */
@ExtendWith(MockitoExtension.class)
class DemoDataDateNormalizerTest {

    private static final String DEMO_ID = "a1000001-0000-4000-8000-000000000001";
    private static final String PREVIOUS_QUARTER_DEMO_ID = "a1000010-0000-4000-8000-000000000010";

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void doesNothingWhenTheRealQuarterMatchesTheAuthoredOne() {
        stubDemoRows(new DemoDataDateNormalizer.DemoRow(DEMO_ID, Instant.parse("2026-08-17T09:14:00Z")));

        // Still Q3 2026 - exactly the quarter the seed was written for.
        Quarter stillTheSameQuarter = Quarter.of(Instant.parse("2026-08-01T00:00:00Z"));
        DemoDataDateNormalizer normalizer = new DemoDataDateNormalizer(jdbc, () -> stillTheSameQuarter);

        int shifted = normalizer.normalize();

        assertThat(shifted).isZero();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void rebasesACurrentQuarterRowOntoWhicheverQuarterItReallyIs() {
        stubDemoRows(new DemoDataDateNormalizer.DemoRow(DEMO_ID, Instant.parse("2026-08-17T09:14:00Z")));
        when(jdbc.queryForList(anyString(), eq(String.class), any(Object[].class))).thenReturn(List.of());

        // One quarter on from when the seed was authored.
        Quarter fakeNow = Quarter.of(Instant.parse("2026-10-15T00:00:00Z"));
        DemoDataDateNormalizer normalizer = new DemoDataDateNormalizer(jdbc, () -> fakeNow);

        int shifted = normalizer.normalize();

        assertThat(shifted).isEqualTo(1);
        // Q3 2026 (the row's authored quarter) starts 1 July, not the 1 August
        // used to pick that quarter above - start() is the calendar quarter
        // boundary, not the anchor instant used to identify which quarter it is.
        long expectedShiftSeconds = Duration.between(
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z")).toSeconds();
        verify(jdbc).update(anyString(), eq(expectedShiftSeconds), eq(expectedShiftSeconds),
                eq(expectedShiftSeconds), eq(DEMO_ID));
    }

    @Test
    void rebasesAPreviousQuarterRowOntoTheQuarterBeforeWhicheverIsRealNow() {
        stubDemoRows(new DemoDataDateNormalizer.DemoRow(
                PREVIOUS_QUARTER_DEMO_ID, Instant.parse("2026-04-22T13:27:00Z")));
        when(jdbc.queryForList(anyString(), eq(String.class), any(Object[].class))).thenReturn(List.of());

        Quarter fakeNow = Quarter.of(Instant.parse("2026-10-15T00:00:00Z")); // Q4 2026
        DemoDataDateNormalizer normalizer = new DemoDataDateNormalizer(jdbc, () -> fakeNow);

        int shifted = normalizer.normalize();

        // Authored for Q2 2026 (one quarter before the seed's Q3 2026), so it
        // lands one quarter before whatever "now" resolves to: Q3 2026.
        assertThat(shifted).isEqualTo(1);
        long expectedShiftSeconds = Duration.between(
                Instant.parse("2026-04-01T00:00:00Z"), Instant.parse("2026-07-01T00:00:00Z")).toSeconds();
        verify(jdbc).update(anyString(), eq(expectedShiftSeconds), eq(expectedShiftSeconds),
                eq(expectedShiftSeconds), eq(PREVIOUS_QUARTER_DEMO_ID));
    }

    @Test
    void leavesARowFromNeitherSeedQuarterUntouched() {
        // In real operation normalize() only ever sees rows matching the demo
        // id prefix, so this can't happen with a genuine user submission - it
        // exercises the "not one of ours" branch directly instead.
        stubDemoRows(new DemoDataDateNormalizer.DemoRow(DEMO_ID, Instant.parse("2025-01-05T00:00:00Z")));

        Quarter fakeNow = Quarter.of(Instant.parse("2026-10-15T00:00:00Z"));
        DemoDataDateNormalizer normalizer = new DemoDataDateNormalizer(jdbc, () -> fakeNow);

        int shifted = normalizer.normalize();

        assertThat(shifted).isZero();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void shiftsTheAuditTrailForRowsThatHaveOne() {
        stubDemoRows(new DemoDataDateNormalizer.DemoRow(DEMO_ID, Instant.parse("2026-08-17T09:14:00Z")));
        String auditLogId = "b2000001-0000-4000-8000-000000000001";
        when(jdbc.queryForList(anyString(), eq(String.class), any(Object[].class)))
                .thenReturn(List.of(auditLogId));

        Quarter fakeNow = Quarter.of(Instant.parse("2026-10-15T00:00:00Z"));
        DemoDataDateNormalizer normalizer = new DemoDataDateNormalizer(jdbc, () -> fakeNow);

        normalizer.normalize();

        // Q3 2026 (the row's authored quarter) starts 1 July, not the 1 August
        // used to pick that quarter above - start() is the calendar quarter
        // boundary, not the anchor instant used to identify which quarter it is.
        long expectedShiftSeconds = Duration.between(
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z")).toSeconds();
        verify(jdbc).update(anyString(), eq(expectedShiftSeconds), eq(DEMO_ID));
        verify(jdbc).update(anyString(), eq(expectedShiftSeconds), eq(auditLogId));
    }

    @SuppressWarnings("unchecked")
    private void stubDemoRows(DemoDataDateNormalizer.DemoRow... rows) {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(rows));
    }
}
