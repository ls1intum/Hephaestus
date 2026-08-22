/**
 * jsdom ships no types, and `@types/jsdom` describes it in terms of the DOM lib, which these
 * Node-only scripts deliberately do not load — pulling it in would put `window` and `document` in
 * scope for every script here.
 *
 * So this declares the slice `check-mermaid-diagrams.ts` uses: a document object model handed
 * straight to mermaid, which is the only thing that looks inside it.
 */
declare module "jsdom" {
	export class JSDOM {
		constructor(html: string, options?: { pretendToBeVisual?: boolean });
		readonly window: { readonly document: object };
	}
}
