import { ruleTester } from "../rule-tester.ts";
import { preferAutoStoryTitle } from "./prefer-auto-story-title.ts";

const component = "const Button = () => null;";

ruleTester.run("prefer-auto-story-title", preferAutoStoryTitle, {
	valid: [
		{
			code: `${component}\nconst meta = { component: Button } satisfies Meta<typeof Button>;`,
			filename: "webapp/src/components/ui/Button.stories.tsx",
		},
		{
			// Dropping the implementation-only `components` segment is a real sidebar relocation.
			code: `${component}\nconst meta = { title: "UI primitives/Button", component: Button } satisfies Meta<typeof Button>;`,
			filename: "webapp/src/components/ui/Button.stories.tsx",
		},
		{
			// Product surfaces can cut across the source layout.
			code: `${component}\nconst meta = { title: "Workspace admin/Practices/Review/Overview", component: Button } satisfies Meta<typeof Button>;`,
			filename: "webapp/src/components/admin/practices/review/ReviewPage.stories.tsx",
		},
		{
			// A computed title is owned by gate:story-sort, which gives the more precise diagnostic.
			code: `${component}\nconst meta = { title: prefix + "/Button", component: Button } satisfies Meta<typeof Button>;`,
			filename: "webapp/src/components/ui/Button.stories.tsx",
		},
	],
	invalid: [
		{
			code: `${component}\nconst meta = { title: "components/ui/Button", component: Button } satisfies Meta<typeof Button>;`,
			filename: "webapp/src/components/ui/Button.stories.tsx",
			errors: [{ messageId: "redundant", data: { automatic: "components/ui/Button" } }],
		},
		{
			// Sentence case and punctuation do not make the same Storybook path meaningful metadata.
			code: `${component}\nconst meta = { title: "components/UI/button", component: Button } satisfies Meta<typeof Button>;`,
			filename: "/repo/webapp/src/components/ui/Button.stories.tsx",
			cwd: "/repo",
			errors: [{ messageId: "redundant" }],
		},
	],
});
