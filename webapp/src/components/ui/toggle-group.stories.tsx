import type { Meta, StoryObj } from "@storybook/react-vite";
import { CodeIcon, TextIcon } from "lucide-react";
import { expect, within } from "storybook/test";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";

/**
 * A regression suite for one upstream defect, not a design-system entry for the primitive.
 *
 * Base UI's `ToggleGroup` renders `role="group"` and then delegates to `CompositeRoot`, whose
 * `useCompositeRoot` always contributes `aria-orientation` — the only value that suppresses it,
 * `'both'`, is unreachable from the public API. ARIA 1.2 lists `aria-orientation` as used in
 * `scrollbar`, `select`, `separator`, `slider`, `tablist` and `toolbar`; `group` is not one of them,
 * so every group the kit rendered failed axe's `aria-allowed-attr` — and Radix shipped the same bug
 * (radix-ui/primitives#964).
 *
 * The wrapper passes `aria-orientation={undefined}`. These stories exist to prove that this is
 * actually enough: Base UI's `mergeProps` assigns every own key of the later object, `undefined`
 * included, rather than skipping it, and React omits an attribute whose value is `undefined`. That
 * is an implementation detail of a dependency, so it is asserted rather than assumed.
 *
 * @see https://www.w3.org/TR/wai-aria-1.2/#group
 */
const meta = {
	title: "Tests/Toggle group orientation",
	component: ToggleGroup,
	parameters: { layout: "padded", chromatic: { disableSnapshot: true } },
	tags: ["autodocs"],
} satisfies Meta<typeof ToggleGroup>;

export default meta;
type Story = StoryObj<typeof meta>;

function ViewSwitch({ orientation }: { orientation?: "horizontal" | "vertical" }) {
	return (
		<ToggleGroup
			orientation={orientation}
			variant="outline"
			size="sm"
			defaultValue={["rendered"]}
			aria-label="How to show the feedback"
		>
			<ToggleGroupItem value="rendered">
				<TextIcon aria-hidden />
				Rendered
			</ToggleGroupItem>
			<ToggleGroupItem value="source">
				<CodeIcon aria-hidden />
				Source
			</ToggleGroupItem>
		</ToggleGroup>
	);
}

/**
 * The default axis. The group keeps `role="group"` and carries no `aria-orientation`, while the
 * visual axis stays readable from `data-orientation` for styling.
 */
export const HorizontalHasNoAriaOrientation: Story = {
	render: () => <ViewSwitch />,
	play: async ({ canvasElement }) => {
		const group = within(canvasElement).getByRole("group", {
			name: "How to show the feedback",
		});
		await expect(group).not.toHaveAttribute("aria-orientation");
		await expect(group).toHaveAttribute("data-orientation", "horizontal");
	},
};

/**
 * The axis a caller actually changes. `orientation` reaches the primitive — so `CompositeRoot`
 * moves focus with Up/Down rather than Left/Right — without the attribute coming back.
 */
export const VerticalHasNoAriaOrientation: Story = {
	render: () => <ViewSwitch orientation="vertical" />,
	play: async ({ canvasElement }) => {
		const group = within(canvasElement).getByRole("group", {
			name: "How to show the feedback",
		});
		await expect(group).not.toHaveAttribute("aria-orientation");
		await expect(group).toHaveAttribute("data-orientation", "vertical");
	},
};

/**
 * A caller who wants the attribute — on a role that ARIA allows it on — can still set it. The
 * suppression sits before the prop spread, so it is a default and not a lock.
 */
export const ExplicitAriaOrientationSurvives: Story = {
	render: () => (
		<ToggleGroup
			role="toolbar"
			aria-orientation="horizontal"
			variant="outline"
			size="sm"
			defaultValue={["rendered"]}
			aria-label="How to show the feedback"
		>
			<ToggleGroupItem value="rendered">Rendered</ToggleGroupItem>
			<ToggleGroupItem value="source">Source</ToggleGroupItem>
		</ToggleGroup>
	),
	play: async ({ canvasElement }) => {
		const group = within(canvasElement).getByRole("toolbar", {
			name: "How to show the feedback",
		});
		await expect(group).toHaveAttribute("aria-orientation", "horizontal");
	},
};
