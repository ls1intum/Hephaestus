import { ruleTester } from "../rule-tester.ts";
import { svgNeedsAccessibleName } from "./svg-needs-accessible-name.ts";

ruleTester.run("svg-needs-accessible-name", svgNeedsAccessibleName, {
	valid: [
		'const a = <svg aria-hidden="true"><path d="M0 0" /></svg>;',
		'const b = <svg aria-label="GitLab" role="img"><path d="M0 0" /></svg>;',
		'const c = <svg aria-labelledby="t"><title id="t">Chart</title></svg>;',
		"const d = <svg><title>Sparkline</title></svg>;",
		// The bare attribute is `aria-hidden={true}`, which hides it just as the string does.
		'const e = <svg aria-hidden><path d="M0 0" /></svg>;',
		// A value the rule cannot evaluate is a decision the component makes at runtime, not an
		// omission: the attribute is written, so the author has answered the question.
		'const f = <svg aria-hidden={decorative ? "true" : undefined} aria-label={label} />;',
		// A spread may carry any of the four, and nothing here can see through it.
		"const g = <svg {...rest} />;",
		"const h = <svg viewBox=\"0 0 16 16\" {...rest}><path d='M0 0' /></svg>;",
		// Not the intrinsic element: a capitalised name is a component, which answers for itself.
		'const i = <Svg><path d="M0 0" /></Svg>;',
		"const j = <Icons.svg />;",
		// Not an svg at all.
		"const k = <div><path /></div>;",
	],
	invalid: [
		{
			code: 'const l = <svg viewBox="0 0 16 16"><path d="M0 0" /></svg>;',
			errors: [{ messageId: "unnamed", line: 1, column: 11, endColumn: 36 }],
		},
		{
			code: "const m = <svg><circle /></svg>;",
			errors: [{ messageId: "unnamed" }],
		},
		{
			// `role="img"` gives the graphic a role and no name, so a screen reader still says
			// "image" and stops. The pair below shows the fix.
			code: 'const n = <svg role="img"><path d="M0 0" /></svg>;',
			errors: [{ messageId: "unnamed" }],
		},
		{
			// `aria-hidden="false"` is the one value that puts the graphic back in the tree.
			code: 'const o = <svg aria-hidden="false"><path d="M0 0" /></svg>;',
			errors: [{ messageId: "unnamed" }],
		},
		{
			code: 'const p = <svg aria-hidden={false}><path d="M0 0" /></svg>;',
			errors: [{ messageId: "unnamed" }],
		},
		{
			// An empty name is no name: the attribute is present and computes to nothing.
			code: 'const q = <svg aria-label=""><path d="M0 0" /></svg>;',
			errors: [{ messageId: "unnamed" }],
		},
		{
			code: 'const r = <svg aria-labelledby="  "><path d="M0 0" /></svg>;',
			errors: [{ messageId: "unnamed" }],
		},
		{
			// `<desc>` is the long description, and never the accessible name.
			code: "const s = <svg><desc>A bar chart</desc></svg>;",
			errors: [{ messageId: "unnamed" }],
		},
		{
			// SVG takes the name from the element's OWN `<title>` child; one inside a `<g>` names
			// the `<g>`.
			code: "const t = <svg><g><title>Sparkline</title></g></svg>;",
			errors: [{ messageId: "unnamed" }],
		},
		{
			// Each unnamed graphic is its own edit, including a nested one.
			code: "const u = <svg><svg /></svg>;",
			errors: [{ messageId: "unnamed" }, { messageId: "unnamed" }],
		},
	],
});
