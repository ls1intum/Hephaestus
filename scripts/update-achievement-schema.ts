import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { basename } from "node:path";
import { asRecord, at, isRecord, parseJson } from "./lib/json.ts";

const SCHEMA_PATH = "server/src/main/resources/achievements/achievements-schema.json";
const EVALUATOR_PKG_PATH = "server/src/main/java/de/tum/cit/aet/hephaestus/achievement/evaluator";
const PACKAGE_PREFIX = "de.tum.cit.aet.hephaestus.achievement.evaluator.";

function updateSchema(): void {
	try {
		// 1. Find all evaluators
		const files = readdirSync(EVALUATOR_PKG_PATH);
		const evaluators = files
			.filter((file) => file.endsWith(".java") && file !== "AchievementEvaluator.java")
			.map((file) => basename(file, ".java"))
			.sort();

		const fullPaths = evaluators.map((name) => PACKAGE_PREFIX + name);
		const allOptions = [...evaluators, ...fullPaths];

		// 2. Read schema
		const schemaContent = readFileSync(SCHEMA_PATH, "utf8");
		const schema = asRecord(parseJson(schemaContent), SCHEMA_PATH);

		// 3. Update evaluatorClass enum
		const evaluatorClass = at(
			schema,
			["definitions", "achievement", "properties", "evaluatorClass"],
			SCHEMA_PATH,
		);
		if (!isRecord(evaluatorClass)) {
			console.error("Could not find evaluatorClass property in schema");
			process.exit(1);
		}
		evaluatorClass.enum = allOptions;

		// 4. Write back
		writeFileSync(SCHEMA_PATH, `${JSON.stringify(schema, null, "\t")}\n`);
		console.log(`Successfully updated ${SCHEMA_PATH} with ${evaluators.length} evaluators.`);
	} catch (error) {
		console.error("Error updating achievement schema:", error);
		process.exit(1);
	}
}

updateSchema();
