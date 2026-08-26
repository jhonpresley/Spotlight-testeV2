package com.version1.recognition.dev;

import com.version1.recognition.nomination.model.Quarter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo controls: wipe the database back to its seeded baseline, and report what
 * is in it.
 *
 * <p>This exists because the platform has no way to delete a nomination - by
 * design, a recognition scheme should not - and one rule in particular makes
 * that awkward to demonstrate: a nominator gets one nomination per quarter, so
 * the submission form can only be shown working once before the database has
 * to be put back. The Playwright suite calls {@code POST /api/dev/reset}
 * before every test, which is what makes any spec re-runnable, including from
 * the Playwright UI.
 *
 * <p><b>Not a production endpoint.</b> There is no authentication anywhere in
 * this application, so an exposed reset is an unauthenticated wipe of every
 * nomination. It is guarded by {@code app.dev-tools.enabled}: when that is
 * absent or false this bean is never created and both routes 404. Delete the
 * property from application.properties before any real deploy.
 */
@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevController {

    private final DevResetService resetService;

    public DevController(DevResetService resetService) {
        this.resetService = resetService;
    }

    /**
     * What is in the database right now, and confirmation that these routes are
     * live. The front end probes this at boot to decide whether to offer the
     * demo panel, so a real deploy shows no controls rather than broken ones.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", true);
        body.put("quarter", Quarter.current().code());
        body.putAll(resetService.counts());
        return ResponseEntity.ok(body);
    }

    /**
     * Back to the seeded baseline: thirteen nominations, eight of them pending.
     *
     * @return the row counts afterwards, so a caller can print them without a
     *         second request
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reset", true);
        body.put("quarter", Quarter.current().code());
        body.putAll(resetService.reset());
        return ResponseEntity.ok(body);
    }
}
