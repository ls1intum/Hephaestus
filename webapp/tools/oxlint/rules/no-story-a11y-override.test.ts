import { ruleTester } from "../rule-tester.ts";
import { noStoryA11yOverride } from "./no-story-a11y-override.ts";

ruleTester.run("no-story-a11y-override", noStoryA11yOverride, {
	valid: [
		"const meta = { component: Button, parameters: { layout: 'centered' } } satisfies Meta<typeof Button>;",
		"const meta = { component: Button } satisfies Meta<typeof Button>;",
		"export const Wide: Story = { parameters: { viewport: { defaultViewport: 'reflow' } } };",
		// Not a `parameters` object, so not the accessibility check being reconfigured.
		"const axe = { a11y: { test: 'off' } };",
		// A computed key names whatever the expression evaluates to, which is not this property.
		"export const Wide: Story = { parameters: { [key]: { test: 'off' } } };",
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
	],
});
