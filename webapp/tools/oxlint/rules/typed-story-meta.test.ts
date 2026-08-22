import { ruleTester } from "../rule-tester.ts";
import { typedStoryMeta } from "./typed-story-meta.ts";

ruleTester.run("typed-story-meta", typedStoryMeta, {
	valid: [
		"const meta = { component: Button } satisfies Meta<typeof Button>;",
		"const meta = { title: 'Icons/Brand' } satisfies Meta;",
		"const meta = { parameters: { docs: { description: { component: 'All icons.' } } } } satisfies Meta;",
		"const meta = { ...base } satisfies Meta;",
		"const story = { component: Button } satisfies StoryObj;",
		// The annotation spelling.
		"const meta: Meta<typeof Button> = { component: Button };",
		"const meta: Meta = { title: 'Icons/Brand' };",
		"const meta: StoryObj = { component: Button };",
		// An untyped object that is not a `meta` belongs to whoever declared it.
		"const preset = { component: Button };",
		"const meta = { title: 'Icons/Brand' };",
		"const widths = [40, 80] as const;",
	],
	invalid: [
		{
			code: "const meta = { title: 'Button', component: Button } satisfies Meta;",
			errors: [{ messageId: "untyped", line: 1, column: 14, endColumn: 52 }],
		},
		{
			code: "const meta = { 'component': Button } satisfies Meta;",
			errors: [{ messageId: "untyped" }],
		},
		{
			code: "const meta: Meta = { title: 'Button', component: Button };",
			errors: [{ messageId: "untyped" }],
		},
		{
			code: "const meta = { component: Button } as Meta<typeof Button>;",
			errors: [{ messageId: "asserted", line: 1, column: 39, endColumn: 58 }],
		},
		{
			// Even without a `component`, the assertion is what stops the check.
			code: "const meta = { title: 'Icons/Brand' } as Meta;",
			errors: [{ messageId: "asserted" }],
		},
		{
			code: "const meta = { component: Button };",
			errors: [{ messageId: "unchecked", line: 1, column: 14, endColumn: 35 }],
		},
		{
			code: "const meta = { component: Button } satisfies Meta<any>;",
			errors: [{ messageId: "anyArgument", line: 1, column: 46, endColumn: 55 }],
		},
		{
			code: "const meta: Meta<any> = { component: Button };",
			errors: [{ messageId: "anyArgument" }],
		},
	],
});
