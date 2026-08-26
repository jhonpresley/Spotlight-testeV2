# Spotlight — Test Scenarios and Personas

Every scenario the test suite actually verifies, and every persona used to
verify it, in one place. [`testing-guide.md`](./testing-guide.md) explains
*how* the suite is built and run; this document is the catalog of *what* it
covers — read it when you want to know "is X already tested?" without
opening a dozen files.

---

## Contents

1. [Personas used in testing](#1-personas-used-in-testing)
2. [Backend scenario catalog](#2-backend-scenario-catalog)
3. [Frontend E2E scenario catalog](#3-frontend-e2e-scenario-catalog)
4. [Frontend UAT scenario catalog](#4-frontend-uat-scenario-catalog)
5. [Coverage map](#5-coverage-map)

---

## 1. Personas used in testing

Two separate persona systems are in play — don't conflate them.

**Backend tests** use synthetic fixture data built inline in each test
(`"Calvin Ho"` / `"Alex Rivera"`, `integration.*@company.com` for the
integration test) — not the named frontend demo personas below.

**Frontend E2E/UAT tests** seed one of the nine demo personas from
`frontend/src/constants.js` via `localStorage["v1r.persona"]`
(`e2e/fixtures/personas.js`):

| Persona | Role | Key trait for testing | Used in |
|---|---|---|---|
| **Calvin Ho** | Employee | Has already submitted this quarter — the standard "blocked" test subject | `quarter-limit.spec.js`, `uat/employee-recognizes-colleague.spec.js` |
| **Jamie Doyle** | Employee | Also already submitted this quarter; not used by name in the current suite | — |
| **Sarah Murphy** | Employee | Free nomination slot | `employee-submit.spec.js` |
| **Ravi Patel** | Employee | Free nomination slot | — |
| **Michael Chen** | Employee | Free nomination slot | — |
| **Grace O'Sullivan** | Employee | Free nomination slot | — |
| **Aisling Kelly** | Employee | Free nomination slot — her seeded nomination is in the *previous* quarter | — |
| **Colette Lynch** | Coordinator | Used for every review-queue/decision scenario | `coordinator-review.spec.js`, `role-gating.spec.js`, `uat/coordinator-approves-nomination.spec.js` |
| **Dara Quinn** | Coordinator | A second reviewer, so the activity log can show two people deciding | — |

**Free slots are not rationed.** Every test resets the database to the demo
baseline before it runs, so any persona above marked "free nomination slot"
can be used by any number of specs, repeatedly. See
[`testing-guide.md` §5](./testing-guide.md#5-frontend-tests-layer-by-layer).

**A tenth, unnamed persona exists on demand.** *Profile menu → + New
employee* mints a throwaway identity (`Test Employee 4821`) whose quarter
nomination is necessarily unspent — useful when clicking around by hand
rather than in a spec. There is no employee table, so a new email address is
a new person as far as the one-per-quarter rule is concerned.

---

## 2. Backend scenario catalog

### `check/SelfNominationCheckTest`

| Scenario | Expected outcome |
|---|---|
| Nominator and nominee share an email address | Flagged — reason names the shared email |
| Nominator and nominee share a name on different emails | Flagged — reason names the shared name |
| Nominator and nominee are different people | Not flagged |
| — | `flag()` returns `AiFlag.SELF_NOMINATION` |

### `check/ReciprocalNominationCheckTest`

| Scenario | Expected outcome |
|---|---|
| The nominee also nominated the nominator, on record | Flagged — reason says "also nominated" |
| The comparison list contains only the nomination itself | Not flagged (self-record correctly excluded) |
| Two unsaved records (both ids null) compared against each other | Correctly told apart by object identity, not treated as "the same record" |
| No reciprocal nomination exists | Not flagged |
| — | `flag()` returns `AiFlag.RECIPROCAL_NOMINATION` |

### `check/RepeatNominationCheckTest`

| Scenario | Expected outcome |
|---|---|
| Same nominee was also nominated in the immediately preceding quarter | Flagged — reason names that quarter |
| Same nominee was nominated two quarters ago (not the immediately preceding one) | Not flagged |
| The nomination being checked has a null `submittedAt` | Short-circuits to not flagged |
| The nomination being checked has a null `nomineeEmail` | Short-circuits to not flagged |
| — | `flag()` returns `AiFlag.REPEAT_NOMINATION_CONSECUTIVE_QUARTER` |

### `check/RoutineLanguageCheckTest`

| Scenario | Expected outcome |
|---|---|
| Text contains a routine-duty phrase only | Flagged — reason mentions "routine duties" |
| Text contains a generic-praise phrase only | Flagged — reason mentions "generic praise" |
| Text contains both phrase families | Flagged — reason mentions both |
| Text contains neither | Not flagged |
| Phrase appears in a different case (`TEAM PLAYER`) | Still flagged — matching is case-insensitive |
| — | `flag()` returns `AiFlag.ROUTINE_TASK_LANGUAGE` |

### `check/WeakJustificationCheckTest`

| Scenario | Expected outcome |
|---|---|
| Exactly one of the three signals (short / no digit / no core value) fails | Not flagged |
| Exactly two of three signals fail | Flagged — "Thin on 2 of 3 signals" |
| All three signals fail | Flagged — "Thin on 3 of 3 signals" |
| — | `flag()` returns `AiFlag.WEAK_JUSTIFICATION` |

### `check/EmployeeStatusCheckTest`

| Scenario | Expected outcome |
|---|---|
| Any nomination (no HR data source is wired up) | Always passes — locks in the documented placeholder behavior |
| — | `flag()` returns `AiFlag.NOMINEE_NOT_ACTIVE_EMPLOYEE` |

### `comms/NotificationServiceTest`

| Scenario | Expected outcome |
|---|---|
| Approval comms are composed | Sent to the nominator; includes the WHAT/HOW write-up |
| Approval comms with/without a coordinator comment | Comment included only when non-blank |
| Nominee-award comms are composed | Sent to the nominee; quotes the nomination in full |
| Decline comms are composed | Sent **only** to the nominator, never the nominee; includes the rejection reason |
| Decline comms with/without a coordinator comment | Comment included only when non-blank |
| Resubmission-requested comms are composed | Sent to the nominator; quotes the original wording |
| Any comms message | Always references the nomination's id ("Reference: ...") |

### `service/CompletenessServiceTest`

| Scenario | Expected outcome |
|---|---|
| A well-formed nomination (detailed WHAT with impact, detailed HOW naming a value, category set, no routine language) | Every one of the 6 criteria passes; no resubmission message |
| A thin nomination (short text, no category, routine phrase) | All 6 criteria fail, in declaration order |
| A failing nomination | The generated resubmission message lists every failure with its remedy, numbered |

### `service/TaggingServiceTest`

| Scenario | Expected outcome |
|---|---|
| Two checks run, one raises a flag and one doesn't | Only the raised flag is collected |
| One check throws an exception | That check's flag is skipped; other checks' flags are still collected (no exception propagates) |
| `retagAll()` runs with an existing AI-sourced flag no rule re-raises | Rule flags replace old rule flags; the untouched AI flag is preserved |
| `retagAll()` runs when a rule now raises the same flag an AI evaluation once did | Only the rule-sourced version remains — the AI duplicate is dropped |

### `service/NominationServiceTest`

| Scenario | Expected outcome |
|---|---|
| A `NEEDS_RESUBMISSION` nomination is resubmitted once, this quarter | Allowed |
| That same original is resubmitted a second time | `QuarterLimitReachedException` |
| A pending nomination is approved | Status → `APPROVED`; comms sent to both nominator and nominee |
| A non-pending nomination is approved | `InvalidReviewStateException` |
| A pending nomination is rejected | Status → `REJECTED`; comms sent only to the nominator |
| A non-pending nomination is rejected | `InvalidReviewStateException` |
| A resubmission is requested on a pending nomination | Status → `NEEDS_RESUBMISSION`; reason recorded |

### `evaluation/MockNominationEvaluatorTest`

| Scenario | Expected outcome |
|---|---|
| — | `isAvailable()` always returns `true` |
| Text with no language concerns | Score 85, no flags |
| WHAT contains a routine phrase | `ROUTINE_TASK_LANGUAGE` flagged, score reduced |
| HOW contains a weak phrase | `WEAK_JUSTIFICATION` flagged, score reduced |
| HOW is under 8 words | `WEAK_JUSTIFICATION` flagged (word-count signal, no phrase needed) |
| Both routine and weak signals present | Both flags, score reduced further |

### `evaluation/EvaluatorSelectorTest`

| Scenario | Expected outcome |
|---|---|
| `auto` mode, Groq available | Groq is used |
| `auto` mode, Groq unavailable | Falls back to the mock evaluator |
| `mock` mode, Groq available | Mock is used anyway (forced) |
| `groq` mode, Groq unavailable | Groq is used anyway (forced), reports unavailable |
| Blank/null mode | Defaults to `auto` behavior |

### `web/NominationControllerTest` (`@WebMvcTest`)

| Scenario | Expected outcome |
|---|---|
| Valid submission | `201 Created`, body includes id and `PENDING_REVIEW` status |
| Submission missing required fields | `400`, field-level validation errors |
| Submission where nominator = nominee | `400`, self-nomination error message |
| Submission over the quarter limit | `409`, `reason: QUARTER_LIMIT` + quarter label |
| Get an existing nomination by id | `200`, full body |
| Get a non-existent id | `404` |
| List with `?status=` filter | Only matching nominations returned |
| Approve a pending nomination | `200`, status `APPROVED` |
| Approve a non-pending nomination | `409` |
| Reject without a reason | `400`, validation error on `reason` |
| Request resubmission on a pending nomination | `200`, status `NEEDS_RESUBMISSION` |

### `web/ActivityControllerTest` (`@WebMvcTest`)

| Scenario | Expected outcome |
|---|---|
| Audit entries exist with matching nominations | Each row includes the nominee name joined in |
| An audit entry's nomination no longer exists | Row shows `"(deleted nomination)"` instead of erroring |

### `web/CategoryControllerTest` / `web/CoreValueControllerTest` (`@WebMvcTest`)

| Scenario | Expected outcome |
|---|---|
| List categories | Every `AwardCategory` value returned with `value`/`label`/`examples` |
| List core values | Every `CoreValue` value returned with `value`/`label`/`prompt` |

### `web/QuarterControllerTest` (`@WebMvcTest`)

| Scenario | Expected outcome |
|---|---|
| `/current` with no `email` param | `hasSubmitted: false` |
| `/current` with an email that hasn't submitted | `hasSubmitted: false` |
| `/current` with an email that has an existing submission | `hasSubmitted: true`, submission details included |
| `/` (history) with no nominations on record | Still includes the current quarter, `totalNominations: 0` |
| `/now` | Returns a server timestamp |

### `integration/NominationReviewWorkflowIntegrationTest` (real MySQL, `@Transactional`)

| Scenario | Expected outcome |
|---|---|
| Submit a nomination | Persisted as `PENDING_REVIEW` with real (non-mocked) rule tagging |
| Approve a submitted nomination | Status → `APPROVED`; one audit log entry with **2** persisted comms rows (nominator + nominee) |
| Reject a submitted nomination | Status → `REJECTED`; one audit log entry with **1** persisted comm (nominator only) |
| Request resubmission, then resubmit once, then attempt a second resubmission on the same original | First resubmission succeeds (`PENDING_REVIEW`, new id); second attempt throws `QuarterLimitReachedException` |

---

## 3. Frontend E2E scenario catalog

| File | Scenario | Persona | Expected outcome |
|---|---|---|---|
| `role-gating.spec.js` | Employee navigates directly to `#/queue` | sarah | Redirected to `#/home` |
| `role-gating.spec.js` | Employee views the sidebar | sarah | No Review Queue link present |
| `role-gating.spec.js` | Coordinator clicks the Review Queue link | colette | Navigates to `#/queue`, "Review Queue" heading visible |
| `quarter-limit.spec.js` | Employee who already submitted visits `#/submit` | calvin | "You've nominated" panel shown, no form |
| `coordinator-review.spec.js` | Coordinator opens the first pending nomination and approves it | colette | Success toast starting "Approved —"; entry visible in Activity Log |
| `employee-submit.spec.js` | Employee attempts to nominate themself, then submits a real nomination | sarah | Self-nomination blocked with a banner error (slot not consumed); real submission then succeeds and appears under "Submitted by you" |

---

## 4. Frontend UAT scenario catalog

Same Playwright tooling as §3, framed as business-readable acceptance
criteria (`test.describe("As a <role>, I ...")`).

| File | Story | Scenario | Persona | Expected outcome |
|---|---|---|---|---|
| `uat/coordinator-approves-nomination.spec.js` | As a coordinator, I can decide on a pending nomination | Given a nomination is pending review, when I approve it | colette | It leaves the queue and the decision is recorded in the activity log |
| `uat/employee-recognizes-colleague.spec.js` | As an employee, I get one nomination per quarter | Given I have already submitted this quarter, when I visit the submission page | calvin | I cannot submit again and I'm told when I can next nominate |

---

## 5. Coverage map

Which layer(s) actually exercise each behavior — useful for spotting gaps
before assuming something is tested.

| Feature / behavior | Unit | Controller | Integration | E2E | UAT |
|---|:---:|:---:|:---:|:---:|:---:|
| Self-nomination guard | ✅ | ✅ (submit) | — | ✅ | — |
| Reciprocal nomination detection | ✅ | — | — | — | — |
| Repeat-nomination (consecutive quarter) detection | ✅ | — | — | — | — |
| Routine-language / weak-justification flagging (rules) | ✅ | — | — | — | — |
| Routine-language / weak-justification flagging (mock AI) | ✅ | — | — | — | — |
| AI evaluator selection (`auto`/`groq`/`mock`) | ✅ | — | — | — | — |
| Completeness checklist | ✅ | — | — | — | — |
| Comms templates (approve/reject/resubmit) | ✅ | — | ✅ (comm count only) | — | — |
| Tagging aggregation & retag merge logic | ✅ | — | ✅ (via submit) | — | — |
| Quarter-limit enforcement | ✅ | ✅ | ✅ | ✅ | ✅ |
| Review decision workflow (approve/reject/resubmit) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Role gating (employee vs. coordinator views) | — | — | — | ✅ | — |
| Activity log rendering (incl. deleted-nomination case) | — | ✅ | — | ✅ (via approve) | ✅ (via approve) |
| Reference data endpoints (categories, core values, quarters) | — | ✅ | — | — | — |

No layer here replaces another: unit tests prove the rule logic in
isolation and fast; controller tests prove the HTTP contract without a
database; the integration test proves the whole stack actually wires
together against real Liquibase-backed MySQL; E2E/UAT prove a real user can
actually do these things through the real UI.
