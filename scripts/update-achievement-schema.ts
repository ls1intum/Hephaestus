import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { join, basename } from 'node:path';

const SCHEMA_PATH = 'server/application-server/src/main/resources/achievements/achievements-schema.json';
const EVALUATOR_PKG_PATH = 'server/application-server/src/main/java/de/tum/in/www1/hephaestus/achievement/evaluator';
const PACKAGE_PREFIX = 'de.tum.in.www1.hephaestus.achievement.evaluator.';

function updateSchema() {
	try {
		// 1. Find all evaluators
		const files = readdirSync(EVALUATOR_PKG_PATH);
		const evaluators = files
			.filter((file) => file.endsWith('.java') && file !== 'AchievementEvaluator.java')
			.map((file) => basename(file, '.java'))
			.sort();

		const fullPaths = evaluators.map((name) => PACKAGE_PREFIX + name);
		const allOptions = [...evaluators, ...fullPaths];

		// 2. Read schema
		const schemaContent = readFileSync(SCHEMA_PATH, 'utf-8');
		const schema = JSON.parse(schemaContent);

		// 3. Update evaluatorClass enum
		if (schema.definitions?.achievement?.properties?.evaluatorClass) {
			schema.definitions.achievement.properties.evaluatorClass.enum = allOptions;
		} else {
			console.error('Could not find evaluatorClass property in schema');
			process.exit(1);
		}

		// 4. Write back
		writeFileSync(SCHEMA_PATH, JSON.stringify(schema, null, '\t') + '\n');
		console.log(`Successfully updated ${SCHEMA_PATH} with ${evaluators.length} evaluators.`);
	} catch (error) {
		console.error('Error updating achievement schema:', error);
		process.exit(1);
	}
}

updateSchema();
