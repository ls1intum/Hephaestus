import { copyFile, mkdir, readFile } from "node:fs/promises";
import { resolve } from "node:path";

import { chromium } from "playwright";

const webappDirectory = resolve(import.meta.dirname, "..");
const sourceDirectory = resolve(webappDirectory, "brand");
const publicDirectory = resolve(webappDirectory, "public");
const docsImageDirectory = resolve(webappDirectory, "../docs/static/img");
const docsBrandDirectory = resolve(docsImageDirectory, "brand");
const markSvg = await readFile(resolve(sourceDirectory, "hephaestus-mark.svg"), "utf8");

await mkdir(resolve(publicDirectory, "brand"), { recursive: true });
await mkdir(docsBrandDirectory, { recursive: true });

for (const [source, targets] of [
	[
		"hephaestus-mark.svg",
		[
			resolve(publicDirectory, "brand/hephaestus-mark.svg"),
			resolve(docsImageDirectory, "hephaestus-mark.svg"),
			resolve(docsBrandDirectory, "hephaestus-mark.svg"),
		],
	],
	["hephaestus-lockup-light.svg", [resolve(docsBrandDirectory, "hephaestus-lockup-light.svg")]],
	["hephaestus-lockup-dark.svg", [resolve(docsBrandDirectory, "hephaestus-lockup-dark.svg")]],
] as const) {
	for (const target of targets) await copyFile(resolve(sourceDirectory, source), target);
}

const browser = await chromium.launch();
const page = await browser.newPage();

async function captureMark(
	path: string,
	size: number,
	opaque = false,
	keepInsideSafeZone = false,
): Promise<void> {
	await page.setViewportSize({ width: size, height: size });
	await page.setContent(
		`<style>*{box-sizing:border-box}html,body{margin:0;width:100%;height:100%;background:${opaque ? "#315FDC" : "transparent"};display:grid;place-items:center}svg{display:block;width:${keepInsideSafeZone ? "80%" : "100%"};height:${keepInsideSafeZone ? "80%" : "100%"}}</style>${markSvg}`,
	);
	await page.screenshot({ path, omitBackground: !opaque });
}

await captureMark(resolve(publicDirectory, "favicon.png"), 64);
await captureMark(resolve(publicDirectory, "apple-touch-icon.png"), 180, true);
await captureMark(resolve(publicDirectory, "icon-192.png"), 192, true);
await captureMark(resolve(publicDirectory, "icon-512.png"), 512, true);
await captureMark(resolve(publicDirectory, "icon-maskable-512.png"), 512, true, true);
await captureMark(resolve(docsBrandDirectory, "hephaestus-app-icon-1024.png"), 1024, true);
await captureMark(resolve(docsImageDirectory, "favicon.png"), 64);

await page.setViewportSize({ width: 1200, height: 630 });
await page.setContent(
	`<style>html,body{margin:0;width:100%;height:100%;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#111318;color:#f8fafc}.card{height:100%;display:flex;align-items:center;padding:96px;gap:56px}.mark{width:230px;height:230px}.name{font-size:86px;font-weight:700;letter-spacing:-3px}.heph{color:#8eaeff}.tagline{margin-top:22px;font-size:34px;color:#b8bec9;max-width:650px;line-height:1.25}</style><main class="card"><div class="mark">${markSvg}</div><div><div class="name"><span class="heph">Heph</span>aestus</div><div class="tagline">Learn from the work you're already doing</div></div></main>`,
);
await page.screenshot({ path: resolve(docsImageDirectory, "hephaestus-social-card.png") });
await browser.close();
