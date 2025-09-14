import path from "node:path";
import { config } from "dotenv";
import { expand } from "dotenv-expand";
import { z } from "zod";

expand(
	config({
		path: path.resolve(
			process.cwd(),
			process.env.NODE_ENV === "test" ? ".env.test" : ".env",
		),
	}),
);

const EnvSchema = z
	.object({
		NODE_ENV: z.string().default("development"),
		PORT: z.coerce.number().default(8000),
		LOG_LEVEL: z
			.enum([
			"fatal",
			"error",
			"warn",
			"info",
			"debug",
			"trace",
			"silent",
			])
			.default("info"),
		DATABASE_URL: z.url(),
	});

export type env = z.infer<typeof EnvSchema>;

const { data: parsed, error } = EnvSchema.safeParse(process.env);

if (error) {
	console.error("❌ Invalid env:");
	console.error(JSON.stringify(z.treeifyError(error).properties, null, 2));
	process.exit(1);
}

// At this point parsed is guaranteed by the guard above
const env: env = parsed as env;
export default env;
