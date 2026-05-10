import path from "node:path";
import { devToolsMiddleware } from "@ai-sdk/devtools";
import { type LanguageModel, wrapLanguageModel } from "ai";
import { config } from "dotenv";
import { expand } from "dotenv-expand";
import { z } from "zod";
import { getModel, SUPPORTED_PROVIDERS } from "@/shared/ai/model";

const ENV_FILE_PATH = process.env.NODE_ENV === "test" ? ".env.test" : ".env";

expand(
	config({
		path: path.resolve(process.cwd(), ENV_FILE_PATH),
		quiet: true, // Suppress dotenv info messages
	}),
);

/** Treat empty strings as undefined — Docker/K8s set unassigned vars to "". */
const optionalString = z.preprocess(
	(v) => (v === "" ? undefined : v),
	z.string().min(1).optional(),
);

const EnvSchema = z
	.object({
		NODE_ENV: z.string().default("development"),
		PORT: z.coerce.number().default(8000),
		LOG_LEVEL: z
			.enum(["fatal", "error", "warn", "info", "debug", "trace", "silent"])
			.default("info"),
		// DATABASE_URL is required at runtime but has a placeholder default for OpenAPI export
		DATABASE_URL: z
			.string()
			.url()
			.default("postgresql://placeholder:placeholder@localhost:5432/placeholder"),

		VERBOSE_LOGGING: z
			.enum(["true", "false", "1", "0"])
			.default("false")
			.transform((v) => v === "true" || v === "1"),
		VERBOSE_LOG_FILE: z.string().default("logs/verbose.log"),

		// LLM providers
		OPENAI_API_KEY: optionalString,
		AZURE_RESOURCE_NAME: optionalString,
		AZURE_API_KEY: optionalString,

		MODEL_NAME: z.string().min(1).default("openai:gpt-4o-mini"),
	})
	.superRefine((val, ctx) => {
		const colonIndex = val.MODEL_NAME.indexOf(":");
		if (colonIndex === -1) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				message: `Invalid model format: ${val.MODEL_NAME}. Expected <provider>:<model>`,
				path: ["MODEL_NAME"],
			});
			return;
		}

		const provider = val.MODEL_NAME.slice(0, colonIndex);
		const model = val.MODEL_NAME.slice(colonIndex + 1);

		if (!model) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				message: `Invalid model format: ${val.MODEL_NAME}. Expected <provider>:<model>`,
				path: ["MODEL_NAME"],
			});
			return;
		}

		if (!SUPPORTED_PROVIDERS.includes(provider as (typeof SUPPORTED_PROVIDERS)[number])) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				message: `Unsupported provider: ${provider}. Supported providers: ${SUPPORTED_PROVIDERS.join(", ")}`,
				path: ["MODEL_NAME"],
			});
		}

		if (val.MODEL_NAME.startsWith("openai:") && !val.OPENAI_API_KEY) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				message: "OPENAI_API_KEY is required when using an OpenAI model",
				path: ["OPENAI_API_KEY"],
			});
		}

		const usesAzure =
			val.MODEL_NAME.startsWith("azure:") || val.MODEL_NAME.startsWith("azure_openai:");

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

const isDev = envData.NODE_ENV === "development";
const wrapWithDevTools = (model: ReturnType<typeof getModel>): LanguageModel =>
	isDev
		? wrapLanguageModel({
				model,
				middleware: devToolsMiddleware(),
			})
		: model;

interface EnvWithModels extends Env {
	defaultModel: LanguageModel;
}

const env: EnvWithModels = {
	...envData,
	defaultModel: wrapWithDevTools(getModel(envData.MODEL_NAME)),
};

export default env;
