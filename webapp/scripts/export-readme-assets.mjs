import { spawn } from "node:child_process";
import { mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const webappDirectory = resolve(scriptDirectory, "..");
const outputDirectory = resolve(webappDirectory, "../docs/images/readme");
const port = 6106;
const storybookUrl = `http://127.0.0.1:${port}`;
const storyId = "components-info-landing-landingfeedbackpreview--readme-export";

async function waitForStorybook() {
	for (let attempt = 0; attempt < 120; attempt += 1) {
		try {
			const response = await fetch(`${storybookUrl}/index.json`);
			if (response.ok) return;
		} catch {
			// Storybook is still starting.
		}
		await new Promise((resolveDelay) => setTimeout(resolveDelay, 500));
	}
	throw new Error("Storybook did not start within 60 seconds.");
}

async function capture(page, theme, mobile) {
	const suffix = mobile ? `mobile-${theme}` : theme;
	const outputPath = resolve(outputDirectory, `landing-feedback-preview-${suffix}.png`);
	const globals = encodeURIComponent(`theme:${theme}`);

	await page.goto(`${storybookUrl}/iframe.html?id=${storyId}&viewMode=story&globals=${globals}`);
	await page.waitForLoadState("networkidle");
	await page.evaluate(() => document.fonts.ready);
	await page.addStyleTag({
		content: "*,*::before,*::after{animation:none!important;transition:none!important;caret-color:transparent!important}",
	});

	const exportSurface = page.locator('[data-readme-export="landing-feedback-preview"]');
	await exportSurface.waitFor({ state: "visible" });
	await exportSurface.screenshot({ path: outputPath });
	console.log(`Exported ${outputPath}`);
}

await mkdir(outputDirectory, { recursive: true });
const storybook = spawn(
	"pnpm",
	["exec", "storybook", "dev", "-p", String(port), "--ci", "--host", "127.0.0.1"],
	{
		cwd: webappDirectory,
		stdio: "ignore",
	},
);

try {
	await waitForStorybook();
	const browser = await chromium.launch();
	try {
		for (const theme of ["light", "dark"]) {
			const desktopPage = await browser.newPage({
				viewport: { width: 900, height: 700 },
				deviceScaleFactor: 2,
				colorScheme: theme,
				reducedMotion: "reduce",
			});
			await capture(desktopPage, theme, false);
			await desktopPage.close();

			const mobilePage = await browser.newPage({
				viewport: { width: 340, height: 700 },
				deviceScaleFactor: 2,
				colorScheme: theme,
				reducedMotion: "reduce",
			});
			await capture(mobilePage, theme, true);
			await mobilePage.close();
		}
	} finally {
		await browser.close();
	}
} finally {
	storybook.kill("SIGTERM");
}
