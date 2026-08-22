import { ruleTester } from "../rule-tester.ts";
import { noManualQueryKey } from "./no-manual-query-key.ts";

ruleTester.run("no-manual-query-key", noManualQueryKey, {
	valid: [
		"queryClient.invalidateQueries({ queryKey: getThingQueryKey({ path: { id } }) });",
		"queryClient.invalidateQueries({ queryKey: thingQueryOptions.queryKey });",
		"queryClient.getQueryCache().find({ queryKey: key, exact: true });",
		"const invalidate = (queryKey: readonly unknown[]) => queryClient.invalidateQueries({ queryKey });",
		"const wrapper = { queryKey };",
		"useQuery({ ...getThingOptions({ path: { id } }), enabled: true });",
		// A `queryKey`-shaped key on an unrelated object is still not an array here.
		"const meta = { queryKey: describeKey() };",
		// A computed key names whatever the expression evaluates to, which is not this property.
		"const dynamic = { [queryKey]: [1, 2] };",
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
	],
});
