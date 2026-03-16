import path from "node:path";
import { config } from "dotenv";
import { expand } from "dotenv-expand";
import { defineConfig } from "drizzle-kit";

// Load application-server .env for port overrides (same pattern as check-ports.sh)
expand(
	config({ path: path.resolve(process.cwd(), "../application-server/.env"), override: false }),
);
// Then load intelligence-service .env (won't override vars already set above)
expand(config({ path: path.resolve(process.cwd(), ".env"), override: false }));

const port = process.env.POSTGRES_PORT || "5432";
const databaseUrl =
	process.env.DATABASE_URL ||
	`postgresql://${process.env.DB_USERNAME || "root"}:${process.env.DB_PASSWORD || "root"}@localhost:${port}/hephaestus`;

export default defineConfig({
	out: "./drizzle",
	schema: "./src/shared/db/schema.ts",
	dialect: "postgresql",
	dbCredentials: {
		url: databaseUrl,
	},
});
