import fs from "node:fs";
import path from "node:path";

/**
 * Puts every achievement's properties in one order, so a diff shows what changed rather than where
 * the author happened to type it. Lines are moved, never rewritten: a property the order below does
 * not name keeps its text and follows the named ones, so this cannot lose a field it has not met.
 */

const ACHIEVEMENTS_FILE = path.join(
	process.cwd(),
	"server/application/src/main/resources/achievements/achievements.yml",
);
const PREFERRED_ORDER = [
	"id",
	"parent",
	"rarity",
	"isHidden",
	"category",
	"triggerEvents",
	"evaluatorClass",
	"requirements",
];

/**
 * A single achievement entry, collected line-for-line so formatting only reorders what the file
 * already says. The map preserves the order the properties were read in, which is the order any
 * property outside `PREFERRED_ORDER` is written back in.
 */
interface Achievement {
	readonly properties: Map<string, string[]>;
	lastProperty?: string;
}

function formatYaml(): void {
	if (!fs.existsSync(ACHIEVEMENTS_FILE)) {
		console.error(`Error: Could not find ${ACHIEVEMENTS_FILE}`);
		process.exit(1);
	}

	const content = fs.readFileSync(ACHIEVEMENTS_FILE, "utf8");
	const lines = content.split("\n");
	const result: string[] = [];

	let currentAchievement: Achievement | null = null;
	let inRequirements = false;

	for (const line of lines) {
		const trimmed = line.trim();

		// `id` is the first property of every entry, so it is also what starts a new one.
		if (trimmed.startsWith("- id:")) {
			if (currentAchievement) flushAchievement(currentAchievement, result);
			currentAchievement = { properties: new Map() };
			parseProperty(line, currentAchievement);
			continue;
		}

		// An entry's own properties sit at six spaces — but so do the children of `requirements`,
		// which is why that one is claimed whole below rather than read key by key.
		if (
			currentAchievement &&
			line.startsWith("      ") &&
			trimmed.includes(":") &&
			!inRequirements
		) {
			if (trimmed.startsWith("requirements:")) {
				inRequirements = true;
				currentAchievement.properties.set("requirements", [line]);
			} else {
				parseProperty(line, currentAchievement);
			}
			continue;
		}

		if (inRequirements) {
			if (line.startsWith("          ")) {
				currentAchievement?.properties.get("requirements")?.push(line);
				continue;
			}
			inRequirements = false;
		}

		// A list item carries no `:`, so the property branch above passed it by; the last property
		// read is the only thing that says which list it belongs to.
		if (currentAchievement?.lastProperty === "triggerEvents" && trimmed.startsWith("- ")) {
			currentAchievement.properties.get("triggerEvents")?.push(line);
			continue;
		}

		// Anything else ends the achievement being collected, and passes through unchanged.
		if (currentAchievement) {
			flushAchievement(currentAchievement, result);
			currentAchievement = null;
		}
		result.push(line);
	}

	if (currentAchievement) flushAchievement(currentAchievement, result);

	fs.writeFileSync(ACHIEVEMENTS_FILE, result.join("\n"));
	console.log("Successfully formatted achievements.yml");
}

function parseProperty(line: string, achievement: Achievement): void {
	const key = /^\s+([^:]+):/.exec(line)?.[1]?.trim();
	if (key === undefined) return;
	achievement.properties.set(key, [line]);
	achievement.lastProperty = key;
}

function flushAchievement(achievement: Achievement, result: string[]): void {
	// Preferred properties first, then whatever else the entry carried, in the order it was read.
	for (const key of PREFERRED_ORDER) {
		const propertyLines = achievement.properties.get(key);
		if (propertyLines) result.push(...propertyLines);
	}
	for (const [key, propertyLines] of achievement.properties) {
		if (!PREFERRED_ORDER.includes(key)) result.push(...propertyLines);
	}
}

formatYaml();
