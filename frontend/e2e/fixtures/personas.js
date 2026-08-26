/* Reuses the exact persona-seeding convention the existing jsdom scripts
   (scripts/smoke.mjs, scripts/assert.mjs) already use - localStorage["v1r.persona"]
   set before the app boots - instead of inventing a second login mechanism. */

export async function seedPersona(page, personaId) {
  await page.addInitScript((id) => {
    window.localStorage.setItem("v1r.persona", id);
  }, personaId);
}

/* From frontend/src/constants.js - kept here rather than imported so e2e specs
   have no build-time dependency on the app's source tree. Keep the two in step:
   a rename over there fails here silently, as a persona that falls back to the
   first in the list rather than an error. */
export const PERSONAS = {
  // Have already spent this quarter's nomination in the demo seed.
  calvin: "calvin",
  jamie: "jamie",
  // Free slot - the submission form renders for these. The per-test reset in
  // ./test.js hands the slot back afterwards, so any of them can be used
  // repeatedly and by more than one spec.
  sarah: "sarah",
  ravi: "ravi",
  michael: "michael",
  grace: "grace",
  aisling: "aisling",
  // Coordinators.
  colette: "colette",
  dara: "dara",
};
