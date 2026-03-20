import type { Meta, StoryObj } from "@storybook/react";
import { FeatureGateDisplay } from "./FeatureGate";

/**
 * Conditionally renders children based on a feature flag's enabled state.
 * Shows loading content while flags are being fetched, fallback content when
 * the flag is disabled, and children when enabled.
 *
 * The stories demonstrate the presentational inner component directly to avoid
 * requiring auth/query context.
 */
const meta = {
	component: FeatureGateDisplay,
	parameters: {
		layout: "centered",
		docs: {
			description: {
				component: "Declarative component for gating UI behind feature flags.",
			},
		},
	},
	tags: ["autodocs"],
	argTypes: {
		enabled: {
			description: "Whether the feature flag is enabled",
			control: "boolean",
		},
		isLoading: {
			description: "Whether the flag state is still loading",
			control: "boolean",
		},
		children: {
			description: "Content shown when the flag is enabled",
			control: false,
		},
		fallback: {
			description: "Content shown when the flag is disabled",
			control: false,
		},
		loading: {
			description: "Content shown while loading",
			control: false,
		},
	},
} satisfies Meta<typeof FeatureGateDisplay>;

export default meta;
type Story = StoryObj<typeof meta>;

const EnabledContent = () => (
	<div className="rounded-lg border border-green-200 bg-green-50 p-4 text-sm text-green-800 dark:border-green-800 dark:bg-green-950 dark:text-green-200">
		Feature is enabled — this content is visible.
	</div>
);

const DisabledContent = () => (
	<div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200">
		Feature is disabled — showing fallback content.
	</div>
);

const LoadingContent = () => (
	<div className="flex items-center gap-2 rounded-lg border border-border bg-muted p-4 text-sm text-muted-foreground">
		<div className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
		Loading feature flags...
	</div>
);

/**
 * Flag is enabled — children are rendered.
 */
export const Enabled: Story = {
	args: {
		enabled: true,
		isLoading: false,
		children: <EnabledContent />,
		fallback: <DisabledContent />,
		loading: <LoadingContent />,
	},
};

/**
 * Flag is disabled — fallback content is shown.
 */
export const Disabled: Story = {
	args: {
		enabled: false,
		isLoading: false,
		children: <EnabledContent />,
		fallback: <DisabledContent />,
		loading: <LoadingContent />,
	},
};

/**
 * Flags are loading — loading indicator is shown.
 */
export const IsLoading: Story = {
	args: {
		enabled: false,
		isLoading: true,
		children: <EnabledContent />,
		fallback: <DisabledContent />,
		loading: <LoadingContent />,
	},
};

/**
 * Disabled without fallback — renders nothing (empty space).
 */
export const DisabledNoFallback: Story = {
	args: {
		enabled: false,
		isLoading: false,
		children: <EnabledContent />,
	},
};
