/* Puts the database back to its thirteen-nomination demo baseline, for when
   you have been clicking around and want a clean slate without restarting
   anything. The Playwright suite does this before every test on its own; this
   is the manual equivalent.

   Needs the app running - it is the app that does the work. */

const BASE_URL = process.env.E2E_BASE_URL || "http://localhost:8080";

let response;
try {
  response = await fetch(`${BASE_URL}/api/dev/reset`, { method: "POST" });
} catch {
  console.error(`Could not reach ${BASE_URL}. Start the app first:\n`
    + "  ./mvnw spring-boot:run");
  process.exit(1);
}

if (response.status === 404) {
  console.error("POST /api/dev/reset returned 404.\n"
    + "Set app.dev-tools.enabled=true in src/main/resources/application.properties.");
  process.exit(1);
}
if (!response.ok) {
  console.error(`Reset failed with ${response.status}.`);
  process.exit(1);
}

const { quarter, nominations, pending, auditEntries, flags } = await response.json();
console.log(`Reset to the demo baseline (${quarter}):`);
console.log(`  nominations   ${nominations}  (${pending} awaiting review)`);
console.log(`  audit entries ${auditEntries}`);
console.log(`  rule flags    ${flags}`);
