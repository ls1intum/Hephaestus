/** Non-browser scripts exclude DOM globals, so declare only the jsdom surface the Mermaid check uses. */
declare module "jsdom" {
	export class JSDOM {
		constructor(html: string, options?: { pretendToBeVisual?: boolean });
		readonly window: { readonly document: object };
	}
}
