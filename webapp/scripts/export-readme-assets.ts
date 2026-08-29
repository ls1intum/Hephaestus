import { spawn } from "node:child_process";
import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

import { type Browser, chromium } from "playwright";

const scriptDirectory = import.meta.dirname;
const webappDirectory = resolve(scriptDirectory, "..");
const outputDirectory = resolve(webappDirectory, "../docs/images/readme");
const port = 6106;
const storybookUrl = `http://127.0.0.1:${port}`;

type Theme = "light" | "dark";

interface CaptureConfig {
	name: string;
	storyId: string;
	selector: string;
	viewportWidth: number;
	/** Asserted after render; a mismatch fails the export instead of committing a resized image. */
	expectedWidth: number;
}

const themes: Theme[] = ["light", "dark"];
const captureConfigs: CaptureConfig[] = [
	{
		name: "landing-hero",
		storyId: "components-info-landing-landingherosection--readme-export",
		selector: '[data-readme-export="landing-hero"]',
		viewportWidth: 1440,
		expectedWidth: 1280,
	},
	{
		name: "feedback-scene",
		storyId: "components-info-landing-landingherosection--scene-export",
		selector: '[data-readme-export="feedback-scene"]',
		viewportWidth: 1024,
		expectedWidth: 896,
	},
];

function indexContains(index: unknown, storyId: string): boolean {
	if (!index || typeof index !== "object" || !("entries" in index)) return false;
	const { entries } = index;
	return Boolean(entries && typeof entries === "object" && storyId in entries);
}

async function waitForStorybook(storyId: string): Promise<void> {
	for (let attempt = 0; attempt < 120; attempt += 1) {
		const response = await fetch(`${storybookUrl}/index.json`).catch(() => undefined);
		if (response?.ok) {
			if (!indexContains(await response.json(), storyId)) {
				throw new Error(`Story ${storyId} is not in the Storybook index. Was it renamed?`);
			}
			return;
		}
		await new Promise((resolveDelay) => {
			setTimeout(resolveDelay, 500);
		});
	}
	throw new Error("Storybook did not start within 60 seconds.");
}

async function capture(
	browser: Browser,
	theme: Theme,
	name: string,
	config: CaptureConfig,
): Promise<void> {
	const page = await browser.newPage({
		viewport: { width: config.viewportWidth, height: 800 },
		deviceScaleFactor: 2,
		colorScheme: theme,
		reducedMotion: "reduce",
	});

	try {
		const globals = encodeURIComponent(`theme:${theme}`);
		await page.goto(
			`${storybookUrl}/iframe.html?id=${config.storyId}&viewMode=story&globals=${globals}`,
		);
		await page.waitForLoadState("networkidle");
		await page.evaluate(() => document.fonts.ready);
		await page.addStyleTag({
			content:
				"*,*::before,*::after{animation:none!important;transition:none!important;caret-color:transparent!important}" +
				"[data-readme-actions]{display:none!important}",
		});

		const exportSurface = page.locator(config.selector);
		await exportSurface.waitFor({ state: "visible" });
		const bounds = await exportSurface.boundingBox();
		if (!bounds || Math.round(bounds.width) !== config.expectedWidth) {
			throw new Error(
				`${name} export width was ${bounds?.width ?? "missing"}px; expected ${config.expectedWidth}px.`,
			);
		}

		const outputPath = resolve(outputDirectory, `${name}-${theme}.png`);
		await exportSurface.screenshot({ path: outputPath });
		process.stdout.write(`Exported ${outputPath}\n`);
	} finally {
		await page.close();
	}
}

await mkdir(outputDirectory, { recursive: true });
const storybook = spawn(
	"bun",
	["run", "storybook:dev", "--", "--port", String(port), "--ci", "--host", "127.0.0.1"],
	{
		cwd: webappDirectory,
		stdio: "ignore",
	},
);

try {
	for (const config of captureConfigs) await waitForStorybook(config.storyId);
	const browser = await chromium.launch();
	try {
		for (const config of captureConfigs) {
			for (const theme of themes) await capture(browser, theme, config.name, config);
		}
	} finally {
		await browser.close();
	}
} finally {
	storybook.kill("SIGTERM");
}
