// Hephaestus Pi provider — Anthropic Messages surface.
//
// Sibling of provider-openai.ts; only the `api` field differs. See that file's header for
// the runtime contract (env vars, jiti loader, server/application-server/agent-extensions
// workspace owns the typecheck).
import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";

export default function (pi: ExtensionAPI) {
    const baseUrl = process.env.PI_HEPHAESTUS_BASE_URL;
    const modelId = process.env.PI_HEPHAESTUS_MODEL;
    if (!baseUrl || !modelId) {
        throw new Error("hephaestus provider needs PI_HEPHAESTUS_BASE_URL + PI_HEPHAESTUS_MODEL");
    }
    pi.registerProvider("hephaestus", {
        name: "Hephaestus Gateway",
        baseUrl,
        apiKey: "PI_HEPHAESTUS_API_KEY",
        authHeader: true,
        api: "anthropic-messages",
        models: [
            {
                id: modelId,
                name: modelId,
                reasoning: false,
                input: ["text"],
                cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
                contextWindow: 131072,
                maxTokens: 4096,
            },
        ],
    });
}
