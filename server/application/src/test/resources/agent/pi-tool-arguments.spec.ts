import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";

import { prepareResultArguments } from "../../../main/resources/agent/pi-tool-arguments.ts";

const changeCitation = {
	sourceKind: "scm.pull-request.diff",
	sourcePath: "context/change.json",
	path: "src/Auth.java",
	side: "NEW",
	startLine: 10,
	endLine: 12,
	quote: "insecure();",
};
const treeCitation = {
	sourceKind: "scm.repository.tree",
	sourcePath: "repos/reviewed/.git/HEAD",
	path: "src/Checked.java",
	revision: "a".repeat(40),
	startLine: 2,
	endLine: 2,
	quote: "checked(token);",
};
const problem = {
	severity: "MAJOR",
	summary: "Login calls insecure()",
	explanation: "The added line calls insecure() where the criteria ask for checked().",
	citations: [changeCitation],
	basis: { type: "introduced", head: 0, explanation: "The change adds the call." },
};
const question = {
	openQuestion: "Does the caller check the token first?",
	wouldSettleIt: "The caller of login().",
	citation: treeCitation,
};
/** A submitted result in the form submit_result declares: nothing for the preparation to repair. */
const result = {
	outcome: "NOT_MET",
	summary: "Added authentication call is insecure",
	explanation: "The changed authentication code calls insecure().",
	citations: [treeCitation],
	problems: [problem],
	example: { citation: treeCitation, note: "checked() guards the token here." },
	evidence: { openQuestions: [question] },
};

void test("a result in its declared form is kept as it is, and preparing it again changes nothing", () => {
	const prepared = prepareResultArguments(result);
	assert.deepEqual(prepared, result);
	assert.deepEqual(prepareResultArguments(prepared), prepared);
});

void test("JSON-encoded lists and objects are decoded at every level, while the root stays typed", () => {
	const encoded = {
		...result,
		citations: JSON.stringify([treeCitation]),
		problems: JSON.stringify([
			{
				...problem,
				citations: JSON.stringify([changeCitation]),
				basis: JSON.stringify(problem.basis),
			},
		]),
		example: JSON.stringify({ ...result.example, citation: JSON.stringify(treeCitation) }),
		evidence: JSON.stringify({ openQuestions: JSON.stringify([question]) }),
	};
	assert.deepEqual(prepareResultArguments(encoded), result);
	assert.equal(typeof encoded.evidence, "string", "the input is not changed");
});

void test("one object sent for a list is a list of it", () => {
	assert.deepEqual(
		prepareResultArguments({
			...result,
			citations: treeCitation,
			problems: { ...problem, citations: changeCitation },
			evidence: { openQuestions: question },
		}),
		result,
	);
});

void test("line coordinates copied from a numbered view are read as numbers, in every citation", () => {
	for (const startLine of ["10", "L10", "[L10]", " [L10] "]) {
		const numbered = { ...changeCitation, startLine, endLine: "[L12]" };
		const tree = { ...treeCitation, startLine: "[L2]", endLine: "2" };
		assert.deepEqual(
			prepareResultArguments({
				...result,
				citations: [tree],
				problems: [{ ...problem, citations: [numbered] }],
				example: { ...result.example, citation: tree },
				evidence: { openQuestions: [{ ...question, citation: tree }] },
			}),
			result,
			startLine,
		);
	}
});

void test("only decimal line coordinates are read; any other start line is left for validation", () => {
	for (const startLine of [
		"E10",
		"[10]",
		"L10]",
		"[L10",
		"1e1",
		"10.5",
		"-10",
		"10-12",
		"line 10",
		"9007199254740993",
		"[L9007199254740993]",
		null,
	]) {
		const input = { ...result, citations: [{ ...treeCitation, startLine }] };
		assert.deepEqual(prepareResultArguments(input), input, String(startLine));
	}
});

void test("an end line that is no line coordinate is refused", () => {
	assert.throws(
		() => prepareResultArguments({ ...result, citations: [{ ...treeCitation, endLine: "end" }] }),
		/citation endLine must be a line coordinate or null/u,
	);
	assert.deepEqual(
		prepareResultArguments({ ...result, citations: [{ ...treeCitation, endLine: null }] }),
		{ ...result, citations: [{ ...treeCitation, endLine: null }] },
	);
});

void test("the indices of a problem's basis are read from their digits", () => {
	const basis = {
		type: "worsened",
		base: " 1 ",
		head: "0",
		explanation: "The change adds a second call.",
	};
	const duty = {
		type: "duty",
		criterionQuote: "use checked()",
		event: "0",
		explanation: "The duty applies.",
	};
	const [worsened, owed] = [basis, duty].map((sent) => {
		const prepared = prepareResultArguments({ ...result, problems: [{ ...problem, basis: sent }] });
		const problems: unknown = prepared.problems;
		assert.ok(Array.isArray(problems));
		const first: unknown = problems[0];
		assert.ok(typeof first === "object" && first !== null);
		const sentBasis: unknown = Reflect.get(first, "basis");
		return sentBasis;
	});
	assert.deepEqual(worsened, { ...basis, base: 1, head: 0 });
	assert.deepEqual(owed, { ...duty, event: 0 });
	// What is not the digits of an index stays for validation to refuse.
	const odd = { type: "introduced", head: "first", explanation: "x" };
	assert.deepEqual(prepareResultArguments({ ...result, problems: [{ ...problem, basis: odd }] }), {
		...result,
		problems: [{ ...problem, basis: odd }],
	});
});

void test("citations sent under evidence are kept, repaired, for the normalizer to move to the result", () => {
	const prepared = prepareResultArguments({
		...result,
		evidence: { citations: JSON.stringify({ ...treeCitation, startLine: "[L2]" }) },
	});
	assert.deepEqual(prepared.evidence, { citations: [treeCitation] });
});

void test("an open question without a citation is kept as it is", () => {
	const unlocated = { openQuestion: "Is login() reachable?", wouldSettleIt: "A caller." };
	assert.deepEqual(
		prepareResultArguments({ ...result, evidence: { openQuestions: [unlocated] } }),
		{ ...result, evidence: { openQuestions: [unlocated] } },
	);
});

void test("a side, revision or quote that is not text is refused, wherever the citation is", () => {
	const places = {
		citation: (cited: object) => ({ ...result, citations: [cited] }),
		"problem citation": (cited: object) => ({
			...result,
			problems: [{ ...problem, citations: [cited] }],
		}),
		"example citation": (cited: object) => ({
			...result,
			example: { ...result.example, citation: cited },
		}),
		"open question citation": (cited: object) => ({
			...result,
			evidence: { openQuestions: [{ ...question, citation: cited }] },
		}),
		"evidence citation": (cited: object) => ({ ...result, evidence: { citations: [cited] } }),
	};
	for (const [where, place] of Object.entries(places)) {
		for (const field of ["side", "revision", "quote"]) {
			assert.throws(
				() => prepareResultArguments(place({ ...changeCitation, [field]: 42 })),
				new RegExp(`^Error: ${where} ${field} must be a string or null$`, "u"),
				`${where} ${field}`,
			);
		}
		for (const field of ["side", "revision"]) {
			assert.throws(
				() => prepareResultArguments(place({ ...changeCitation, [field]: " " })),
				new RegExp(`^Error: ${where} ${field} must be nonempty or null$`, "u"),
				`${where} ${field}`,
			);
		}
	}
	// An empty quote is text: validation decides whether it quotes the line.
	assert.doesNotThrow(() =>
		prepareResultArguments({ ...result, citations: [{ ...treeCitation, quote: "" }] }),
	);
});

void test("a warrant that is not an object is refused; an absent one is not", () => {
	for (const branch of ["search", "inapplicability"]) {
		assert.throws(
			() => prepareResultArguments({ ...result, evidence: { [branch]: "the diff" } }),
			new RegExp(`evidence\\.${branch} must be an object or null`, "u"),
		);
		assert.throws(() => prepareResultArguments({ ...result, evidence: { [branch]: [] } }));
		assert.deepEqual(prepareResultArguments({ ...result, evidence: { [branch]: null } }), {
			...result,
			evidence: { [branch]: null },
		});
	}
});

void test("malformed containers and invalid claims are left for validation to refuse", () => {
	for (const evidence of ["{not json}", "null", "[]", "42", [], { openQuestions: "not JSON" }]) {
		const input = { ...result, outcome: "INVALID", evidence };
		assert.deepEqual(prepareResultArguments(input), input, JSON.stringify(evidence));
	}
	for (const citations of ["not JSON", "42", 42]) {
		const input = { ...result, citations };
		assert.deepEqual(prepareResultArguments(input), input, JSON.stringify(citations));
	}
	const invented = {
		...changeCitation,
		path: "../task.json",
		quote: "invented();",
		side: "INVALID",
		endLine: 99_999,
	};
	assert.deepEqual(prepareResultArguments({ ...result, citations: [invented] }), {
		...result,
		citations: [invented],
	});
});

void test("arguments that are not one result object become an empty object, which the required fields refuse", () => {
	for (const args of [null, 42, [result], JSON.stringify(result), JSON.stringify([result])]) {
		assert.deepEqual(prepareResultArguments(args), {});
	}
});

void test("review prompts and bundled practices do not require write or edit tools", () => {
	for (const path of [
		"agent/pi-orchestrator.md",
		"agent/feedback-composer.md",
		"practices/default-catalog.json",
	]) {
		const text = readFileSync(new URL(`../../../main/resources/${path}`, import.meta.url), "utf8");
		assert.doesNotMatch(
			text,
			/`(?:write|edit)`|tools\.(?:write|edit)\(|\b(?:write|edit) tool\b/u,
			path,
		);
	}
});
