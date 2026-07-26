import { describe, expect, it } from "vitest";
import { validateLlmConnectionForm, validateLlmModelForm } from "./llm-form-validation";

const validModel = {
	displayName: "GPT-5 mini",
	upstreamModelId: "openai/gpt-5-mini",
	contextWindow: "",
	maxOutputTokens: "",
	pricingMode: "UNPRICED" as const,
};

describe("validateLlmConnectionForm", () => {
	it("accepts a plain provider endpoint", () => {
		expect(
			validateLlmConnectionForm({ displayName: "OpenAI", baseUrl: "https://api.openai.com/v1" }),
		).toEqual({});
	});

	it("rejects a base URL that is not a URL", () => {
		const errors = validateLlmConnectionForm({ displayName: "OpenAI", baseUrl: "api.openai.com" });
		expect(errors.baseUrl).toMatch(/full URL/);
	});

	it("rejects a URL carrying a credential, which would leak the key into logs", () => {
		const errors = validateLlmConnectionForm({
			displayName: "Gateway",
			baseUrl: "https://gw.example.com/v1?api-key=SECRET",
		});
		expect(errors.baseUrl).toMatch(/credentials, query string or fragment/);
	});

	it("skips the base URL on edit, where the form cannot change it", () => {
		expect(validateLlmConnectionForm({ displayName: "OpenAI" })).toEqual({});
	});

	it("requires a display name", () => {
		expect(
			validateLlmConnectionForm({ displayName: "   ", baseUrl: "https://api.openai.com/v1" })
				.displayName,
		).toBeTruthy();
	});
});

describe("validateLlmModelForm", () => {
	it("accepts an unpriced model with no token limits", () => {
		expect(validateLlmModelForm(validModel)).toEqual({});
	});

	it("rejects a negative token limit, which the number input's min alone cannot", () => {
		const errors = validateLlmModelForm({ ...validModel, maxOutputTokens: "-1" });
		expect(errors.maxOutputTokens).toMatch(/whole number/);
	});

	it("rejects a fractional token limit", () => {
		expect(
			validateLlmModelForm({ ...validModel, contextWindow: "1.5" }).contextWindow,
		).toBeTruthy();
	});

	it("rejects a token count the server's int column cannot hold", () => {
		// 2^31 exactly: accepted here, it fails at deserialisation with no field to attach it to.
		expect(
			validateLlmModelForm({ ...validModel, contextWindow: "2147483648" }).contextWindow,
		).toMatch(/2,147,483,647 tokens or fewer/);
		expect(
			validateLlmModelForm({ ...validModel, contextWindow: "2147483647" }).contextWindow,
		).toBeUndefined();
	});

	it("requires both rates once the model is priced", () => {
		const errors = validateLlmModelForm({
			...validModel,
			pricingMode: "PRICED",
			per1mInputUsd: 1.25,
		});
		expect(errors.per1mOutputUsd).toMatch(/Required/);
		expect(errors.per1mInputUsd).toBeUndefined();
	});

	it("rejects a negative rate", () => {
		const errors = validateLlmModelForm({
			...validModel,
			pricingMode: "PRICED",
			per1mInputUsd: -1,
			per1mOutputUsd: 2,
		});
		expect(errors.per1mInputUsd).toMatch(/negative/);
	});

	it("rejects an all-zero price, which would record verified $0 spend forever", () => {
		const errors = validateLlmModelForm({
			...validModel,
			pricingMode: "PRICED",
			per1mInputUsd: 0,
			per1mOutputUsd: 0,
		});
		expect(errors.per1mInputUsd).toMatch(/above zero/);
	});

	it("accepts a price where only one rate is above zero", () => {
		expect(
			validateLlmModelForm({
				...validModel,
				pricingMode: "PRICED",
				per1mInputUsd: 0,
				per1mOutputUsd: 3,
			}),
		).toEqual({});
	});

	it("requires a note for a no-charge model", () => {
		expect(
			validateLlmModelForm({ ...validModel, pricingMode: "NO_CHARGE", note: " " }).note,
		).toBeTruthy();
	});

	it("skips the upstream id on edit, where the form cannot change it", () => {
		expect(validateLlmModelForm({ ...validModel, upstreamModelId: undefined })).toEqual({});
	});
});
