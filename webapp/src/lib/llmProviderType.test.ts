import { describe, expect, it } from "vitest";
import {
	API_PROTOCOLS,
	authModeDefaultFor,
	baseUrlDefaultFor,
	defaultProtocolFor,
	PROVIDER_PRESET_ORDER,
	presetForConnection,
} from "./llmProviderType";

describe("OpenAI-compatible endpoint presets", () => {
	it("offers OpenAI, Azure v1, and a generic OpenAI-compatible endpoint", () => {
		expect(PROVIDER_PRESET_ORDER).toEqual(["OPENAI", "AZURE_OPENAI_V1", "OTHER"]);
		expect(authModeDefaultFor("OPENAI")).toBe("BEARER");
		expect(authModeDefaultFor("AZURE_OPENAI_V1")).toBe("API_KEY");
		expect(baseUrlDefaultFor("AZURE_OPENAI_V1")).toBe(
			"https://RESOURCE.openai.azure.com/openai/v1",
		);
	});

	it("supports the two exact OpenAI wire APIs", () => {
		expect(defaultProtocolFor(true)).toBe(API_PROTOCOLS.OPENAI_RESPONSES);
		expect(defaultProtocolFor(false)).toBe(API_PROTOCOLS.OPENAI_COMPLETIONS);
	});

	it("does not infer the create-time Azure preset while editing", () => {
		expect(
			presetForConnection({
				apiProtocol: API_PROTOCOLS.OPENAI_RESPONSES,
				baseUrl: "https://example.openai.azure.com/openai/v1",
			}),
		).toBe("OTHER");
	});
});
