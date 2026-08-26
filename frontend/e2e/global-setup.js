import { execSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

/* One reset before the run starts, on top of the per-test reset in
   fixtures/test.js. Cheap insurance: it also covers the case where the app was
   left running against a database somebody had been clicking around in.

   IMPORTANT, and the opposite of what this file used to claim: globalSetup runs
   *after* webServer, not before. Playwright builds its startup task list in
   runner/index.js createGlobalSetupTasks() as
   [removeOutputDirs, ...pluginSetup, ...globalTeardowns, ...globalSetups] -
   and the webServer plugin lives in pluginSetup. So by the time this function
   is called, Spring is already up and holding connections.

   That is why the primary path here is an HTTP call rather than Maven. The old
   implementation ran `liquibase:dropAll` from Maven at this point, which meant
   DDL dropping tables out from under a live HikariCP pool and an open Hibernate
   SessionFactory - it either blocked on a MySQL metadata lock or threw out of
   execSync and killed the run. The endpoint resets data through the app's own
   connection instead, and never touches the schema.

   The Maven path survives only as a fallback for when nothing is listening. */

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..", "..");
const BASE_URL = process.env.E2E_BASE_URL || "http://localhost:8080";

const env = {
  ...process.env,
  MYSQL_URL: process.env.MYSQL_URL
    || "jdbc:mysql://localhost:3306/recognitiondb?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
  MYSQL_USERNAME: process.env.MYSQL_USERNAME || "root",
  MYSQL_PASSWORD: process.env.MYSQL_PASSWORD || "141512",
};

export default async function globalSetup() {
  if (await resetViaRunningApp()) return;

  console.log("[global-setup] Nothing on " + BASE_URL + " - falling back to Liquibase via Maven.");
  execSync("./mvnw liquibase:dropAll -Dliquibase.force=true", { cwd: projectRoot, env, stdio: "inherit" });
  execSync("./mvnw liquibase:update -Dliquibase.contexts=demo", { cwd: projectRoot, env, stdio: "inherit" });
  console.log("[global-setup] Reset complete. webServer will start the app next, and "
    + "TaggingStartupRunner recomputes the rule flags on that boot.");
}

/* Returns false rather than throwing when the app is unreachable, so the caller
   can fall back. A reachable app that refuses the reset is a real problem
   though - a 404 means app.dev-tools.enabled is off - so that one is fatal. */
async function resetViaRunningApp() {
  let response;
  try {
    response = await fetch(`${BASE_URL}/api/dev/reset`, {
      method: "POST",
      signal: AbortSignal.timeout(30_000),
    });
  } catch {
    return false;
  }

  if (response.status === 404) {
    throw new Error(
      "POST /api/dev/reset returned 404. Set app.dev-tools.enabled=true in "
      + "src/main/resources/application.properties - without it the dev endpoints "
      + "are not registered and no test can reset the database.",
    );
  }
  if (!response.ok) {
    throw new Error(`[global-setup] Reset failed with ${response.status}.`);
  }

  const counts = await response.json();
  console.log(`[global-setup] Database reset: ${counts.nominations} nominations, `
    + `${counts.pending} pending (${counts.quarter}).`);
  return true;
}
