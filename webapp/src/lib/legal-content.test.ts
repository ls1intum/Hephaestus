import { describe, expect, it } from "vitest";

// Source of truth for every Markdown file we ship. Each is imported as a
// raw string so the test runs inside jsdom (no node:fs) and any
// addition/removal of a layer will trigger a type error here first.
import disclaimerImprint from "../../public/legal/_disclaimer/imprint.md?raw";
import disclaimerPrivacy from "../../public/legal/_disclaimer/privacy.md?raw";
import tumImprint from "../../public/legal/profiles/tum/imprint.md?raw";
import tumPrivacy from "../../public/legal/profiles/tum/privacy.md?raw";

const DISCLAIMER = {
	"imprint.md": disclaimerImprint,
	"privacy.md": disclaimerPrivacy,
};

const TUM_PROFILE = {
	"imprint.md": tumImprint,
	"privacy.md": tumPrivacy,
};

// The disclaimer is allowed to *name* TUM only to say "this deployment is
// *not* TUM". Operator-identity markers (address, DPO email, VAT ID, named
// individuals) must not appear there — those strings would otherwise mislead
// visitors into believing TUM is the controller.
const TUM_OPERATOR_IDENTITY_MARKERS = [
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

	it("ships imprint and privacy for the TUM profile", () => {
		for (const [name, body] of Object.entries(TUM_PROFILE)) {
			expect(body.length, name).toBeGreaterThan(500);
		}
	});

	it("built-in disclaimer never presents TUM as the operator", () => {
		for (const [file, body] of Object.entries(DISCLAIMER)) {
			for (const marker of TUM_OPERATOR_IDENTITY_MARKERS) {
				expect({ file, marker, found: body.includes(marker) }).toEqual({
					file,
					marker,
					found: false,
				});
			}
		}
	});

	it("TUM profile contains the canonical operator identity", () => {
		expect(tumPrivacy).toContain("Technical University of Munich");
		expect(tumPrivacy).toContain("ls1.admin@in.tum.de");
		expect(tumPrivacy).toContain("85748 Garching");
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
