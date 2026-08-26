import { test, expect } from "./fixtures/test.js";
import { seedPersona, PERSONAS } from "./fixtures/personas.js";

test.describe("role gating", () => {
  test("an employee visiting the Review Queue is redirected home", async ({ page }) => {
    await seedPersona(page, PERSONAS.sarah);
    await page.goto("/#/queue");

    await expect(page).toHaveURL(/#\/home$/);
  });

  test("an employee never sees the Review Queue link in the sidebar", async ({ page }) => {
    await seedPersona(page, PERSONAS.sarah);
    await page.goto("/#/home");

    await expect(page.locator('a[href="#/queue"]')).toHaveCount(0);
  });

  test("a coordinator sees the Review Queue link and can open it", async ({ page }) => {
    await seedPersona(page, PERSONAS.colette);
    await page.goto("/#/home");

    // The queue link appears three times on a coordinator's home (sidebar nav
    // plus two quick-action shortcuts) - the sidebar one has a distinct
    // accessible name ("☑ Review Queue") from the other two.
    await page.getByRole("link", { name: "☑ Review Queue" }).click();

    await expect(page).toHaveURL(/#\/queue$/);
    await expect(page.getByRole("heading", { name: /Review Queue/i })).toBeVisible();
  });
});
