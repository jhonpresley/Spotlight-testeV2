import { test, expect } from "./fixtures/test.js";
import { seedPersona, PERSONAS } from "./fixtures/personas.js";

/* The other side of the one-nomination-per-quarter rule: the demo seed gives
   Calvin a nomination in the current quarter, so he gets the "you've
   nominated" panel rather than the form. The matching "a free slot shows the
   real form" case is employee-submit.spec.js. */
test("Calvin, who has already submitted, is blocked from the form", async ({ page }) => {
  await seedPersona(page, PERSONAS.calvin);
  await page.goto("/#/submit");

  await expect(page.getByRole("heading", { name: /you've nominated/i })).toBeVisible();
  await expect(page.locator("textarea#whatText")).toHaveCount(0);
});
