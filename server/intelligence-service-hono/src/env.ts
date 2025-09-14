import path from "node:path";
import { config } from "dotenv";
import { expand } from "dotenv-expand";
import { z } from "zod";
import { getModel } from "./lib/ai-model";

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
			.enum(["fatal", "error", "warn", "info", "debug", "trace", "silent"])
			.default("info"),
		DATABASE_URL: z.string().url(),

		// LLM Providers
		OPENAI_API_KEY: z.string().min(1).optional(),
		AZURE_RESOURCE_NAME: z.string().min(1).optional(),
		AZURE_API_KEY: z.string().min(1).optional(),

		// Models
		MODEL_NAME: z.string().min(1).default("openai:gpt-5-mini"),
		DETECTION_MODEL_NAME: z.string().min(1).default("openai:gpt-5-mini"),

		// LangFuse (optional)
		LANGFUSE_SECRET_KEY: z.string().min(1).optional(),
		LANGFUSE_PUBLIC_KEY: z.string().min(1).optional(),
		LANGFUSE_BASE_URL: z.string().min(1).url().optional(),
	})
	.superRefine((val, ctx) => {
		// Basic model format validation: "provider:model"
		const parseProvider = (modelName: string) => {
			const [provider, model] = modelName.split(":");
			return { provider, model } as const;
		};

		const modelPairs = [
			["MODEL_NAME", val.MODEL_NAME],
			["DETECTION_MODEL_NAME", val.DETECTION_MODEL_NAME],
		] as const;
		for (const [field, m] of modelPairs) {
			const { provider, model } = parseProvider(m);
			if (!provider || !model) {
				ctx.addIssue({
					code: z.ZodIssueCode.custom,
					message: `Invalid model format: ${m}. Expected <provider>:<model>`,
					path: [field],
				});
			}
			if (!["openai", "azure"].includes(provider)) {
				ctx.addIssue({
					code: z.ZodIssueCode.custom,
					message: `Unsupported provider: ${provider}. Use 'openai', 'azure', or 'azure_openai'.`,
					path: [field],
				});
			}
		}

		// Conditional provider credentials
		const modelValues = modelPairs.map(([, v]) => v);
		const usesOpenAI = modelValues.some((m) => m.startsWith("openai:"));
		const usesAzure = modelValues.some(
			(m) => m.startsWith("azure:") || m.startsWith("azure_openai:"),
		);

		if (usesOpenAI && !val.OPENAI_API_KEY) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				message: "OPENAI_API_KEY is required when using an OpenAI model",
				path: ["OPENAI_API_KEY"],
			});
		}

		if (usesAzure) {
			if (!val.AZURE_API_KEY) {
				ctx.addIssue({
					code: z.ZodIssueCode.custom,
					message: "AZURE_API_KEY is required when using an Azure model",
					path: ["AZURE_API_KEY"],
				});
			}
			if (!val.AZURE_RESOURCE_NAME) {
				ctx.addIssue({
					code: z.ZodIssueCode.custom,
					message: "AZURE_RESOURCE_NAME is required when using an Azure model",
					path: ["AZURE_RESOURCE_NAME"],
				});
			}
		}
	});

export type env = z.infer<typeof EnvSchema>;

const { data: parsed, error } = EnvSchema.safeParse(process.env);

if (error) {
	console.error("❌ Invalid env:");
	console.error(JSON.stringify(z.treeifyError(error).properties, null, 2));
	process.exit(1);
}

const defaultModel = getModel(parsed.MODEL_NAME);
const detectionModel = getModel(parsed.DETECTION_MODEL_NAME);

export default {
	...parsed,
	defaultModel,
	detectionModel,
};
