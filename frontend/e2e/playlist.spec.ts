import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test("browser → Vite proxy → Spring Boot → mock MusicBrainz and Last.fm → five results", async ({
  page,
  request,
}) => {
  await page.goto("/");
  await page.getByRole("button", { name: "After-hours drive" }).click();
  await page.getByLabel("Excluded artists").fill("Drake");
  await page.getByLabel("Excluded genres").fill("country");
  await page.getByRole("button", { name: "Find songs" }).click();
  await expect(
    page.getByRole("link", { name: "Find on YouTube Music" }),
  ).toHaveCount(5);
  await expect(
    page.getByRole("heading", { name: "Money Trees" }),
  ).toBeVisible();
  await expect(page.locator(".song-list")).not.toContainText("Drake");
  await expect(page.locator(".song-reason").first()).toContainText("hip hop");
  const stats = (await (
    await request.get("http://127.0.0.1:9011/__test/stats")
  ).json()) as { catalog: number; enrichment: number };
  expect(stats.catalog).toBeGreaterThan(0);
  expect(stats.enrichment).toBeGreaterThanOrEqual(5);
  if (process.env.SI_TUNES_SCREENSHOT_DIR) {
    await page.screenshot({
      path: `${process.env.SI_TUNES_SCREENSHOT_DIR}/playlist-desktop.png`,
      fullPage: true,
    });
  }
});

test("strict explicit-off policy surfaces insufficient clean catalog data", async ({
  page,
}) => {
  await page.goto("/");
  await page.getByRole("button", { name: "After-hours drive" }).click();
  await page.getByRole("switch", { name: "Allow explicit music" }).click();
  await page.getByRole("button", { name: "Find songs" }).click();
  await expect(page.getByRole("alert")).toContainText(
    "unknown explicit status",
  );
  await expect(
    page.getByRole("link", { name: "Find on YouTube Music" }),
  ).toHaveCount(0);
});

test("downstream failure is visible and the form can retry", async ({
  page,
}) => {
  await page.goto("/");
  await page
    .getByLabel("What are you in the mood for?")
    .fill("electronic music");
  await page.getByRole("button", { name: "Find songs" }).click();
  await expect(page.getByRole("alert")).toContainText(
    "MusicBrainz is unavailable",
  );
  await expect(page.getByRole("button", { name: "Find songs" })).toBeEnabled();
  await page.getByRole("button", { name: "After-hours drive" }).click();
  await page.getByRole("button", { name: "Find songs" }).click();
  await expect(
    page.getByRole("link", { name: "Find on YouTube Music" }),
  ).toHaveCount(5);
});

test("mobile form and results fit the viewport", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await page.getByRole("button", { name: "After-hours drive" }).click();
  await page.getByRole("button", { name: "Find songs" }).click();
  await expect(
    page.getByRole("link", { name: "Find on YouTube Music" }),
  ).toHaveCount(5);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  if (process.env.SI_TUNES_SCREENSHOT_DIR) {
    await page.screenshot({
      path: `${process.env.SI_TUNES_SCREENSHOT_DIR}/playlist-mobile.png`,
      fullPage: true,
    });
  }
});

test("API rejects invalid input through the actual server", async ({
  request,
}) => {
  const response = await request.post("/api/playlists/suggestions", {
    data: {
      prompt: "",
      targetType: "SONG_COUNT",
      targetValue: 0,
      allowExplicit: true,
      wildcardsEnabled: false,
    },
  });
  expect(response.status()).toBe(400);
  expect(((await response.json()) as { code: string }).code).toBe(
    "INVALID_REQUEST",
  );
});

test("form and populated results have no detectable WCAG A/AA violations", async ({
  page,
}) => {
  await page.goto("/");
  const formAudit = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .analyze();
  expect(formAudit.violations).toEqual([]);
  await page.getByRole("button", { name: "After-hours drive" }).click();
  await page.getByRole("button", { name: "Find songs" }).click();
  await expect(
    page.getByRole("link", { name: "Find on YouTube Music" }),
  ).toHaveCount(5);
  const resultsAudit = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .analyze();
  expect(resultsAudit.violations).toEqual([]);
});
