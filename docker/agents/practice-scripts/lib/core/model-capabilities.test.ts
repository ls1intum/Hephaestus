import assert from "node:assert/strict";
import test from "node:test";
import { supportsForcedToolChoice } from "./model-capabilities.ts";

void test("forced tool choice follows model capability across gateway model names", () => {
	for (const model of [
		"claude-opus-5-5",
		"anthropic/claude-sonnet-5.5",
		"claude-opus-5-5-20261001",
		"claude-sonnet-6-0",
	]) {
		assert.equal(supportsForcedToolChoice(model), false, model);
	}
	for (const model of ["claude-opus-4-6", "claude-sonnet-5-4", "gpt-6", undefined]) {
		assert.equal(supportsForcedToolChoice(model), true, model);
	}
});
