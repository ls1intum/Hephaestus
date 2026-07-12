// One-off capture script for the practice-surface screenshots (docs + PR body).
// Serves nothing itself: point it at an already-served storybook-static build.
import { mkdir } from "node:fs/promises";
import { chromium } from "playwright";

const BASE = process.env.SB_URL ?? "http://127.0.0.1:38230";
const DOCS_DIR = process.env.DOCS_DIR ?? "../docs/user/img/practices";
const PR_DIR = process.env.PR_DIR ?? "/tmp/practice-surfaces-shots";

const SHOTS = [
	{
		id: "components-practices-mypracticespage--filled",
		waitFor: "Include tests with the change",
		out: [`${DOCS_DIR}/my-practices.png`, `${PR_DIR}/developer-view-filled.png`],
	},
	{
		id: "components-practices-practiceoverviewpage--filled",
		waitFor: "Priya Raghavan",
		out: [`${DOCS_DIR}/practice-overview.png`, `${PR_DIR}/mentor-matrix.png`],
		// The play function opens the drill-down sheet; close it so the matrix is the subject.
		closeSheet: true,
	},
	{
		// The story's play function already expands the first practice row to its evidence.
		id: "components-practices-drilldownsheet--filled",
		waitFor: "Retry handling for payment webhooks ships with no test",
		out: [`${DOCS_DIR}/drill-down.png`, `${PR_DIR}/drill-down-sheet.png`],
	},
	{
		id: "components-practices-workspacehealthcards--available",
		waitFor: "Testing your changes",
		out: [`${PR_DIR}/health-cards-available.png`],
	},
	{
		// Component state of the member-facing health payload (the admin overview gets full counts).
		id: "components-practices-workspacehealthcards--suppressed",
		waitFor: "nobody can be singled out",
		out: [`${PR_DIR}/health-cards-suppressed.png`],
	},
];

await mkdir(DOCS_DIR, { recursive: true });
await mkdir(PR_DIR, { recursive: true });

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1280, height: 860 } });

for (const shot of SHOTS) {
	const url = `${BASE}/iframe.html?id=${shot.id}&viewMode=story`;
	await page.goto(url, { waitUntil: "networkidle" });
	await page.getByText(shot.waitFor, { exact: false }).first().waitFor({ timeout: 15000 });
	if (shot.closeSheet) {
		await page.waitForTimeout(800);
		await page.keyboard.press("Escape");
		await page.waitForTimeout(500);
	}
	await page.waitForTimeout(300);
	for (const out of shot.out) {
		await page.screenshot({ path: out, fullPage: true });
	}
	console.log(`captured ${shot.id}`);
}

await browser.close();
