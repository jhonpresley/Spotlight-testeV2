package com.version1.recognition.dev;

import com.version1.recognition.nomination.service.DemoDataDateNormalizer;
import com.version1.recognition.nomination.service.TaggingService;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Puts the database back to the state a fresh clone would see: the thirteen
 * demo nominations, their audit history, and nothing else.
 *
 * <p><b>Why not dropAll.</b> The obvious implementation - Liquibase's
 * {@code dropAll} followed by {@code update} - is what
 * {@code frontend/e2e/global-setup.js} used to run from Maven, and it is
 * dangerous: it runs DDL against a schema this very application is holding
 * open, so it either blocks on a MySQL metadata lock or drops tables out from
 * under a live connection pool. This resets the <em>data</em> instead and
 * never touches the schema.
 *
 * <p><b>How the reseed stays in sync.</b> Rather than keeping a second copy of
 * the demo dataset here - which would drift the first time someone edited a
 * changeset - this deletes the {@code context="demo"} rows from Liquibase's
 * own bookkeeping table and asks Liquibase to run again. It replays exactly
 * the demo changesets, in changelog order, and skips every schema changeset
 * that is already applied. Adding a seventh demo changeset needs no edit here.
 *
 * <p>Only exists when {@code app.dev-tools.enabled} is true. See
 * {@link DevController}.
 */
@Service
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevResetService {

    private static final Logger log = LoggerFactory.getLogger(DevResetService.class);

    /**
     * Children first: nomination_audit_comms references nomination_audit_log,
     * and both nomination_audit_log and nomination_ai_flags reference
     * nominations. Deleting in any other order trips a foreign key.
     */
    private static final String[] TABLES_CHILDREN_FIRST = {
            "nomination_audit_comms",
            "nomination_audit_log",
            "nomination_ai_flags",
            "nominations",
    };

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TaggingService taggingService;
    private final DemoDataDateNormalizer dateNormalizer;
    private final String changeLogPath;

    public DevResetService(DataSource dataSource,
                           JdbcTemplate jdbc,
                           TaggingService taggingService,
                           DemoDataDateNormalizer dateNormalizer,
                           @Value("${spring.liquibase.change-log}") String changeLog) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.taggingService = taggingService;
        this.dateNormalizer = dateNormalizer;
        // Liquibase's own resource accessor is already classpath-relative, so the
        // "classpath:" prefix Spring wants has to come back off.
        this.changeLogPath = changeLog.replaceFirst("^classpath:/?", "");
    }

    /**
     * Wipes every nomination and replays the demo seed.
     *
     * @return the row counts after the reset, ready to hand back over HTTP
     */
    public Map<String, Object> reset() {
        long started = System.currentTimeMillis();

        clearData();
        replayDemoChangesets();

        // The replay re-inserts the seed's original, hardcoded absolute dates -
        // correct only in the quarter this was authored for. Rebase them onto
        // today's quarter before anything reads them.
        dateNormalizer.normalize();

        // Changeset 006 deletes the handwritten seed flags on purpose - they had
        // no reason text - so the rule flags have to be recomputed or the
        // coordinator's queue comes back with none. This is the same pass
        // TaggingStartupRunner makes at boot.
        taggingService.retagAll();

        Map<String, Object> counts = counts();
        log.info("Demo data reset in {}ms: {}", System.currentTimeMillis() - started, counts);
        return counts;
    }

    /** Current row counts, for the UI's demo panel and for eyeballing a test run. */
    public Map<String, Object> counts() {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("nominations", count("nominations"));
        counts.put("pending", jdbc.queryForObject(
                "SELECT COUNT(*) FROM nominations WHERE status = 'PENDING_REVIEW'", Long.class));
        counts.put("auditEntries", count("nomination_audit_log"));
        counts.put("flags", count("nomination_ai_flags"));
        return counts;
    }

    private Long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    /**
     * Empties the four tables. Each DELETE is its own transaction, which is fine
     * here - the only caller follows it immediately with a reseed, and a
     * half-cleared database is not a state anything observes.
     */
    private void clearData() {
        for (String table : TABLES_CHILDREN_FIRST) {
            jdbc.execute("DELETE FROM " + table);
        }
    }

    /**
     * Forgets that the demo changesets ever ran, then runs them again.
     *
     * <p>Liquibase takes its own connection and its own lock, so this
     * deliberately does not sit inside a transaction on the same pool.
     */
    private void replayDemoChangesets() {
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));

            // Ask Liquibase for the table name rather than writing
            // DATABASECHANGELOG here - on macOS MySQL, lower_case_table_names
            // decides whether that literal resolves, and it varies by install.
            String changeLogTable = database.getDatabaseChangeLogTableName();
            int forgotten = jdbc.update(
                    "DELETE FROM " + changeLogTable + " WHERE CONTEXTS LIKE '%demo%'");
            log.debug("Cleared {} demo changeset rows from {}", forgotten, changeLogTable);

            try (Liquibase liquibase =
                         new Liquibase(changeLogPath, new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts("demo"), new LabelExpression());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not replay the demo seed: " + e.getMessage(), e);
        }
    }
}
