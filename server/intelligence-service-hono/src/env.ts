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
		PORT: z.coerce.number().default(9999),
		LOG_LEVEL: z.enum([
			"fatal",
			"error",
			"warn",
			"info",
			"debug",
			"trace",
			"silent",
		]),
		DATABASE_URL: z.string().url(),
		DATABASE_AUTH_TOKEN: z.string().optional(),
	})
	.superRefine((input, ctx) => {
		if (input.NODE_ENV === "production" && !input.DATABASE_AUTH_TOKEN) {
			ctx.addIssue({
				code: z.ZodIssueCode.invalid_type,
				expected: "string",
				received: "undefined",
				path: ["DATABASE_AUTH_TOKEN"],
				message: "Must be set when NODE_ENV is 'production'",
			});
		}
	});

export type env = z.infer<typeof EnvSchema>;

const { data: parsed, error } = EnvSchema.safeParse(process.env);

if (error) {
	console.error("❌ Invalid env:");
	console.error(JSON.stringify(error.flatten().fieldErrors, null, 2));
	process.exit(1);
}

// At this point parsed is guaranteed by the guard above
const env: env = parsed as env;
export default env;
