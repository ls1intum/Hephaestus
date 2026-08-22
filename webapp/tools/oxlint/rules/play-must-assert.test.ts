import { ruleTester } from "../rule-tester.ts";
import { playMustAssert } from "./play-must-assert.ts";

ruleTester.run("play-must-assert", playMustAssert, {
	valid: [
		"export const Open: Story = { play: async ({ canvas }) => { canvas.getByRole('dialog'); } };",
		"export const Open: Story = { play: async ({ canvas, userEvent }) => { await userEvent.click(canvas.getByRole('button')); await canvas.findByRole('dialog'); } };",
		"export const Open: Story = { play: async ({ canvas }) => { await expect(canvas.getByRole('button').disabled).toBe(true); } };",
		// The shared helpers in `src/test/` all carry the `expect` prefix.
		"export const Wide: Story = { play: async () => { await expectNoPageOverflow(); } };",
		"export const Wide: Story = { play: async ({ canvas }) => { await expectSettledVisible(canvas.getByRole('menu')); } };",
		// Scoping into a subtree still ends in a query.
		"export const Row: Story = { play: async ({ canvas }) => { within(canvas.getByRole('row')).getByText('Zeus'); } };",
		// A step wrapper does not hide what is inside it.
		"export const Flow: Story = { play: async ({ canvas, step }) => { await step('open', async () => { canvas.getByRole('dialog'); }); } };",
		// Delegating to another story's play inherits that story's assertions.
		"export const Then: Story = { play: async (context) => { await Open.play?.(context); await context.canvas.findByText('Saved'); } };",
		// A `play` that is not a function literal is somebody else's body.
		"export const Open: Story = { play: sharedPlay };",
	],
	invalid: [
		{
			code: "export const Open: Story = { play: async ({ userEvent }) => { await userEvent.click(trigger); } };",
			errors: [{ messageId: "noAssertion", line: 1, column: 30, endColumn: 34 }],
		},
		{
			code: "export const Open: Story = { play: async () => { await new Promise((resolve) => setTimeout(resolve, 100)); } };",
			errors: [{ messageId: "noAssertion" }],
		},
		{
			code: "export const Open: Story = { play({ canvas }) { canvas.querySelector('[data-slot=table]'); } };",
			errors: [{ messageId: "noAssertion" }],
		},
		{
			// A `queryBy*` returns null instead of throwing, so on its own it asserts nothing.
			code: "export const Open: Story = { play: async ({ canvas }) => { canvas.queryByRole('alert'); } };",
			errors: [{ messageId: "noAssertion" }],
		},
		{
			code: "export const Open: Story = { play: async ({ canvas }) => { await expectNoPageOverflow(); } };",
			options: [{ assertFunctionNames: ["expect", "getBy*", "**.getBy*"] }],
			errors: [{ messageId: "noAssertion" }],
		},
	],
});
