import { spawn } from "node:child_process";
import { mkdir } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { type Browser, chromium } from "playwright";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const webappDirectory = resolve(scriptDirectory, "..");
const outputDirectory = resolve(webappDirectory, "../docs/images/readme");
const port = 6106;
const storybookUrl = `http://127.0.0.1:${port}`;

/** The two colour schemes every asset is exported in, one file each. */
type Theme = "light" | "dark";

/**
 * Breakpoints an asset can be exported at. `desktop` and `mobile` are mandatory because the README
 * shows both; `tablet` only exists for assets whose layout has a third state.
 */
type Breakpoint = "desktop" | "tablet" | "mobile";

interface CaptureConfig {
	storyId: string;
	selector: string;
	viewportWidth: number;
	/**
	 * The width the surface must measure once rendered. A mismatch means the story's layout moved out
	 * from under the README, so the export fails rather than silently committing a resized image.
	 */
	expectedWidth: number;
}

interface AssetExport {
	name: string;
	desktop: CaptureConfig;
	tablet?: CaptureConfig;
	mobile: CaptureConfig;
}

const themes: Theme[] = ["light", "dark"];

const exportsToCapture: AssetExport[] = [
	{
		name: "landing-feedback-preview",
		desktop: {
			storyId: "components-info-landing-landingfeedbackpreview--readme-export",
			selector: '[data-readme-export="landing-feedback-preview"]',
			viewportWidth: 900,
			expectedWidth: 744,
		},
		mobile: {
			storyId: "components-info-landing-landingfeedbackpreview--readme-export",
			selector: '[data-readme-export="landing-feedback-preview"]',
			viewportWidth: 390,
			expectedWidth: 390,
		},
	},
	{
		name: "feedback-loop",
		desktop: {
			storyId: "components-info-landing-landingfeedbackloop--readme-desktop-export",
			selector: '[data-readme-export="feedback-loop-desktop"]',
			viewportWidth: 1280,
			expectedWidth: 1224,
		},
		tablet: {
			storyId: "components-info-landing-landingfeedbackloop--readme-tablet-export",
			selector: '[data-readme-export="feedback-loop-tablet"]',
			viewportWidth: 824,
			expectedWidth: 768,
		},
		mobile: {
			storyId: "components-info-landing-landingfeedbackloop--readme-mobile-export",
			selector: '[data-readme-export="feedback-loop-mobile"]',
			viewportWidth: 390,
			expectedWidth: 390,
		},
	},
];

async function waitForStorybook(): Promise<void> {
	for (let attempt = 0; attempt < 120; attempt += 1) {
		try {
			const response = await fetch(`${storybookUrl}/index.json`);
			if (response.ok) return;
		} catch {
			// Storybook is still starting.
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
	mode: Breakpoint,
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
				"*,*::before,*::after{animation:none!important;transition:none!important;caret-color:transparent!important}",
		});

		const exportSurface = page.locator(config.selector);
		await exportSurface.waitFor({ state: "visible" });
		const bounds = await exportSurface.boundingBox();
		if (!bounds || Math.round(bounds.width) !== config.expectedWidth) {
			throw new Error(
				`${name} ${mode} export width was ${bounds?.width ?? "missing"}px; expected ${config.expectedWidth}px.`,
			);
		}

		const modeSuffix = mode === "desktop" ? "" : `${mode}-`;
		const outputPath = resolve(outputDirectory, `${name}-${modeSuffix}${theme}.png`);
		await exportSurface.screenshot({ path: outputPath });
		console.log(`Exported ${outputPath}`);
	} finally {
		await page.close();
	}
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
		for (const theme of themes) {
			for (const asset of exportsToCapture) {
				await capture(browser, theme, asset.name, "desktop", asset.desktop);
				if (asset.tablet) {
					await capture(browser, theme, asset.name, "tablet", asset.tablet);
				}
				await capture(browser, theme, asset.name, "mobile", asset.mobile);
			}
		}
	} finally {
		await browser.close();
	}
} finally {
	storybook.kill("SIGTERM");
}
