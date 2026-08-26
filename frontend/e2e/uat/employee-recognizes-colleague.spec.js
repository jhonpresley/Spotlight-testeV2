import { test, expect } from "../fixtures/test.js";
import { seedPersona, PERSONAS } from "../fixtures/personas.js";

/* UAT: business-readable acceptance criteria, same Playwright tooling as
   e2e/, organized by user story rather than technical mechanics.

   This one covers the limit itself - what an employee sees once they have
   spent their quarter's nomination. The submission journey it pairs with is
   ../employee-submit.spec.js. */
test.describe("As an employee, I get one nomination per quarter", () => {
  test("Given I have already submitted this quarter, when I visit the submission page, "
    + "then I cannot submit again and I'm told when I can next", async ({ page }) => {
    await seedPersona(page, PERSONAS.calvin);
    await page.goto("/#/submit");

    await expect(page.getByRole("heading", { name: /you've nominated/i })).toBeVisible();
    await expect(page.locator("textarea#whatText")).toHaveCount(0);
    await expect(page.getByText(/next nomination opens/i)).toBeVisible();
  });
});
