import { describe, expect, it } from "vitest";

// Source of truth for every Markdown file we ship. Each is imported as a
// raw string so the test runs inside jsdom (no node:fs) and any
// addition/removal of a layer will trigger a type error here first.
import disclaimerImprint from "../../public/legal/_disclaimer/imprint.md?raw";
import disclaimerPrivacy from "../../public/legal/_disclaimer/privacy.md?raw";
import tumaetImprint from "../../public/legal/profiles/tumaet/imprint.md?raw";
import tumaetPrivacy from "../../public/legal/profiles/tumaet/privacy.md?raw";

const DISCLAIMER = {
	"imprint.md": disclaimerImprint,
	"privacy.md": disclaimerPrivacy,
};

const TUMAET_PROFILE = {
	"imprint.md": tumaetImprint,
	"privacy.md": tumaetPrivacy,
};

// The disclaimer is allowed to *name* TUM only to say "this deployment is
// *not* TUM". Operator-identity markers (address, DPO email, VAT ID, named
// individuals) must not appear there — those strings would otherwise mislead
// visitors into believing TUM is the controller.
const TUMAET_OPERATOR_IDENTITY_MARKERS = [
	"85748 Garching",
	"Boltzmannstraße 3",
	"Prof. Dr. Stephan Krusche",
	"ls1.admin@in.tum.de",
	"beauftragter@datenschutz.tum.de",
	"Arcisstraße 21",
	"DE811193231",
];

describe("legal content layout", () => {
	it("ships imprint and privacy for the disclaimer", () => {
		for (const [name, body] of Object.entries(DISCLAIMER)) {
			expect(body.length, name).toBeGreaterThan(100);
		}
	});

	// Byte-count checks prove *something* shipped but not that the fallback
	// text carries the statutory basis visitors and auditors are looking for.
	// Pin the actual obligation markers so any future simplification that
	// drops them fails loudly.
	it("disclaimer imprint cites § 5 DDG as the triggering obligation", () => {
		expect(disclaimerImprint).toContain("§ 5 DDG");
	});

	it("disclaimer privacy cites Art. 13 and Art. 14 GDPR and names the controller", () => {
		expect(disclaimerPrivacy).toContain("Art. 13");
		expect(disclaimerPrivacy).toContain("Art. 14");
		expect(disclaimerPrivacy).toContain("Art. 4(7)");
	});

	it("ships imprint and privacy for the tumaet profile", () => {
		for (const [name, body] of Object.entries(TUMAET_PROFILE)) {
			expect(body.length, name).toBeGreaterThan(500);
		}
	});

	it("built-in disclaimer never presents TUM as the operator", () => {
		for (const [file, body] of Object.entries(DISCLAIMER)) {
			for (const marker of TUMAET_OPERATOR_IDENTITY_MARKERS) {
				expect({ file, marker, found: body.includes(marker) }).toEqual({
					file,
					marker,
					found: false,
				});
			}
		}
	});

	it("tumaet profile contains the canonical operator identity", () => {
		expect(tumaetPrivacy).toContain("Technical University of Munich");
		expect(tumaetPrivacy).toContain("ls1.admin@in.tum.de");
		expect(tumaetPrivacy).toContain("85748 Garching");
	});

	it("disclaimer points operators at the admin docs", () => {
		for (const [file, body] of Object.entries(DISCLAIMER)) {
			expect({ file, linked: body.includes("docs/admin/legal-pages") }).toEqual({
				file,
				linked: true,
			});
		}
	});
});
