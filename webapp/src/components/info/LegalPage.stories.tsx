import type { Meta, StoryObj } from "@storybook/react";
import {
	LEGAL_PAGE_TITLES,
	type LegalPageId,
	type ResolvedLegalContent,
	type resolveLegalContent,
} from "@/lib/legal";
import { LegalPage } from "./LegalPage";

const TUM_IMPRINT = `Information in accordance with § 5 DDG — German Digital Services Act.

## Publisher

Technical University of Munich  \nArcisstraße 21  \n80333 Munich, Germany

## Responsible for Content

Prof. Dr. Stephan Krusche  \nApplied Education Technologies (AET)  \nBoltzmannstraße 3  \n85748 Garching bei München, Germany
`;

const TUM_PRIVACY = `# Privacy Statement

The Technical University of Munich (TUM), through AET, operates the Hephaestus platform.

## 1. Controller

Technical University of Munich  \nArcisstraße 21, 80333 Munich, Germany

## 7. Third-Country Transfers

Recipients in the U.S. are DPF-certified under Art. 45(3) GDPR; SCCs under Art. 46(2)(c) GDPR serve as a fall-back.
`;

const DISCLAIMER_IMPRINT = `# Imprint not configured

This Hephaestus instance has been deployed without a legal profile.

The operator is legally required under § 5 DDG to identify itself and cannot rely on this fallback.
`;

const DISCLAIMER_PRIVACY = `# Privacy statement not configured

This Hephaestus instance has been deployed without a legal profile. The operator remains the controller under Art. 4(7) GDPR and owes a transparent privacy statement (Art. 13 / 14 GDPR).
`;

type FixtureKey = "tum" | "disclaimer";
const FIXTURES: Record<FixtureKey, Record<LegalPageId, string>> = {
	tum: {
		imprint: TUM_IMPRINT,
		privacy: TUM_PRIVACY,
	},
	disclaimer: {
		imprint: DISCLAIMER_IMPRINT,
		privacy: DISCLAIMER_PRIVACY,
	},
};

function makeResolver(key: FixtureKey): typeof resolveLegalContent {
	return async (page): Promise<ResolvedLegalContent> => ({
		markdown: FIXTURES[key][page],
		source: key === "tum" ? "profile" : "disclaimer",
		profile: key === "tum" ? "tum" : "",
	});
}

/**
 * Renders an imprint or privacy page from Markdown resolved through the
 * operator-override → bundled-profile → disclaimer cascade. Stories inject a
 * fixture resolver so each variant mounts without network I/O.
 */
const meta = {
	component: LegalPage,
	tags: ["autodocs"],
	parameters: {
		layout: "padded",
		docs: {
			description: {
				component:
					"Legal-page shell used by `/imprint` and `/privacy`. Resolves the Markdown body through a three-layer cascade and surfaces a disclaimer banner when the shipped fallback is active.",
			},
		},
	},
	argTypes: {
		page: {
			description: "Which legal page to render.",
			control: { type: "inline-radio" },
			options: ["imprint", "privacy"] satisfies LegalPageId[],
		},
		title: {
			description: "Page title (shown above the Markdown body).",
			control: "text",
		},
		resolver: {
			description: "Injected async resolver (fixture in stories, real fetcher in prod).",
			control: false,
		},
		profileOverride: {
			description: "Forces a specific profile; production reads `environment.legal.profile`.",
			control: false,
		},
	},
} satisfies Meta<typeof LegalPage>;

export default meta;
type Story = StoryObj<typeof meta>;

/** TUM profile — canonical upstream imprint. */
export const TumImprint: Story = {
	args: {
		page: "imprint",
		title: LEGAL_PAGE_TITLES.imprint,
		resolver: makeResolver("tum"),
	},
};

/** TUM profile — canonical upstream privacy statement. */
export const TumPrivacy: Story = {
	args: {
		page: "privacy",
		title: LEGAL_PAGE_TITLES.privacy,
		resolver: makeResolver("tum"),
	},
};

/** Safety fallback shown when no profile is configured (imprint). */
export const DisclaimerImprint: Story = {
	args: {
		page: "imprint",
		title: LEGAL_PAGE_TITLES.imprint,
		resolver: makeResolver("disclaimer"),
	},
};

/** Safety fallback shown when no profile is configured (privacy). */
export const DisclaimerPrivacy: Story = {
	args: {
		page: "privacy",
		title: LEGAL_PAGE_TITLES.privacy,
		resolver: makeResolver("disclaimer"),
	},
};
