package com.version1.recognition.nomination.service;

import com.version1.recognition.nomination.model.Quarter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the demo seed inside the quarter it is supposed to demonstrate,
 * regardless of what the real calendar says.
 *
 * <p>{@code 005-seed-demo-nominations.xml} and
 * {@code 009-rebalance-demo-quarters.xml} write absolute dates - ten
 * nominations dated for what was Q3 2026 when this was authored, three for
 * the quarter before it. Liquibase runs a changeset once and never touches it
 * again, so the moment the real calendar moves past Q3 2026 every one of
 * those dates is simply wrong: Calvin's "already submitted this quarter"
 * nomination stops being in the current quarter, the one-per-quarter limit
 * stops blocking him, and the form renders where the demo (and
 * {@code quarter-limit.spec.js}) expect it not to.
 *
 * <p>Rather than re-editing the changelog every few months, this runs after
 * the seed loads - on every boot via {@link TaggingStartupRunner}, and again
 * after {@link com.version1.recognition.dev.DevResetService} replays it - and
 * slides each demo row's dates onto whichever quarter it is meant to
 * represent, keeping its exact offset from that quarter's start. A row
 * authored for "the quarter this was written in" always lands in the real
 * current quarter; one authored for "the quarter before that" always lands in
 * the quarter before the real current one. On an unchanged calendar quarter -
 * true for most of the life of this feature - every shift is zero and this is
 * a no-op.
 */
@Component
public class DemoDataDateNormalizer {

    private static final Logger log = LoggerFactory.getLogger(DemoDataDateNormalizer.class);

    /** The demo rows' ids all share this prefix - see changeset 006's own
     *  {@code nomination_id LIKE 'a10000%'}, reused here rather than invented
     *  fresh. Never matches a real submission: those get a random UUID. */
    private static final String DEMO_ID_PATTERN = "a10000%";

    /** The quarter the seed's "current quarter" dates were written against.
     *  Fixed forever - this records when the changelog was authored, not
     *  anything that should track today. */
    private static final Quarter SEED_AUTHORED_QUARTER = Quarter.of(Instant.parse("2026-08-01T00:00:00Z"));

    private final JdbcTemplate jdbc;
    private final Supplier<Quarter> currentQuarter;

    @Autowired
    public DemoDataDateNormalizer(JdbcTemplate jdbc) {
        this(jdbc, Quarter::current);
    }

    /** Test seam: lets a test simulate any real-world quarter without waiting
     *  for the calendar or touching the system clock. Production always goes
     *  through the public constructor above. */
    DemoDataDateNormalizer(JdbcTemplate jdbc, Supplier<Quarter> currentQuarter) {
        this.jdbc = jdbc;
        this.currentQuarter = currentQuarter;
    }

    /**
     * Rebases every demo nomination still dated for the quarter it was
     * authored in (or the one before it) onto the equivalent quarter today.
     *
     * @return how many nominations were shifted; 0 most of the time
     */
    @Transactional
    public int normalize() {
        List<DemoRow> rows = jdbc.query(
                "SELECT id, submitted_at FROM nominations WHERE id LIKE ?",
                (rs, rowNum) -> new DemoRow(rs.getString("id"), rs.getTimestamp("submitted_at").toInstant()),
                DEMO_ID_PATTERN);

        int shifted = 0;
        for (DemoRow row : rows) {
            Quarter authored = Quarter.of(row.submittedAt());
            Quarter target = targetQuarterFor(authored);
            if (target == null || target.equals(authored)) {
                continue;
            }

            long shiftSeconds = Duration.between(authored.start(), target.start()).toSeconds();
            shiftNomination(row.id(), shiftSeconds);
            shiftAuditTrail(row.id(), shiftSeconds);
            shifted++;
        }

        if (shifted > 0) {
            log.info("Rebased {} demo nomination(s) from {} onto the current quarter.",
                    shifted, SEED_AUTHORED_QUARTER);
        }
        return shifted;
    }

    /** Which live quarter a row authored for {@code authored} should land in,
     *  or null if this is not one of the two quarters the seed uses - a row a
     *  real user submitted, for instance, which this must never touch. */
    private Quarter targetQuarterFor(Quarter authored) {
        Quarter now = currentQuarter.get();
        if (authored.equals(SEED_AUTHORED_QUARTER)) {
            return now;
        }
        if (authored.equals(SEED_AUTHORED_QUARTER.previous())) {
            return now.previous();
        }
        return null;
    }

    private void shiftNomination(String id, long shiftSeconds) {
        // DATE_ADD on a NULL column (decision_date/comms_sent_date on a still-
        // pending nomination) is NULL, so this needs no null check.
        jdbc.update("UPDATE nominations SET "
                + "submitted_at = DATE_ADD(submitted_at, INTERVAL ? SECOND), "
                + "decision_date = DATE_ADD(decision_date, INTERVAL ? SECOND), "
                + "comms_sent_date = DATE_ADD(comms_sent_date, INTERVAL ? SECOND) "
                + "WHERE id = ?", shiftSeconds, shiftSeconds, shiftSeconds, id);
    }

    /** The audit log entries and the messages they sent carry their own
     *  timestamps, and they are what the Activity Log and audit-log endpoint
     *  actually render - leaving them in the old quarter would put a "just
     *  approved" nomination under a decision dated months ago. */
    private void shiftAuditTrail(String nominationId, long shiftSeconds) {
        List<String> auditLogIds = jdbc.queryForList(
                "SELECT id FROM nomination_audit_log WHERE nomination_id = ?", String.class, nominationId);
        if (auditLogIds.isEmpty()) {
            return;
        }

        jdbc.update("UPDATE nomination_audit_log SET occurred_at = DATE_ADD(occurred_at, INTERVAL ? SECOND) "
                + "WHERE nomination_id = ?", shiftSeconds, nominationId);

        for (String auditLogId : auditLogIds) {
            jdbc.update("UPDATE nomination_audit_comms SET sent_at = DATE_ADD(sent_at, INTERVAL ? SECOND) "
                    + "WHERE audit_log_id = ?", shiftSeconds, auditLogId);
        }
    }

    record DemoRow(String id, Instant submittedAt) {
    }
}
