import { ruleTester } from "../rule-tester.ts";
import { typedStoryMeta } from "./typed-story-meta.ts";

ruleTester.run("typed-story-meta", typedStoryMeta, {
	valid: [
		"const meta = { component: Button } satisfies Meta<typeof Button>;",
		"const meta = { title: 'Icons/Brand' } satisfies Meta;",
		"const meta = { parameters: { docs: { description: { component: 'All icons.' } } } } satisfies Meta;",
		"const meta = { ...base } satisfies Meta;",
		"const story = { component: Button } satisfies StoryObj;",
		// The annotation spelling, which 11 story files use.
		"const meta: Meta<typeof Button> = { component: Button };",
		"const meta: Meta = { title: 'Icons/Brand' };",
		"const meta: StoryObj = { component: Button };",
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
	],
});
