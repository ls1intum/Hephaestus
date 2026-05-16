// Hephaestus Pi provider — OpenAI Chat Completions surface.
//
// Typechecked at CI time against @earendil-works/pi-coding-agent (see the npm workspace at
// server/application-server/agent-extensions). Runtime: PiRuntimeFactory copies these bytes
// verbatim to ~/.pi/extensions/hephaestus-provider.ts inside the sandbox container, where
// Pi's jiti loader executes them at session start. ALL config flows through env vars set by
// LlmProxyAuthShell (PI_HEPHAESTUS_BASE_URL/_API_KEY/_MODEL) — never inline the model id.
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
        api: "openai-completions",
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
