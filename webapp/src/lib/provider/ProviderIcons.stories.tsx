import type { Meta, StoryObj } from "@storybook/react";
import { getPullRequestStateIcon } from "./provider-icons";
import type { ProviderType } from "./provider-terms";
import { getProviderTerms } from "./provider-terms";

/**
 * Visual reference for all provider-specific pull request / merge request icons.
 * Shows every state (open, draft, merged, closed) for each provider (GitHub, GitLab)
 * with their correct icons and color tokens.
 */
const meta = {
	title: "Provider/Icons",
	tags: ["autodocs"],
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component:
					"Gallery of all pull request / merge request state icons for GitHub and GitLab. Each provider uses its own native icons with provider-aware color tokens.",
			},
		},
	},
} satisfies Meta;

export default meta;
type Story = StoryObj<typeof meta>;

const STATES = [
	{ state: "OPEN" as const, isDraft: false, label: "Open" },
	{ state: "OPEN" as const, isDraft: true, label: "Draft" },
	{ state: "MERGED" as const, isDraft: false, label: "Merged" },
	{ state: "CLOSED" as const, isDraft: false, label: "Closed" },
];

function IconRow({ provider, size = 20 }: { provider: ProviderType; size?: number }) {
	const terms = getProviderTerms(provider);
	return (
		<div className="flex flex-col gap-3">
			<h3 className="text-sm font-semibold">
				{provider === "GITHUB" ? "GitHub" : "GitLab"} — {terms.pullRequest}
			</h3>
			<div className="flex gap-6">
				{STATES.map(({ state, isDraft, label }) => {
					const { icon: Icon, colorClass } = getPullRequestStateIcon(provider, state, isDraft);
					return (
						<div key={label} className="flex flex-col items-center gap-2">
							<div
								className={`flex items-center justify-center rounded-md border p-3 ${colorClass}`}
							>
								<Icon size={size} />
							</div>
							<span className="text-xs text-muted-foreground">{label}</span>
						</div>
					);
				})}
			</div>
		</div>
	);
}

/**
 * GitHub pull request icons in all four states: open, draft, merged, closed.
 * Uses `@primer/octicons-react` icons with GitHub color tokens.
 */
export const GitHub: Story = {
	render: () => <IconRow provider="GITHUB" />,
};

/**
 * GitLab merge request icons in all four states: open, draft, merged, closed.
 * Uses custom GitLab SVG wrappers with Pajamas color tokens.
 */
export const GitLab: Story = {
	render: () => (
		<div data-provider="gitlab">
			<IconRow provider="GITLAB" />
		</div>
	),
};

/**
 * Side-by-side comparison of GitHub and GitLab icons at the default size.
 * Highlights the visual differences between providers — different icons and
 * different color palettes (e.g. GitHub purple vs GitLab blue for merged).
 */
export const Comparison: Story = {
	render: () => (
		<div className="flex flex-col gap-8">
			<IconRow provider="GITHUB" />
			<div data-provider="gitlab">
				<IconRow provider="GITLAB" />
			</div>
		</div>
	),
	parameters: {
		docs: {
			description: {
				story:
					"Side-by-side comparison showing how the same states render differently per provider.",
			},
		},
	},
};

/**
 * Icons rendered at multiple sizes (12, 16, 20, 24, 32) for both providers.
 * Useful for verifying icon clarity at different scales.
 */
export const Sizes: Story = {
	render: () => {
		const sizes = [12, 16, 20, 24, 32];
		return (
			<div className="flex flex-col gap-8">
				{(["GITHUB", "GITLAB"] as const).map((provider) => (
					<div key={provider} data-provider={provider === "GITLAB" ? "gitlab" : undefined}>
						<h3 className="mb-3 text-sm font-semibold">
							{provider === "GITHUB" ? "GitHub" : "GitLab"}
						</h3>
						<div className="flex gap-6 items-end">
							{sizes.map((size) => {
								const { icon: Icon, colorClass } = getPullRequestStateIcon(provider, "OPEN");
								return (
									<div key={size} className="flex flex-col items-center gap-2">
										<div className={colorClass}>
											<Icon size={size} />
										</div>
										<span className="text-xs text-muted-foreground">{size}px</span>
									</div>
								);
							})}
						</div>
					</div>
				))}
			</div>
		);
	},
	parameters: {
		docs: {
			description: {
				story: "Icons at different sizes to verify rendering quality across scales.",
			},
		},
	},
};
