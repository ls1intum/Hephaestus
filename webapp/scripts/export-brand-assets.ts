import { spawn } from "node:child_process";
import { mkdir, readFile } from "node:fs/promises";
import { resolve } from "node:path";

import { type Browser, chromium } from "playwright";

const scriptDirectory = import.meta.dirname;
const webappDirectory = resolve(scriptDirectory, "..");
const outputDirectory = resolve(webappDirectory, "../docs/static/img/brand");
const port = 6107;
const storybookUrl = `http://127.0.0.1:${port}`;
const storyId = "components-mentor-mentoricon--brand-export";

interface CaptureConfig {
	/** Output filename inside docs/static/img/brand/. */
	fileName: string;
	selector: string;
	/** Whether to capture with a transparent backdrop instead of the tile's own background. */
	transparent: boolean;
}

// Each tile is 512 CSS pixels; deviceScaleFactor 2 yields the 1024x1024 brand asset.
const expectedTileSize = 512;
const expectedPixelSize = 1024;

const captureConfigs: CaptureConfig[] = [
	{
		fileName: "heph-avatar-1024.png",
		selector: '[data-brand-export="heph-avatar"]',
		transparent: false,
	},
	{
		fileName: "heph-avatar-1024-transparent.png",
		selector: '[data-brand-export="heph-avatar-transparent"]',
		transparent: true,
	},
];

function indexContains(index: unknown): boolean {
	if (!index || typeof index !== "object" || !("entries" in index)) return false;
	const { entries } = index;
	return Boolean(entries && typeof entries === "object" && storyId in entries);
}

async function waitForStorybook(): Promise<void> {
	for (let attempt = 0; attempt < 120; attempt += 1) {
		const response = await fetch(`${storybookUrl}/index.json`).catch(() => undefined);
		if (response?.ok) {
			if (!indexContains(await response.json())) {
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

/** Asserted after render; a mismatch fails the export instead of committing a wrongly sized image. */
async function assertPngSize(path: string): Promise<void> {
	const png = await readFile(path);
	const width = png.readUInt32BE(16);
	const height = png.readUInt32BE(20);
	if (width !== expectedPixelSize || height !== expectedPixelSize) {
		throw new Error(`${path} is ${width}x${height}px; expected ${expectedPixelSize}px square.`);
	}
}

async function capture(browser: Browser, config: CaptureConfig): Promise<void> {
	const page = await browser.newPage({
		viewport: { width: 1400, height: 700 },
		deviceScaleFactor: 2,
		colorScheme: "light",
		reducedMotion: "reduce",
	});

	try {
		const globals = encodeURIComponent("theme:light");
		await page.goto(`${storybookUrl}/iframe.html?id=${storyId}&viewMode=story&globals=${globals}`);
		await page.waitForLoadState("networkidle");
		await page.addStyleTag({
			content: `*,*::before,*::after{animation:none!important;transition:none!important}${
				config.transparent ? "html,body{background:transparent!important}" : ""
			}`,
		});

		const exportSurface = page.locator(config.selector);
		await exportSurface.waitFor({ state: "visible" });
		const bounds = await exportSurface.boundingBox();
		if (!bounds || Math.round(bounds.width) !== expectedTileSize) {
			throw new Error(
				`${config.fileName} export width was ${bounds?.width ?? "missing"}px; expected ${expectedTileSize}px.`,
			);
		}

		const outputPath = resolve(outputDirectory, config.fileName);
		await exportSurface.screenshot({ path: outputPath, omitBackground: config.transparent });
		await assertPngSize(outputPath);
		process.stdout.write(`Exported ${outputPath}\n`);
	} finally {
		await page.close();
	}
}

// The brand directory also holds hand-drawn SVG marks, so it is never wiped — the
// export only overwrites the PNGs it owns.
await mkdir(outputDirectory, { recursive: true });
const storybook = spawn(
	"pnpm",
	["run", "storybook:dev", "--port", String(port), "--ci", "--host", "127.0.0.1"],
	{
		cwd: webappDirectory,
		stdio: "ignore",
	},
);

try {
	await waitForStorybook();
	const browser = await chromium.launch();
	try {
		for (const config of captureConfigs) await capture(browser, config);
	} finally {
		await browser.close();
	}
} finally {
	storybook.kill("SIGTERM");
}
