import { spawn } from "node:child_process";
import { copyFile, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import { type Browser, chromium } from "playwright";

const webappDirectory = resolve(import.meta.dirname, "..");
const sourceDirectory = resolve(webappDirectory, "brand");
const publicDirectory = resolve(webappDirectory, "public");
const docsImageDirectory = resolve(webappDirectory, "../docs/static/img");
const docsBrandDirectory = resolve(docsImageDirectory, "brand");
const readmeImageDirectory = resolve(webappDirectory, "../docs/images/readme");
const proxyComposePath = resolve(webappDirectory, "../docker/compose.proxy.yaml");
const markSvg = await readFile(resolve(sourceDirectory, "hephaestus-mark.svg"), "utf8");
const interFont = await readFile(
	new URL(import.meta.resolve("@fontsource-variable/inter/files/inter-latin-wght-normal.woff2")),
);
const FONT_FACE = `@font-face{font-family:Inter;font-style:normal;font-weight:100 900;src:url(data:font/woff2;base64,${interFont.toString("base64")}) format('woff2')}`;
const SIGNAL_BLUE = "#315FDC";
const DARK_ACCENT = "#8EAEFF";
const DARK_SURFACE = "#111318";
const storybookPort = 6106;
const storybookUrl = `http://127.0.0.1:${storybookPort}`;

await mkdir(resolve(publicDirectory, "brand"), { recursive: true });
await rm(docsBrandDirectory, { recursive: true, force: true });
await mkdir(docsBrandDirectory, { recursive: true });
await rm(readmeImageDirectory, { recursive: true, force: true });
await mkdir(readmeImageDirectory, { recursive: true });

for (const [source, targets] of [
	["hephaestus-mark.svg", [resolve(publicDirectory, "brand/hephaestus-mark.svg")]],
] as const) {
	for (const target of targets) await copyFile(resolve(sourceDirectory, source), target);
}

const proxyCompose = await readFile(proxyComposePath, "utf8");
const proxyMark = markSvg
	.replace(
		'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128" role="img" aria-labelledby="title"><title id="title">Hephaestus</title>',
		'<svg class="brand" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128" role="img" aria-label="Hephaestus">',
	)
	.trim();
const nextProxyCompose = proxyCompose.replace(
	/<svg class="brand" xmlns="http:\/\/www\.w3\.org\/2000\/svg".*<\/svg>/,
	proxyMark,
);
if (nextProxyCompose === proxyCompose && !proxyCompose.includes(proxyMark)) {
	throw new Error("The maintenance-page brand mark could not be updated.");
}
await writeFile(proxyComposePath, nextProxyCompose);

interface ReadmeCapture {
	name: string;
	storyId: string;
	selector: string;
	viewportWidth: number;
	expectedWidth: number;
}

const readmeCaptures: ReadmeCapture[] = [
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

const browser = await chromium.launch();
try {
	const page = await browser.newPage();

	const captureMark = async (
		path: string,
		size: number,
		opaque = false,
		keepInsideSafeZone = false,
	): Promise<void> => {
		await page.setViewportSize({ width: size, height: size });
		await page.setContent(
			`<style>*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;background:${opaque ? SIGNAL_BLUE : "transparent"};display:grid;place-items:center}svg{display:block;width:${keepInsideSafeZone ? "80%" : "100%"};height:${keepInsideSafeZone ? "80%" : "100%"}}</style>${markSvg}`,
		);
		await page.screenshot({ path, omitBackground: !opaque });
	};

	await captureMark(resolve(publicDirectory, "favicon.png"), 64);
	await captureMark(resolve(publicDirectory, "apple-touch-icon.png"), 180, true);
	await captureMark(resolve(publicDirectory, "icon-192.png"), 192, true);
	await captureMark(resolve(publicDirectory, "icon-512.png"), 512, true);
	await captureMark(resolve(publicDirectory, "icon-maskable-512.png"), 512, true, true);
	await captureMark(resolve(docsBrandDirectory, "slack-app-icon-512.png"), 512, true);
	await captureMark(resolve(docsBrandDirectory, "external-app-icon-1024.png"), 1024, true);
	await captureMark(resolve(docsImageDirectory, "favicon.png"), 64);

	const captureLockup = async (path: string, dark: boolean): Promise<void> => {
		await page.setViewportSize({ width: 1240, height: 256 });
		await page.setContent(
			`<style>${FONT_FACE}html,body{margin:0;width:100%;height:100%;font-family:Inter,sans-serif;background:transparent;color:${dark ? "#F8FAFC" : "#17191F"}}main{height:100%;display:flex;align-items:center;gap:56px}.mark{width:256px;height:256px}.name{font-size:144px;font-weight:650;letter-spacing:-4px}.heph{color:${dark ? DARK_ACCENT : SIGNAL_BLUE}}</style><main><div class="mark">${markSvg}</div><div class="name"><span class="heph">Heph</span>aestus</div></main>`,
		);
		await page.evaluate(() => document.fonts.ready);
		await page.screenshot({ path, omitBackground: true });
	};

	await captureLockup(resolve(docsBrandDirectory, "hephaestus-lockup-light.png"), false);
	await captureLockup(resolve(docsBrandDirectory, "hephaestus-lockup-dark.png"), true);

	await page.setViewportSize({ width: 1200, height: 630 });
	await page.setContent(
		`<style>${FONT_FACE}html,body{margin:0;width:100%;height:100%;font-family:Inter,sans-serif;background:${DARK_SURFACE};color:#f8fafc}.card{height:100%;display:flex;align-items:center;padding:96px;gap:56px}.mark{width:230px;height:230px}.name{font-size:86px;font-weight:700;letter-spacing:-3px}.heph{color:${DARK_ACCENT}}.tagline{margin-top:22px;font-size:34px;color:#b8bec9;max-width:650px;line-height:1.25}</style><main class="card"><div class="mark">${markSvg}</div><div><div class="name"><span class="heph">Heph</span>aestus</div><div class="tagline">Learn from the work you're already doing</div></div></main>`,
	);
	await page.evaluate(() => document.fonts.ready);
	const socialCard = resolve(docsImageDirectory, "hephaestus-social-card.png");
	await page.screenshot({ path: socialCard });
	await copyFile(socialCard, resolve(publicDirectory, "hephaestus-social-card.png"));

	await page.setViewportSize({ width: 1280, height: 640 });
	await page.setContent(
		`<style>${FONT_FACE}html,body{margin:0;width:100%;height:100%;font-family:Inter,sans-serif;background:${DARK_SURFACE};color:#f8fafc}.card{height:100%;display:flex;align-items:center;padding:96px 112px;gap:56px}.mark{width:230px;height:230px}.name{font-size:86px;font-weight:700;letter-spacing:-3px}.heph{color:${DARK_ACCENT}}.tagline{margin-top:22px;font-size:34px;color:#b8bec9;max-width:650px;line-height:1.25}</style><main class="card"><div class="mark">${markSvg}</div><div><div class="name"><span class="heph">Heph</span>aestus</div><div class="tagline">Learn from the work you're already doing</div></div></main>`,
	);
	await page.evaluate(() => document.fonts.ready);
	await page.screenshot({
		path: resolve(docsBrandDirectory, "github-repository-social-preview-1280x640.png"),
	});

	await exportReadmeImages(browser);
} finally {
	await browser.close();
}

async function waitForStorybook(storyId: string): Promise<void> {
	for (let attempt = 0; attempt < 120; attempt += 1) {
		const response = await fetch(`${storybookUrl}/index.json`).catch(() => undefined);
		if (response?.ok) {
			const index: unknown = await response.json();
			if (
				!index ||
				typeof index !== "object" ||
				!("entries" in index) ||
				!index.entries ||
				typeof index.entries !== "object" ||
				!(storyId in index.entries)
			) {
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

async function exportReadmeImages(activeBrowser: Browser): Promise<void> {
	const storybook = spawn(
		"pnpm",
		["run", "storybook:dev", "--port", String(storybookPort), "--ci", "--host", "127.0.0.1"],
		{ cwd: webappDirectory, stdio: "ignore" },
	);

	try {
		for (const capture of readmeCaptures) await waitForStorybook(capture.storyId);
		for (const capture of readmeCaptures) {
			for (const theme of ["light", "dark"] as const) {
				const page = await activeBrowser.newPage({
					viewport: { width: capture.viewportWidth, height: 800 },
					deviceScaleFactor: 2,
					colorScheme: theme,
					reducedMotion: "reduce",
				});
				try {
					const globals = encodeURIComponent(`theme:${theme}`);
					await page.goto(
						`${storybookUrl}/iframe.html?id=${capture.storyId}&viewMode=story&globals=${globals}`,
					);
					await page.waitForLoadState("networkidle");
					await page.evaluate(() => document.fonts.ready);
					await page.addStyleTag({
						content:
							"*,*::before,*::after{animation:none!important;transition:none!important;caret-color:transparent!important}" +
							"[data-readme-actions]{display:none!important}",
					});
					const surface = page.locator(capture.selector);
					await surface.waitFor({ state: "visible" });
					const bounds = await surface.boundingBox();
					if (!bounds || Math.round(bounds.width) !== capture.expectedWidth) {
						throw new Error(
							`${capture.name} export width was ${bounds?.width ?? "missing"}px; expected ${capture.expectedWidth}px.`,
						);
					}
					await surface.screenshot({
						path: resolve(readmeImageDirectory, `${capture.name}-${theme}.png`),
					});
				} finally {
					await page.close();
				}
			}
		}
	} finally {
		storybook.kill("SIGTERM");
	}
}
