import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import {
	REVIEW_TOOL_DESCRIPTION,
	WRITE_CONTRACT,
} from "../../../main/resources/agent/pi-runner-composition.ts";

const prompt = (name: string) =>
	readFileSync(new URL(`../../../main/resources/agent/${name}.md`, import.meta.url), "utf8");

function section(text: string, heading: string): string {
	const start = text.indexOf(`## ${heading}\n`);
	assert.ok(start !== -1, heading);
	const end = text.indexOf("\n## ", start + 1);
	return text.slice(start, end === -1 ? undefined : end).trim();
}

const shared = prompt("feedback-style");
const publicPlacement = section(prompt("review-composer"), "Where each point goes");
const privateWording = section(prompt("feedback-composer"), "Wording rules for these destinations");

void test("composer voice, placement, writing contract and tool guidance match the intentional golden", () => {
	const actual = `${[
		"# Shared feedback voice",
		shared.trim(),
		"# Public placement guidance",
		publicPlacement,
		"# Public writing contract",
		WRITE_CONTRACT,
		"# Private wording guidance",
		privateWording,
		"# Public tool description",
		REVIEW_TOOL_DESCRIPTION,
	].join("\n\n")}\n`;
	assert.equal(
		actual,
		readFileSync(new URL("golden-prompts/composition-contracts.md", import.meta.url), "utf8"),
	);
});

void test("public prompts bound the whole summary and each local point without sacrificing qualifications", () => {
	for (const text of [publicPlacement, WRITE_CONTRACT, REVIEW_TOOL_DESCRIPTION]) {
		assert.match(text, /600 characters/u);
		assert.match(text, /300 characters/u);
		assert.match(text, /(?:three|3) points/u);
		assert.match(text, /(?:two|2) short sentences/u);
	}
	assert.match(WRITE_CONTRACT, /Keep the evidence and its qualifications/u);
	assert.match(publicPlacement, /withhold lesser concerns/u);
	assert.match(WRITE_CONTRACT, /does not use praise to cushion a concern/u);
});

void test("composer prompts keep internal labels and coordinates out of delivered words", () => {
	for (const text of [shared, publicPlacement, WRITE_CONTRACT, privateWording]) {
		assert.match(text, /(?:without|no|No|Do not (?:put|write)) headings/u);
		for (const label of ["Situation:", "Capability:", "Evidence:"]) {
			assert.ok(text.includes(label), `${label} must be identified as an internal label`);
		}
		assert.match(text, /description’s closing line/u);
		assert.match(text, /description L(?:2|16)/u);
	}
	assert.match(privateWording, /Keep title, body and nextStep separate/u);
	assert.match(privateWording, /Conversation notes stay structured for the mentor/u);
});
