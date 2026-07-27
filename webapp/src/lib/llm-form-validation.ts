import { z } from "zod";
import type { PricingMode } from "@/lib/llm-pricing";

/**
 * Client-side validation for the LLM connection and model forms.
 *
 * Zod + `safeParse`, the same shape the workspace-creation wizard uses
 * (`components/workspace/create-workspace/schemas.ts`), because the forms set `noValidate` — a
 * deliberate choice, so the messages render in `FieldError` rather than in an unstylable browser
 * bubble — which also makes `required`, `min`, `step` and `type="url"` inert. Something has to
 * enforce them, and it may as well be the same something for every field.
 *
 * The rules mirror the server's (`CreateWorkspaceLlmModelRequestDTO`, `LlmPriceValidation`,
 * `EgressPolicy`) so a form the browser accepts is one the server accepts. Deliberately *not*
 * mirrored: anything that depends on server state — whether a host resolves publicly, whether it is
 * on the instance's egress allowlist, whether loopback is permitted. Those can only be answered by
 * the server, and guessing at them here would reject setups an operator has legitimately enabled.
 */

/** Reported back per field, keyed the way the form's own error state is keyed. */
export type FieldErrors<TField extends string> = Partial<Record<TField, string>>;

function fieldErrorsOf<TField extends string>(error: z.ZodError): FieldErrors<TField> {
	const errors: FieldErrors<TField> = {};
	for (const issue of error.issues) {
		const field = issue.path[0] as TField | undefined;
		// First issue per field wins: it is the one closest to what the reader typed.
		if (field !== undefined && errors[field] === undefined) {
			errors[field] = issue.message;
		}
	}
	return errors;
}

const displayNameSchema = z
	.string()
	.trim()
	.min(1, "A display name is required.")
	.max(128, "Use 128 characters or fewer.");

/**
 * Shape only. A URL the browser cannot parse, or one carrying a credential or a query string, is
 * wrong no matter how the instance is configured — `EgressPolicy` rejects the second outright,
 * because that is how a gateway URL smuggles an API key into logs and snapshots.
 */
const baseUrlSchema = z
	.string()
	.trim()
	.min(1, "A base URL is required.")
	.max(2048, "Use 2048 characters or fewer.")
	.superRefine((value, ctx) => {
		let url: URL;
		try {
			url = new URL(value);
		} catch {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				message: "Enter a full URL, including https:// — for example https://api.openai.com/v1.",
			});
			return;
		}
		if (url.protocol !== "https:" && url.protocol !== "http:") {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				message: "Enter a full URL, including https:// — for example https://api.openai.com/v1.",
			});
			return;
		}
		if (url.username !== "" || url.password !== "" || url.search !== "" || url.hash !== "") {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				message: "Remove any credentials, query string or fragment from the URL.",
			});
		}
	});

/** The server stores these as a Java `int`, which rejects anything larger at deserialisation. */
const MAX_TOKEN_COUNT = 2_147_483_647;

/** An `<input type="number">` hands back a string; empty means the admin left it out. */
const tokenCountSchema = z
	.string()
	.trim()
	.refine(
		(value) => value === "" || /^\d+$/.test(value),
		"Enter a whole number of tokens, or leave it blank.",
	)
	.refine(
		(value) => value === "" || !/^\d+$/.test(value) || Number(value) <= MAX_TOKEN_COUNT,
		`Enter ${MAX_TOKEN_COUNT.toLocaleString("en-US")} tokens or fewer.`,
	);

const rateSchema = z
	.number()
	.refine(Number.isFinite, "Enter an amount in USD.")
	.min(0, "Rates can't be negative.");

export type LlmConnectionFormField = "displayName" | "baseUrl";

export interface LlmConnectionFormValue {
	displayName: string;
	/** Omitted when editing: the endpoint is immutable, so the form never sends one. */
	baseUrl?: string;
}

const llmConnectionFormSchema = z.object({
	displayName: displayNameSchema,
	baseUrl: baseUrlSchema.optional(),
});

export function validateLlmConnectionForm(
	value: LlmConnectionFormValue,
): FieldErrors<LlmConnectionFormField> {
	const result = llmConnectionFormSchema.safeParse(value);
	return result.success ? {} : fieldErrorsOf<LlmConnectionFormField>(result.error);
}

export type LlmModelFormField =
	| "displayName"
	| "upstreamModelId"
	| "contextWindow"
	| "maxOutputTokens"
	| "per1mInputUsd"
	| "per1mOutputUsd"
	| "note";

export interface LlmModelFormValue {
	displayName: string;
	/** Omitted when editing: the upstream id is immutable, so the form never sends one. */
	upstreamModelId?: string;
	contextWindow: string;
	maxOutputTokens: string;
	pricingMode: PricingMode;
	per1mInputUsd?: number;
	per1mOutputUsd?: number;
	per1mCacheReadUsd?: number;
	per1mCacheWriteUsd?: number;
	note?: string;
}

const llmModelFormSchema = z
	.object({
		displayName: displayNameSchema,
		upstreamModelId: z
			.string()
			.trim()
			.min(1, "The upstream model id is required.")
			.max(256, "Use 256 characters or fewer.")
			.optional(),
		contextWindow: tokenCountSchema,
		maxOutputTokens: tokenCountSchema,
		pricingMode: z.enum(["PRICED", "NO_CHARGE", "UNPRICED"]),
		per1mInputUsd: rateSchema.optional(),
		per1mOutputUsd: rateSchema.optional(),
		per1mCacheReadUsd: rateSchema.optional(),
		per1mCacheWriteUsd: rateSchema.optional(),
		note: z.string().trim().max(500, "Use 500 characters or fewer.").optional(),
	})
	.superRefine((value, ctx) => {
		if (value.pricingMode === "PRICED") {
			const rates = [
				value.per1mInputUsd,
				value.per1mOutputUsd,
				value.per1mCacheReadUsd,
				value.per1mCacheWriteUsd,
			];
			if (value.per1mInputUsd == null) {
				ctx.addIssue({
					code: z.ZodIssueCode.custom,
					path: ["per1mInputUsd"],
					message: "Required when the model has a price.",
				});
			}
			if (value.per1mOutputUsd == null) {
				ctx.addIssue({
					code: z.ZodIssueCode.custom,
					path: ["per1mOutputUsd"],
					message: "Required when the model has a price.",
				});
			}
			// An all-zero price is not a price: it would record verified $0 spend forever, which is what
			// the free option is for. The server refuses it, so refuse it here rather than at submit.
			if (rates.every((rate) => rate == null || rate === 0) && value.per1mInputUsd != null) {
				ctx.addIssue({
					code: z.ZodIssueCode.custom,
					path: ["per1mInputUsd"],
					message: "At least one rate must be above zero. For a free model, pick the free option.",
				});
			}
		}
		if (value.pricingMode === "NO_CHARGE" && !value.note?.trim()) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				path: ["note"],
				message: "Explain why no metered API rate applies.",
			});
		}
	});

export function validateLlmModelForm(value: LlmModelFormValue): FieldErrors<LlmModelFormField> {
	const result = llmModelFormSchema.safeParse(value);
	return result.success ? {} : fieldErrorsOf<LlmModelFormField>(result.error);
}
