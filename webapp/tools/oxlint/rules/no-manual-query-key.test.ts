import { ruleTester } from "../rule-tester.ts";
import { noManualQueryKey } from "./no-manual-query-key.ts";

ruleTester.run("no-manual-query-key", noManualQueryKey, {
	valid: [
		"queryClient.invalidateQueries({ queryKey: getThingQueryKey({ path: { id } }) });",
		"queryClient.invalidateQueries({ queryKey: thingQueryOptions.queryKey });",
		"queryClient.getQueryCache().find({ queryKey: key, exact: true });",
		"const wrapper = { queryKey };",
		"useQuery({ ...getThingOptions({ path: { id } }), enabled: true });",
		// A key computed from an expression names whatever that evaluates to, which is unreadable here.
		"const dynamic = { [key]: [1, 2] };",
	],
	invalid: [
		{
			code: 'queryClient.invalidateQueries({ queryKey: [{ tags: ["Leaderboard"], path: { workspaceSlug } }] });',
			errors: [{ messageId: "handWritten", line: 1, column: 43 }],
		},
		{
			code: 'useQuery({ queryKey: ["things", id], queryFn: fetchThings });',
			errors: [{ messageId: "handWritten", line: 1, column: 22 }],
		},
		{
			code: 'const opts = { "queryKey": [] };',
			errors: [{ messageId: "handWritten", line: 1, column: 28 }],
		},
		{
			// A key computed from a literal names exactly that literal.
			code: "const opts = { [`queryKey`]: [] };",
			errors: [{ messageId: "handWritten" }],
		},
		{
			// `as const` states the array's type; the array is still this file claiming the key shape.
			code: 'useQuery({ queryKey: ["things", id] as const, queryFn: fetchThings });',
			errors: [{ messageId: "handWritten", line: 1, column: 22 }],
		},
		{
			code: 'useQuery({ queryKey: ["things"] satisfies readonly unknown[] });',
			errors: [{ messageId: "handWritten", line: 1, column: 22 }],
		},
	],
});
