# Spotlight — Testing Guide

Everything about how this project is tested: what technologies are used,
where each layer of tests lives, how to run them, and the traps that were
actually hit while building this out — so the next person doesn't fall into
them again.

Read this top to bottom once, then use it as a lookup table. It pairs with
[`technical-guide.md`](./technical-guide.md), which covers the application
itself, and [`test-scenarios.md`](./test-scenarios.md), which catalogs every
scenario and persona this suite actually verifies.

---

## Contents

1. [The one-paragraph version](#1-the-one-paragraph-version)
2. [Technologies used, and why](#2-technologies-used-and-why)
3. [Where everything lives](#3-where-everything-lives)
4. [Backend tests, layer by layer](#4-backend-tests-layer-by-layer)
5. [Frontend tests, layer by layer](#5-frontend-tests-layer-by-layer)
6. [CI](#6-ci)
7. [Known limitations](#7-known-limitations)
8. [I want to add a new test](#8-i-want-to-add-a-new-test)
9. [Playground — resetting and inspecting](#9-playground--resetting-and-inspecting)

---

## 1. The one-paragraph version

The backend has three layers of JUnit tests — plain unit tests, HTTP-layer
slice tests, and one real-database integration test — plus the frontend has
three headless jsdom checks (pre-existing) and a Playwright suite split into
technical E2E specs and business-readable UAT specs (new). **The one thing to
know before running any of it**: everything except the pure backend unit
tests exercises a real, unmocked, stateful system — a live Spring Boot app, a
real MySQL database, or both, with no test doubles. That single fact explains
almost every non-obvious decision documented below.

State is handled by resetting rather than by mocking: every Playwright test
calls `POST /api/dev/reset` before it runs, which puts the database back to
its thirteen-nomination demo baseline in about 150ms. So any spec can be run
any number of times, in any order, including repeatedly from the Playwright
UI. See [§5's Automatic reset](#automatic-reset--the-per-test-database-reset)
and [§9 Playground](#9-playground--resetting-and-inspecting).

---

## 2. Technologies used, and why

| Technology | Where | What it does here | Where to look |
|---|---|---|---|
| **JUnit 5** | backend | The test runner and assertion framework for every Java test | `src/test/java/` |
| **Mockito** | backend unit tests | Fakes collaborators (`NominationRepository`, `NotificationService`, etc.) so a class is tested in isolation | `nomination/{check,comms,service,evaluation}/*Test.java` |
| **AssertJ** | backend tests | Fluent assertions (`assertThat(x).isEqualTo(y)`) — bundled with `spring-boot-starter-test`, no separate dependency needed | throughout `src/test/java/` |
| **Spring Test — `@WebMvcTest`** | backend controller tests | Boots only the web layer (one controller + `MockMvc`), with the service layer mocked out — no database | `nomination/web/*Test.java` |
| **Spring Test — `@SpringBootTest`** | backend integration test | Boots the full application context against a real database | `nomination/integration/NominationReviewWorkflowIntegrationTest.java` |
| **Liquibase (test profile)** | backend integration test | Builds a disposable schema (`recognitiondb_test`) from the same migrations the real app uses, minus the demo-seed data | `src/test/resources/application-test.properties` |
| **Playwright** | frontend E2E/UAT | Drives a real Chromium browser against the fully built app — clicks, fills forms, reads rendered text | `frontend/e2e/`, `frontend/playwright.config.js` |
| **jsdom** (pre-existing) | frontend smoke/behavior checks | Runs the built bundle headlessly (no real browser) against a live backend, for fast render/behavior/spacing checks | `frontend/scripts/` |
| **GitHub Actions** | CI | Runs the backend suite and the E2E/UAT suite automatically on push/PR | `.github/workflows/junit-tests.yml`, `.github/workflows/e2e-tests.yml` |

---

## 3. Where everything lives

```
src/test/java/com/version1/recognition/
  nomination/
    check/          6 unit tests, one per NominationCheck rule
    comms/          NotificationServiceTest.java (email templates)
    service/        NominationServiceTest.java, CompletenessServiceTest.java,
                     TaggingServiceTest.java
    evaluation/      MockNominationEvaluatorTest.java, EvaluatorSelectorTest.java
    web/            5 @WebMvcTest controller slice tests
    integration/    NominationReviewWorkflowIntegrationTest.java (real DB)
src/test/resources/
  application-test.properties   the "test" Spring profile

frontend/
  scripts/          smoke.mjs, assert.mjs, spacecheck.mjs   (pre-existing, jsdom)
  playwright.config.js
  scripts/reset-db.mjs   `npm run db:reset` - manual database reset
  e2e/
    global-setup.js             one reset before the run (see §5)
    fixtures/test.js            the `test` every spec imports: resets per test
    fixtures/personas.js        shared persona-seeding helper
    *.spec.js                   technical E2E specs
    uat/*.spec.js                business-readable acceptance specs

src/main/java/com/version1/recognition/
  dev/                DevController + DevResetService - the /api/dev endpoints
                        the reset is built on. Gated on app.dev-tools.enabled.

.github/workflows/
  junit-tests.yml     backend suite, needs only a MySQL service container
  e2e-tests.yml       frontend suite, needs Node + a built frontend + a running app
```

---

## 4. Backend tests, layer by layer

### Unit tests — `check/`, `comms/`, `service/`, `evaluation/`

The bulk of backend coverage. No Spring context, no database — just
`@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks` for any
collaborators, and AssertJ assertions. Fast (milliseconds) and the template
to copy for anything new. See `check/SelfNominationCheckTest.java` for the
simplest example (a plain POJO, `new XCheck().evaluate(...)`, no mocks
needed at all) or `service/TaggingServiceTest.java` for one that does use
Mockito.

### Controller slice tests — `web/`

`@WebMvcTest(SomeController.class)` boots only the HTTP layer — routing,
JSON serialization, validation, and `GlobalExceptionHandler` (it's picked up
automatically, no `@Import` needed) — with the service layer replaced by a
`@MockBean`. No database involved. Use `MockMvc` to perform requests and
`jsonPath(...)` to assert on the response body. See
`web/NominationControllerTest.java` for the full pattern (status codes,
validation errors, and the 409 mappings for
`QuarterLimitReachedException`/`InvalidReviewStateException`).

### The one real-DB integration test — `integration/`

`NominationReviewWorkflowIntegrationTest.java` is deliberately the *only*
test that talks to a real database. It's `@SpringBootTest` +
`@ActiveProfiles("test")` + `@Transactional` — every test method's writes
roll back automatically when the method ends, so tests are order-independent
and never leave residue.

**The test profile — `application-test.properties`:**

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/recognitiondb_test?createDatabaseIfNotExist=true&...
spring.liquibase.contexts=!demo
ai.evaluator=mock
```

- It points at a **separate schema**, `recognitiondb_test`, never the real
  `recognitiondb` dev database — tests can never corrupt real/demo data.
- `createDatabaseIfNotExist=true` means the schema self-creates on first
  connection — no manual `CREATE DATABASE` step, same mechanism the main app
  already relies on.
- `ai.evaluator=mock` guarantees no live Groq API call happens during a test
  run, even if a real key is present in the environment.

**⚠️ A trap that was actually hit building this:** `db.changelog-master.xml`
has a comment saying *"Drop `spring.liquibase.contexts` to get schema
without seed rows."* **This is wrong for how Spring Boot's Liquibase
auto-config actually behaves.** Leaving the property unset does **not**
skip `context="demo"` changesets — it runs *every* changeset, seed data
included, because "no contexts configured" means "no filter," not "match
nothing." The first version of this file omitted the property entirely and
silently seeded `recognitiondb_test` with all 13 demo rows on the very
first test run.

The fix, and what's actually configured, is the explicit **exclusion
syntax**:

```properties
spring.liquibase.contexts=!demo
```

If you ever see `recognitiondb_test` come back non-empty after a fresh
Liquibase run, check this line first before assuming the test code is
wrong.

**Running it:** needs a real MySQL reachable at `localhost:3306` (or
`MYSQL_USERNAME`/`MYSQL_PASSWORD` env vars pointing elsewhere). Nothing else
to set up — the schema and tables build themselves on first run.

### Running the backend suite

```bash
mvn test                                          # everything
mvn test -Dtest='com.version1.recognition.nomination.check.*Test'   # one package
mvn test -Dtest='!com.version1.recognition.nomination.integration.**'  # skip the DB test
```

Unit and controller tests need nothing extra. The integration test needs a
reachable MySQL — without one, only that one test class fails; everything
else still runs and passes.

---

## 5. Frontend tests, layer by layer

### The pre-existing jsdom scripts — `frontend/scripts/`

Not part of this session's work, but worth knowing about since they overlap
in purpose with the new Playwright suite:

| Script | npm command | What it checks |
|---|---|---|
| `smoke.mjs` | `npm run smoke` | Every route × every persona (60 combinations) renders without a JS error |
| `assert.mjs` | `npm run check` | 26 concrete behavioral assertions — role gating, hidden fields per role, quarter-limit enforcement, deep links |
| `spacecheck.mjs` | `npm run spacing` | Visual/text-spacing regression — catches text that visually runs together |

All three load the **built bundle** into `jsdom` (not a real browser) and
point `fetch` at a **live backend on `:8080`**. Run `npm run build` first,
and have the app running, before using any of them.

### Playwright E2E — `frontend/e2e/*.spec.js`

Real-browser tests (Chromium via Playwright) that click, fill forms, and
navigate exactly like a user would. Configured in `playwright.config.js`:

- **Target**: `http://localhost:8080` — the fully built app, not the Vite
  dev server (`:5173`). This matches how the app actually runs in
  production (Spring serves both the API and the built frontend from one
  process) and matches what the jsdom scripts above already assume.
- **`webServer`**: Playwright can auto-start `mvn spring-boot:run` itself
  and wait for it to be ready (`reuseExistingServer: !process.env.CI`, so
  it reuses an already-running app locally rather than starting a second
  one).
- **`workers: 1`** — deliberately **not** parallel. See the next section
  for why.

### Playwright UAT — `frontend/e2e/uat/*.spec.js`

Same Playwright tooling, same config, same `webServer` — just organized and
named around business-readable acceptance criteria (`test.describe("As a
coordinator, I can decide on a pending nomination", ...)`) instead of
technical mechanics. Deliberately not a second framework (no Cucumber/Gherkin)
— one tool, two ways of framing the same underlying tests.

### The persona-seeding convention — `e2e/fixtures/personas.js`

Reuses the exact mechanism the jsdom scripts already use — setting
`localStorage["v1r.persona"]` before the app boots — instead of inventing a
second login flow:

```js
import { seedPersona, PERSONAS } from "./fixtures/personas.js";

await seedPersona(page, PERSONAS.colette);   // sets localStorage before page.goto()
await page.goto("/#/queue");
```

### ⚠️ Why `workers: 1` — a real race condition was hit here

Early versions of this suite ran with `fullyParallel: true`. Two different
spec files (`coordinator-review.spec.js` and
`uat/coordinator-approves-nomination.spec.js`) both grabbed "the first
pending nomination in the queue" and tried to approve it — running
concurrently, both hit the API at nearly the same moment, and the second
one got a `409 Invalid Review State` because the first had already decided
it. **Every spec here shares one real, live backend with no per-test
isolation or mocking** — there is no way to give two Playwright tests their
own private copies of the data the way a mocked test could. `workers: 1`
makes the whole suite run one test at a time, trading parallel speed for
correctness — the right tradeoff for a suite this size against real,
shared state.

### Automatic reset — the per-test database reset

Every test resets `recognitiondb` to its 13-nomination demo-seeded baseline
before it runs. That happens in `e2e/fixtures/test.js`, which every spec
imports its `test` from instead of `@playwright/test`:

```js
import { test, expect } from "./fixtures/test.js";   // not "@playwright/test"
```

It is an `auto` fixture, so specs don't opt in and a new spec is covered by
default. All it does is `POST /api/dev/reset` — about 150ms.

**Why per test, and not once per run.** There was a `globalSetup` doing this,
and it was not enough for two reasons.

*Reason one: `globalSetup` fires once per `playwright test` **process**.*
Re-running a single test from the Playwright UI — the obvious thing to do
while demonstrating something — reuses whatever the previous attempt left
behind. Since a nominator gets one nomination per quarter and a nomination
can only be decided once, `employee-submit.spec.js` and both coordinator
specs could only pass on the first attempt. It also made `retries` actively
harmful: a retry re-ran against the wreckage of the failed attempt, turning
a flake into a guaranteed red.

*Reason two: ⚠️ `globalSetup` runs **after** `webServer`, not before.* The
config comment here used to claim the opposite, and so did this document.
Playwright builds its startup task list in `runner/index.js`'s
`createGlobalSetupTasks()` as
`[removeOutputDirs, ...pluginSetup, ...globalTeardowns, ...globalSetups]` —
and the `webServer` plugin lives in `pluginSetup`. So the old
`./mvnw liquibase:dropAll` ran **while Spring was already up**, dropping
tables out from under a live HikariCP pool and an open Hibernate
`SessionFactory`. That either blocks on a MySQL metadata lock or throws out
of `execSync` and kills the run. If you are debugging something in this area,
check the ordering in the installed runner rather than trusting a comment —
that inverted comment is what hid this for so long.

**What the endpoint does instead** (`src/main/java/com/version1/recognition/dev/`):
it never touches the schema. It deletes the rows from the four tables, deletes
the `context="demo"` rows from Liquibase's own changelog table, and asks
Liquibase to run again — which replays exactly the demo changesets, in
changelog order, skipping every schema changeset already applied. So the
baseline still comes from the migrations rather than from a second copy of
the dataset that would drift, but it goes through the app's own connection.

**One gotcha worth keeping**: the rule-based flags (`ROUTINE_TASK_LANGUAGE`,
`WEAK_JUSTIFICATION`, and the rest) are deleted on purpose by changeset 006,
and are normally only recomputed when the app boots — `TaggingStartupRunner`
runs once at startup, not per request. So the reset finishes by calling
`TaggingService.retagAll()` itself. Without that, a freshly reseeded queue
comes back with zero flags.

`e2e/global-setup.js` still runs one reset before the suite, as belt and
braces for the case where the app was left running against a database
somebody had been clicking around in. It now calls the same endpoint, and
only falls back to the Maven `dropAll`/`update` cycle when nothing is
listening on `:8080`.

Verified by running the full suite twice back to back, and with
`--repeat-each=3` (18 tests in one process, one `globalSetup`) — all green.

### Running the E2E/UAT suite

```bash
cd frontend
npm run e2e     # builds, then runs everything under e2e/ except uat/
npm run uat     # builds, then runs only e2e/uat/
```

Both need a real, reachable MySQL and the app able to start (same
requirement as the integration test above) — `webServer` in
`playwright.config.js` starts the app automatically if one isn't already
running.

### Watching it run — headed mode, UI mode, debugging

By default Playwright runs headless (no visible browser window). Since our
npm scripts wrap `playwright test`, pass extra flags after `--`, or call
`playwright test` directly:

```bash
cd frontend

# See the real browser window while a spec runs
npx playwright test "e2e/[^/]+\.spec\.js$" --headed
npx playwright test e2e/uat --headed

# Interactive UI mode - watch each step live, time-travel through actions,
# pick which test to run. The best option for exploring/debugging.
npx playwright test --ui

# Step-through debugger - pauses at each action, opens the Playwright Inspector
npx playwright test e2e/employee-submit.spec.js --headed --debug

# Review screenshots/traces from the LAST run, headless or not, without
# re-running anything
npx playwright show-report
```

**Needs a real display.** `--headed` and `--ui` only work from a local
terminal with an actual screen attached — they do nothing useful (or
outright fail) from a headless CI runner or a display-less remote/sandboxed
session. `--debug` and `show-report` have the same requirement.

---

## 6. CI

Two separate workflows, not one, because they're materially different
pipelines:

| Workflow | Triggers | What it does |
|---|---|---|
| `.github/workflows/junit-tests.yml` | push/PR to `main`, manual | Spins up a disposable MySQL service container (`recognitiondb_test`), runs `./mvnw test` |
| `.github/workflows/e2e-tests.yml` | push/PR to `main`, manual | Spins up a disposable MySQL service container (`recognitiondb`, demo-seeded), builds the frontend, installs Playwright's Chromium, starts the Spring app, runs the E2E then UAT specs |

Both use GitHub Actions' `services:` block for MySQL — a fresh, disposable
database per CI run, no Testcontainers, no Docker-in-Docker complexity.

**⚠️ `reuseExistingServer` must stay `true`.** It used to be
`!process.env.CI`, which meant `false` in CI — but `e2e-tests.yml` starts
Spring itself (so it can sequence the MySQL service container's health check
first) and waits for `:8080` before invoking Playwright. Playwright then found
the port bound and threw
`http://localhost:8080 is already used, make sure that nothing is running on
the port/url` out of `WebServerPlugin`, before a single test ran. The two
settings were mutually exclusive as written. If you ever need Playwright to
own the app lifecycle in CI, remove the workflow's own start step at the same
time.

---

## 7. Known limitations

- ~~No reset between runs.~~ Closed: every Playwright test resets
  `recognitiondb` to a fresh baseline first — see §5's "Automatic reset".
  The E2E/UAT suite can be run any number of times, in any order, including
  repeatedly from the Playwright UI. This only applies to the Playwright
  suite; the backend integration test (`recognitiondb_test`) already had its
  own isolation via `@Transactional` rollback.
- **The reset endpoint must not ship.** `app.dev-tools.enabled=true` in
  `application.properties` is what registers `/api/dev`. There is no
  authentication anywhere in this application, so an exposed reset is an
  unauthenticated wipe of every nomination. Delete the property before a real
  deploy; the beans are then never created and the routes 404.
- ~~The demo seed's dates are absolute, and expire on 1 October 2026.~~
  Closed: `DemoDataDateNormalizer`
  (`nomination/service/DemoDataDateNormalizer.java`) runs after every seed
  load — on boot via `TaggingStartupRunner`, and again after
  `POST /api/dev/reset` replays it — and rebases each demo row onto the
  equivalent quarter today, keeping its offset from that quarter's start.
  Ten rows authored for "the quarter this was written in" always land in the
  real current quarter; three authored for "the quarter before that" always
  land one quarter behind it. Today (still Q3 2026) every shift is zero, so
  this is invisible; the actual quarter-math is proven by
  `DemoDataDateNormalizerTest`, which simulates Q4 2026 without touching the
  system clock.
- **The integration test and the whole E2E/UAT suite need a real, reachable
  MySQL.** Neither can run at all without one — there's no in-memory or
  Testcontainers fallback (a deliberate choice, matching this project's
  "MySQL is the real store" philosophy). `global-setup.js`'s reset also
  requires MySQL to be reachable before it can do anything.
- **Resetting `recognitiondb_test` locally** (the backend integration
  test's schema — unaffected by `global-setup.js`, which only touches
  `recognitiondb`): stop the running app, `DROP DATABASE
  recognitiondb_test;`, re-run the integration test — Liquibase rebuilds it
  automatically on next connection.

---

## 8. I want to add a new test

| I want to… | Do this |
|---|---|
| Test a new `NominationCheck` rule | New file in `check/`, follow `SelfNominationCheckTest.java` — plain Mockito-free unit test |
| Test a new service method | New/extended file in `service/`, follow `TaggingServiceTest.java` for the Mockito pattern |
| Test a new controller endpoint | New/extended file in `web/`, follow `NominationControllerTest.java` (`@WebMvcTest` + `@MockBean`) |
| Test a new end-to-end workflow against the real DB | Add a method to `integration/NominationReviewWorkflowIntegrationTest.java` rather than a new class — keep this layer to one file |
| Add a new technical E2E scenario | New file in `frontend/e2e/`, import `test`/`expect` from `./fixtures/test.js` (**not** `@playwright/test`, or it won't reset), and `fixtures/personas.js` for the persona |
| Add a new UAT acceptance scenario | New file in `frontend/e2e/uat/`, same tooling, name it after the user story |
| A test needs "an employee with a free quarter slot" | Any of `sarah`, `ravi`, `michael`, `grace`, `aisling` — the reset gives every slot back before each test, so there is nothing to ration and no need to coordinate between specs |
| The database got left in a weird state | `npm run db:reset` (app running), or the button in the profile menu. Only if the *schema* is broken: stop the app, `DROP DATABASE recognitiondb;`, restart — Liquibase rebuilds it |

---

## 9. Playground — resetting and inspecting

The one rule that makes this app awkward to demonstrate: **a nominator gets
one nomination per calendar quarter**, and there is no way to delete a
nomination — correctly, since a recognition scheme should not have one. So
without help you can show the submission form working exactly once. These are
the ways out.

### Reset

Three doors to the same endpoint. All need the app running — it is the app
that does the work.

| | |
|---|---|
| **Profile menu → Reset demo data** | Bottom-left of the sidebar, under "Demo controls". Refetches the screen too, so the change is visible immediately |
| `cd frontend && npm run db:reset` | Prints the row counts afterwards |
| `curl -X POST localhost:8080/api/dev/reset` | The raw thing; returns the counts as JSON |

All three return to the same baseline: **13 nominations, 8 awaiting review, 5
audit entries**. It takes about 150ms.

### Never run out of employees

`Profile menu → + New employee` invents an identity nobody has seen —
`Test Employee 4821` or similar — so that quarter's nomination is necessarily
unspent. Pick it again whenever you need another free slot; there is no limit.

This works because there is **no employee or user table**. A nominator is only
a name and an email on the nomination row (`Nomination.nominatorEmail`), and
nothing validates it against a list, so a new address is a new person as far
as the one-per-quarter rule is concerned. `NominationRequest` says as much in
its own comment: *"In the real system this is captured from the logged-in
user."*

Of the named profiles, `sarah`, `ravi`, `michael`, `grace` and `aisling` start
each quarter with a free slot; `calvin` and `jamie` have already spent theirs
in the seed, which is how the "you've nominated" panel is demonstrated.

### Watching the database change

MySQL Workbench is already installed — connect to `localhost:3306`, schema
`recognitiondb`. Note that on this machine the `mysql` CLI is **not** on
`PATH`; it lives at `/usr/local/mysql/bin/mysql`, so either use the full path
or `export PATH="/usr/local/mysql/bin:$PATH"`.

There are only four tables:

```sql
-- The headline numbers, same ones GET /api/dev/status returns
SELECT status, COUNT(*) FROM nominations GROUP BY status;

-- Most recent first: did the test you just ran land?
SELECT nominator_email, nominee_email, status, category, ai_score, submitted_at
FROM nominations ORDER BY submitted_at DESC LIMIT 10;

-- Who decided what, and what was sent
SELECT a.occurred_at, a.coordinator_email, a.action, n.nominee_name, a.reason
FROM nomination_audit_log a JOIN nominations n ON n.id = a.nomination_id
ORDER BY a.occurred_at DESC;

-- Which nominations tripped which rules
SELECT n.nominee_name, f.flag, f.source, f.reason
FROM nomination_ai_flags f JOIN nominations n ON n.id = f.nomination_id
ORDER BY n.nominee_name;
```

`GET /api/dev/status` gives the same counts without leaving the browser, and
the "Reset demo data" row in the profile menu shows the current nomination
count as its subtitle.

### ⚠️ Why `recognitiondb_test` looks empty

It is a **different schema** from `recognitiondb`, used only by
`NominationReviewWorkflowIntegrationTest`, and that test is `@Transactional` —
so every row it writes is rolled back when it finishes. There is deliberately
nothing to see afterwards. It is also seeded with `contexts=!demo`, so it has
no demo rows either, and it does not exist at all until `./mvnw test` has been
run once (`createDatabaseIfNotExist=true` creates it on first connection).

None of that is broken. If you want to watch a workflow land in a database you
can browse, use the Playwright suite or the UI against `recognitiondb`
instead.
