package de.tum.in.www1.hephaestus.agent.adapter;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapter;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentAdapterRequest;
import de.tum.in.www1.hephaestus.agent.adapter.spi.AgentSandboxSpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight adapter that makes a single LLM API call instead of running an agentic loop.
 *
 * <p>Uses the same {@code node:22-slim} image as OpenCode but runs a simple Node.js script
 * that POSTs the full prompt to the LLM proxy and writes the JSON response to result.json.
 * This eliminates the multi-turn tool-use overhead of agentic frameworks, reducing execution
 * from 10+ minutes to ~30 seconds for analysis tasks where all context is inline.
 *
 * <p>Environment variables (injected by DockerSandboxAdapter for PROXY mode):
 * <ul>
 *   <li>{@code LLM_PROXY_URL} — base URL including provider path</li>
 *   <li>{@code LLM_PROXY_TOKEN} — job token for authentication</li>
 *   <li>{@code MODEL_NAME} — model identifier</li>
 * </ul>
 */
public class DirectLlmAgentAdapter implements AgentAdapter {

    /** Reuse the OpenCode image — it's node:22-slim which has everything we need. */
    static final String IMAGE = "ghcr.io/ls1intum/hephaestus/agent-opencode:latest";
    static final String OUTPUT_PATH = "/workspace/.output";

    @Override
    public AgentType agentType() {
        return AgentType.DIRECT_LLM;
    }

    @Override
    public AgentSandboxSpec buildSandboxSpec(AgentAdapterRequest request) {
        if (request.modelName() == null || request.modelName().isBlank()) {
            throw new IllegalArgumentException("modelName is required for DIRECT_LLM");
        }

        Map<String, String> env = new HashMap<>();
        env.put("MODEL_NAME", request.modelName());

        Map<String, byte[]> inputFiles = new LinkedHashMap<>();
        inputFiles.put(".prompt", request.prompt().getBytes(StandardCharsets.UTF_8));
        inputFiles.put(".call-llm.mjs", buildScript());

        // Don't redirect stdout — let it go to Docker logs for observability.
        // The script writes result.json directly to the output path.
        String command = "mkdir -p " + OUTPUT_PATH + " && node /workspace/.call-llm.mjs";

        return new AgentSandboxSpec(
            IMAGE,
            List.of("sh", "-c", command),
            env,
            inputFiles,
            OUTPUT_PATH,
            null,
            AgentAdapter.buildNetworkPolicy(request)
        );
    }

    /**
     * Build the Node.js script that makes a single HTTP POST to the LLM proxy.
     *
     * <p>The script reads the prompt from /workspace/.prompt, sends it as a single
     * user message to the chat completions endpoint, extracts the assistant's content,
     * and writes it to result.json. No streaming, no tool use, no agent loop.
     */
    private byte[] buildScript() {
        String script = """
            import { readFileSync, writeFileSync, mkdirSync } from 'fs';
            import http from 'http';
            import https from 'https';

            const prompt = readFileSync('/workspace/.prompt', 'utf8');
            const proxyUrl = process.env.LLM_PROXY_URL;
            const proxyToken = process.env.LLM_PROXY_TOKEN;
            const model = process.env.MODEL_NAME;

            if (!proxyUrl || !proxyToken || !model) {
                console.error('Missing required env vars: LLM_PROXY_URL, LLM_PROXY_TOKEN, MODEL_NAME');
                process.exit(1);
            }

            const url = new URL(proxyUrl + '/chat/completions');
            const client = url.protocol === 'https:' ? https : http;
            const body = JSON.stringify({
                model: model,
                messages: [{ role: 'user', content: prompt }],
                max_tokens: 16384,
                response_format: { type: 'json_object' }
            });

            console.log(`Calling ${url.hostname}:${url.port || (url.protocol === 'https:' ? 443 : 80)}${url.pathname}${url.search || ''}`);
            console.log(`Model: ${model}, Prompt: ${prompt.length} chars`);

            const options = {
                hostname: url.hostname,
                port: url.port || (url.protocol === 'https:' ? 443 : 80),
                path: url.pathname + (url.search || ''),
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + proxyToken,
                    'Content-Length': Buffer.byteLength(body)
                },
                timeout: 300000 // 5 min timeout for the HTTP call
            };

            const req = client.request(options, (res) => {
                let data = '';
                res.on('data', chunk => { data += chunk; });
                res.on('end', () => {
                    console.log(`Response status: ${res.statusCode}, body: ${data.length} bytes`);
                    if (res.statusCode !== 200) {
                        console.error(`LLM API error (${res.statusCode}): ${data.substring(0, 2000)}`);
                        process.exit(1);
                    }
                    try {
                        const response = JSON.parse(data);
                        const content = response.choices?.[0]?.message?.content;
                        if (!content) {
                            console.error('No content in response:', JSON.stringify(response).substring(0, 2000));
                            process.exit(1);
                        }
                        // Validate it's valid JSON, then write
                        const parsed = JSON.parse(content);
                        mkdirSync('/workspace/.output', { recursive: true });
                        writeFileSync('/workspace/.output/result.json', JSON.stringify(parsed, null, 2));
                        console.log(`Success: wrote result.json (${content.length} chars)`);

                        // Log usage if available
                        if (response.usage) {
                            console.log(`Usage: prompt=${response.usage.prompt_tokens}, completion=${response.usage.completion_tokens}, total=${response.usage.total_tokens}`);
                        }
                    } catch (err) {
                        console.error('Failed to parse response:', err.message);
                        console.error('Raw content:', data.substring(0, 3000));
                        // Try to save raw content anyway for debugging
                        try {
                            mkdirSync('/workspace/.output', { recursive: true });
                            writeFileSync('/workspace/.output/raw-response.json', data);
                        } catch (_) {}
                        process.exit(1);
                    }
                });
            });

            req.on('error', (err) => {
                console.error('HTTP request failed:', err.message);
                process.exit(1);
            });

            req.on('timeout', () => {
                console.error('HTTP request timed out after 5 minutes');
                req.destroy();
                process.exit(1);
            });

            req.write(body);
            req.end();
            """;
        return script.getBytes(StandardCharsets.UTF_8);
    }

    // Note: intentionally uses the default AgentAdapter.parseResult() implementation,
    // which reads result.json from sandbox output files. No agent-specific extraction
    // is needed because the Node.js script writes clean JSON directly to result.json.
}
