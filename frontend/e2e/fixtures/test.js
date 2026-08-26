import { test as base, expect } from "@playwright/test";

/* The `test` every spec imports instead of @playwright/test's.
   Identical, except that the database goes back to its thirteen-nomination
   demo baseline before each one runs.

   Why per test rather than once per run: globalSetup fires once per
   `playwright test` *process*, so re-running a single test from the Playwright
   UI - the obvious thing to do while demonstrating something - reused whatever
   the previous attempt left behind. Specs that submit a nomination or approve
   one could only pass on the first attempt, because a nominator gets one
   nomination per quarter and a nomination can only be decided once.

   It also makes `retries` honest: a retry now starts from the same state as
   the first attempt rather than from the wreckage of it.

   Costs about 150ms - the endpoint reuses the app's own connection rather
   than shelling out to Maven. See src/main/java/com/version1/recognition/dev/. */

export const test = base.extend({
  freshDatabase: [async ({ baseURL }, use) => {
    let response;
    try {
      response = await fetch(`${baseURL}/api/dev/reset`, { method: "POST" });
    } catch (cause) {
      throw new Error(
        `Could not reach ${baseURL} to reset the database. Is the app running?`,
        { cause },
      );
    }
    if (response.status === 404) {
      throw new Error(
        "POST /api/dev/reset returned 404. Set app.dev-tools.enabled=true in "
        + "src/main/resources/application.properties - without it the dev "
        + "endpoints are not registered.",
      );
    }
    if (!response.ok) {
      throw new Error(`Resetting the database failed with ${response.status}.`);
    }

    await use();
  }, { auto: true }],
});

export { expect };
