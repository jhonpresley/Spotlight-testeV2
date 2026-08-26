import { test, expect } from "./fixtures/test.js";
import { seedPersona, PERSONAS } from "./fixtures/personas.js";

/* The whole employee journey in the order a real user hits it: the form
   renders, self-nomination is refused, a real nomination goes in, and it comes
   back under "My Recognition".

   Sarah has no nomination in the current quarter in the demo seed, so the form
   renders for her. Submitting spends that slot - but the per-test reset in
   fixtures/test.js hands it back before the next test, so this runs as many
   times as you like, including repeatedly from the Playwright UI. */
test("an employee cannot nominate themself, and can submit a real nomination", async ({ page }) => {
  await seedPersona(page, PERSONAS.sarah);
  await page.goto("/#/submit");

  await expect(page.locator("textarea#whatText")).toBeVisible();

  // Self-nomination is rejected before the quarter-limit check runs
  // server-side, so this attempt does not consume Sarah's slot.
  await page.getByRole("button", { name: "Try self-nomination" }).click();
  await page.getByRole("button", { name: "Submit Star Award" }).click();
  await expect(page.locator(".banner")).toContainText(/can't nominate yourself/i);

  // The form's own "Fill sample" button produces a valid, deterministic
  // submission (nominee "Alex Rivera") - reusing it avoids duplicating the
  // app's own sample data here and keeps the test resilient to field changes.
  await page.getByRole("button", { name: "Fill sample" }).click();
  await page.getByRole("button", { name: "Submit Star Award" }).click();

  await expect(page.getByRole("heading", { name: /you've nominated/i })).toBeVisible();
  // "Alex Rivera" also appears in the success toast, so this matches on the
  // first occurrence (the nominee name in the "you've nominated" card) rather
  // than requiring exactly one match on the page.
  await expect(page.getByText("Alex Rivera").first()).toBeVisible();

  // Employees are deliberately not shown a live status column here (they're
  // told the outcome by email, not by watching a queue) - so this only
  // confirms the submission is recorded under "Submitted by you", not any
  // particular status text.
  await page.goto("/#/mine");
  await expect(page.getByRole("heading", { name: "Submitted by you" })).toBeVisible();
  await expect(page.getByText("Alex Rivera").first()).toBeVisible();
});
