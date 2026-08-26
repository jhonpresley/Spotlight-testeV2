import { defineConfig, devices } from "@playwright/test";

/* Targets the same fully-built, single-origin app as the existing jsdom
   scripts (smoke.mjs / assert.mjs): `npm run build` writes into
   ../src/main/resources/static, and Spring Boot serves both the API and the
   built frontend from one process on :8080 - no separate frontend server in
   production, so E2E follows the same shape rather than testing against the
   Vite dev server's proxy layer. */
export default defineConfig({
  testDir: "./e2e",
  // Resets recognitiondb once before the run. Note this runs AFTER webServer
  // has started the app, not before - see the header of e2e/global-setup.js.
  // The reset that actually makes specs re-runnable is the per-test one in
  // e2e/fixtures/test.js; this is belt and braces.
  globalSetup: "./e2e/global-setup.js",
  // Every spec shares one real, non-mocked backend (the live app + MySQL) with
  // no per-test reset or isolation - two specs that both grab "the first
  // pending nomination" can race and one gets a 409. Running one test at a
  // time trades speed for determinism, the right call for a suite this size
  // against real, unmocked state.
  workers: 1,
  // Safe now that every test resets first: a retry starts from the same
  // baseline as the first attempt rather than from the wreckage of it.
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: "http://localhost:8080",
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "cd .. && ./mvnw spring-boot:run",
    url: "http://localhost:8080",
    // Reuse an app already running rather than starting a second one - locally
    // that's the one in your other terminal, and in CI it's the one
    // e2e-tests.yml starts itself so it can sequence the MySQL service
    // container's health check first.
    //
    // This is deliberately not `!process.env.CI`. With that, CI set it false
    // while the workflow had already bound :8080, and WebServerPlugin threw
    // "http://localhost:8080 is already used" before a single test ran.
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
