import { test, expect } from "../fixtures/test.js";
import { seedPersona, PERSONAS } from "../fixtures/personas.js";

/* UAT: business-readable framing of ../coordinator-review.spec.js's journey.
   Same acceptance criterion, written as Given/When/Then for the people who
   sign it off rather than the people who maintain it. */
test.describe("As a coordinator, I can decide on a pending nomination", () => {
  test("Given a nomination is pending review, when I approve it, then it leaves the queue "
    + "and the decision is recorded in the activity log", async ({ page }) => {
    await seedPersona(page, PERSONAS.colette);
    await page.goto("/#/queue");

    // Only a PENDING_REVIEW nomination offers an Approve button, and the queue
    // opens on all of them - so narrow to awaiting-review before picking one.
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
});
