import { describe, expect, it } from "vitest";
import { multiValue, narrowToEnum, nonEmpty } from "./search-params";

describe("multiValue", () => {
	it("normalizes one query parameter to the repeated-parameter shape", () => {
		expect(multiValue.parse("CREATED")).toEqual(["CREATED"]);
	});

	it("drops invalid input", () => {
		expect(multiValue.parse(42)).toBeUndefined();
	});
});

describe("nonEmpty", () => {
	it("omits an empty selection", () => {
		expect(nonEmpty([])).toBeUndefined();
	});

	it("keeps selected values", () => {
		expect(nonEmpty(["CREATED"])).toEqual(["CREATED"]);
	});
});

describe("narrowToEnum", () => {
	const allowed = ["CREATED", "UPDATED"] as const;

	it("drops values the API would reject", () => {
		expect(narrowToEnum(["CREATED", "RETIRED"], allowed)).toEqual(["CREATED"]);
	});

	it("omits a selection when every value is unknown", () => {
		expect(narrowToEnum(["RETIRED"], allowed)).toBeUndefined();
	});

	it("omits an empty selection", () => {
		expect(narrowToEnum([], allowed)).toBeUndefined();
	});
});
