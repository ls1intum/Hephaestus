import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { basename } from "node:path";
import { asRecord, at, isRecord, parseJson } from "./lib/json.ts";

const SCHEMA_PATH = "server/application/src/main/resources/achievements/achievements-schema.json";
const EVALUATOR_PKG_PATH =
	"server/application/src/main/java/de/tum/cit/aet/hephaestus/achievement/evaluator";
const PACKAGE_PREFIX = "de.tum.cit.aet.hephaestus.achievement.evaluator.";

function updateSchema(): void {
	try {
		const files = readdirSync(EVALUATOR_PKG_PATH);
		const evaluators = files
			.filter((file) => file.endsWith(".java") && file !== "AchievementEvaluator.java")
			.map((file) => basename(file, ".java"))
			.toSorted();

		// Both spellings of every evaluator: the catalogue writes the bare class name, and the
		// fully qualified one is accepted too, so the enum has to hold each twice.
		const fullPaths = evaluators.map((name) => PACKAGE_PREFIX + name);
		const allOptions = [...evaluators, ...fullPaths];

		const schemaContent = readFileSync(SCHEMA_PATH, "utf8");
		const schema = asRecord(parseJson(schemaContent), SCHEMA_PATH);

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

		writeFileSync(SCHEMA_PATH, `${JSON.stringify(schema, null, "\t")}\n`);
		console.log(`Successfully updated ${SCHEMA_PATH} with ${evaluators.length} evaluators.`);
	} catch (error) {
		console.error("Error updating achievement schema:", error);
		process.exit(1);
	}
}

updateSchema();
