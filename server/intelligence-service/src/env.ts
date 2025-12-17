import path from "node:path";
import { config } from "dotenv";
import { expand } from "dotenv-expand";
import { z } from "zod";
import { getModel, SUPPORTED_PROVIDERS } from "@/shared/ai/model";

const ENV_FILE_PATH = process.env.NODE_ENV === "test" ? ".env.test" : ".env";

expand(
	config({
		path: path.resolve(process.cwd(), ENV_FILE_PATH),
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

		// Verbose logging - logs request/response bodies to file
		VERBOSE_LOGGING: z
			.enum(["true", "false", "1", "0"])
			.default("false")
			.transform((v) => v === "true" || v === "1"),
		VERBOSE_LOG_FILE: z.string().default("logs/verbose.log"),

		// LLM Providers
		OPENAI_API_KEY: z.string().min(1).optional(),
		AZURE_RESOURCE_NAME: z.string().min(1).optional(),
		AZURE_API_KEY: z.string().min(1).optional(),

		// Models
		MODEL_NAME: z.string().min(1).default("openai:gpt-4o-mini"),
		DETECTION_MODEL_NAME: z.string().min(1).default("openai:gpt-4o-mini"),

		// LangFuse (optional)
		LANGFUSE_SECRET_KEY: z.string().min(1).optional(),
		LANGFUSE_PUBLIC_KEY: z.string().min(1).optional(),
		LANGFUSE_BASE_URL: z.string().min(1).url().optional(),
	})
	.superRefine((val, ctx) => {
		const parseProvider = (modelName: string) => {
			const colonIndex = modelName.indexOf(":");
			if (colonIndex === -1) {
				return { provider: undefined, model: undefined };
			}
			return {
				provider: modelName.slice(0, colonIndex),
				model: modelName.slice(colonIndex + 1),
			};
		};

		const modelPairs = [
			["MODEL_NAME", val.MODEL_NAME],
			["DETECTION_MODEL_NAME", val.DETECTION_MODEL_NAME],
		] as const;

		for (const [field, modelName] of modelPairs) {
			const { provider, model } = parseProvider(modelName);

			if (!(provider && model)) {
				ctx.addIssue({
					code: z.ZodIssueCode.custom,
					message: `Invalid model format: ${modelName}. Expected <provider>:<model>`,
					path: [field],
				});
				continue;
			}

			if (!SUPPORTED_PROVIDERS.includes(provider as (typeof SUPPORTED_PROVIDERS)[number])) {
				ctx.addIssue({
					code: z.ZodIssueCode.custom,
					message: `Unsupported provider: ${provider}. Supported providers: ${SUPPORTED_PROVIDERS.join(", ")}`,
					path: [field],
				});
			}
		}

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

		if (usesAzure && !val.AZURE_API_KEY) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				message: "AZURE_API_KEY is required when using an Azure model",
				path: ["AZURE_API_KEY"],
			});
		}

		if (usesAzure && !val.AZURE_RESOURCE_NAME) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				message: "AZURE_RESOURCE_NAME is required when using an Azure model",
				path: ["AZURE_RESOURCE_NAME"],
			});
		}
	});

export type Env = z.infer<typeof EnvSchema>;

const parseResult = EnvSchema.safeParse(process.env);

if (!parseResult.success) {
	const errorTree = z.treeifyError(parseResult.error);
	const errorMessage = JSON.stringify(errorTree.properties, null, 2);
	throw new Error(`Invalid environment configuration:\n${errorMessage}`);
}

const envData = parseResult.data;

const env = {
	...envData,
	defaultModel: getModel(envData.MODEL_NAME),
	detectionModel: getModel(envData.DETECTION_MODEL_NAME),
};

export default env;
