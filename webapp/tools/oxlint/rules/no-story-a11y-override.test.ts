import { ruleTester } from "../rule-tester.ts";
import { noStoryA11yOverride } from "./no-story-a11y-override.ts";

ruleTester.run("no-story-a11y-override", noStoryA11yOverride, {
	valid: [
		"const meta = { component: Button, parameters: { layout: 'centered' } } satisfies Meta<typeof Button>;",
		"const meta = { component: Button } satisfies Meta<typeof Button>;",
		// Neither a `parameters` nor a `globals` object, so not the accessibility check being set.
		"const axe = { a11y: { test: 'off' } };",
		// A key computed from an expression names whatever that evaluates to, which is unreadable here.
		"export const Wide: Story = { parameters: { [key]: { test: 'off' } } };",
		// Reading a story's parameters apart is not writing them.
		"const { parameters, globals } = context;",
		// The addon reads `ghostStories` off the globals; under `parameters` it sets nothing.
		"export const Wide: Story = { parameters: { ghostStories: true } };",
	],
	invalid: [
		{
			code: "export const Wide: Story = { parameters: { a11y: { test: 'off' } } };",
			errors: [{ messageId: "localOverride", line: 1, column: 44, endColumn: 65 }],
		},
		{
			code: "const meta = { component: Button, parameters: { a11y: { disable: true } } } satisfies Meta<typeof Button>;",
			errors: [{ messageId: "localOverride" }],
		},
		{
			code: "export const Wide: Story = { parameters: { 'a11y': { test: 'todo' } } };",
			errors: [{ messageId: "localOverride" }],
		},
		{
			// A key computed from a literal names exactly that literal.
			code: "export const Wide: Story = { parameters: { ['a11y']: { test: 'off' } } };",
			errors: [{ messageId: "localOverride" }],
		},
		{
			// `globals.a11y.manual` stops the check running just as `parameters.a11y` reconfigures it.
			code: "export const Wide: Story = { globals: { a11y: { manual: true } } };",
			errors: [{ messageId: "localOverride" }],
		},
		{
			// `globals.ghostStories` skips the check outright, without naming accessibility at all.
			code: "export const Wide: Story = { globals: { ghostStories: true } };",
			errors: [{ messageId: "localOverride" }],
		},
		{
			code: "export const Wide: Story = { parameters: { ...A11Y_OFF } };",
			errors: [{ messageId: "indirect", line: 1, column: 44, endColumn: 55 }],
		},
		{
			code: "export const Wide: Story = { parameters: sharedParameters };",
			errors: [{ messageId: "indirect" }],
		},
		{
			// Two `a11y` entries are two edits to delete, so each is reported where it stands.
			code: "export const Wide: Story = { parameters: { a11y: { test: 'off' }, ['a11y']: { disable: true } } };",
			errors: [{ messageId: "localOverride" }, { messageId: "localOverride" }],
		},
	],
});
