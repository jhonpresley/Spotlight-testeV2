import { test, expect } from "./fixtures/test.js";
import { seedPersona, PERSONAS } from "./fixtures/personas.js";

/* Approving a nomination end to end, from the queue through to the activity
   log. */
test("a coordinator can open and approve a pending nomination from the queue", async ({ page }) => {
  await seedPersona(page, PERSONAS.colette);
  await page.goto("/#/queue");

  // The queue opens on every nomination, decided ones included, and only a
  // PENDING_REVIEW one offers an Approve button. Narrow to awaiting-review
  // first rather than trusting that the newest row happens to be undecided.
  await page.getByRole("button", { name: /Awaiting review/ }).click();

  const firstPending = page.locator("table tbody tr.clickable").first();
  await expect(firstPending).toBeVisible();

  await firstPending.click();
  await page.getByRole("button", { name: "✓ Approve" }).click();
  await page.getByRole("button", { name: "Confirm approval" }).click();

  await expect(page.getByText(/^Approved —/)).toBeVisible();

  await page.goto("/#/activity");
  await expect(page.getByText(/Approved/i).first()).toBeVisible();
});
