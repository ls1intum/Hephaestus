# Prompt Management System

This module provides the prompt loader, types, and Langfuse integration.

## Architecture

Prompts are **colocated with their features**, not centralized here.

```
src/
├── mentor/
│   ├── chat.prompt.ts      # ← Mentor system prompt
│   └── tools/              # Tool definitions (colocated with factories)
│       ├── reviews.tool.ts     # Factory + Langfuse definition
│       ├── documents.tool.ts   # Factory + Langfuse definition
│       └── ...
├── detector/
│   └── bad-practice.prompt.ts  # ← Detector prompt
└── prompts/                # This module (loader + types)
    ├── index.ts            # Re-exports prompts from features
    ├── types.ts            # Type definitions
    ├── loader.ts           # Langfuse integration + fallback
    └── README.md           # You are here

scripts/
├── prompts.ts              # CLI for sync with Langfuse
└── ast-utils.ts            # AST-based code manipulation
```

## Why This Architecture?

1. **Feature colocation**: Prompts live with the code that uses them
2. **Single import**: `import { loadPrompt, mentorChatPrompt } from "@/prompts"`
3. **Easy discovery**: Find the prompt next to its handler
4. **Tool colocation**: Tool factories AND Langfuse definitions in same file

## Quick Start

### Loading a Prompt

```typescript
import { loadPrompt, mentorChatPrompt } from "@/prompts";

const prompt = await loadPrompt(mentorChatPrompt);
const compiled = prompt.compile({ firstName: "Alice" });
```

### CLI Commands

```bash
npm run prompts          # Check sync status (default)
npm run prompts status   # Same as above
npm run prompts push     # Push local → Langfuse
npm run prompts pull     # Pull Langfuse → local
npm run prompts diff     # Show differences
npm run prompts list     # List discovered prompts
```

## Key Concepts

### PromptDefinition

The source of truth for a prompt. Stored in git, synced to Langfuse.

```typescript
export const myPrompt: PromptDefinition = {
  name: "my-prompt",       // Unique ID in Langfuse
  type: "text",            // "text" or "chat"
  prompt: `Hello {{name}}`,// Template with variables
  labels: ["production"],  // Langfuse labels
  config: {                // Model configuration
    temperature: 0.7,
  },
};
```

### Template Variables

Use Mustache-style `{{variable}}` syntax:

```typescript
const compiled = prompt.compile({ name: "Alice" });
// "Hello Alice"
```

### Tool Definitions

For prompts with tools, define them in `_meta.toolsDir`:

```typescript
export const chatPrompt: PromptDefinition = {
  name: "mentor-chat",
  type: "text",
  prompt: `...`,
  _meta: {
    toolsDir: "mentor/tools",  // Relative to src/
  },
  config: {
    tools: mentorToolDefinitions,
  },
};
```

Each `*.tool.ts` file exports a Langfuse-compatible definition:

```typescript
// src/mentor/tools/reviews.tool.ts
export const { definition: getReviewsGivenDefinition } = defineToolMeta({
  name: "getReviewsGiven",
  description: `...`,
  inputSchema: z.object({ ... }),
});
```

## Langfuse Integration

### How It Works

1. **Local first**: Prompts are defined in TypeScript files
2. **Sync to Langfuse**: `npm run prompts push` uploads to Langfuse
3. **Pull from Langfuse**: `npm run prompts pull` downloads changes
4. **Runtime fallback**: If Langfuse is unavailable, uses local definition

### Benefits

- **Version control**: Prompts are in git
- **A/B testing**: Use Langfuse labels for experiments
- **No code deploy**: Update prompts without redeploying
- **Graceful degradation**: Works offline

## Adding a New Prompt

1. Create `src/<feature>/<name>.prompt.ts` (colocated with your feature):

```typescript
import type { PromptDefinition } from "@/prompts/types";

export const myNewPrompt: PromptDefinition = {
  name: "my-new-prompt",
  type: "text",
  prompt: `Your prompt here...`,
  labels: ["production"],
};
```

2. Re-export from `src/prompts/index.ts`:

```typescript
export { myNewPrompt } from "@/<feature>/<name>.prompt";
```

3. Push to Langfuse:

```bash
npm run prompts push
```

## File Naming Conventions

| Pattern | Purpose |
|---------|---------|
| `*.prompt.ts` | Prompt definitions (in feature folders) |
| `*.tool.ts` | Tool definitions with Zod schemas |
| `types.ts` | Type definitions |
| `loader.ts` | Runtime loading logic |
| `index.ts` | Public API exports |

## Testing

```bash
npm test -- test/prompts  # Run prompt tests
```

## Troubleshooting

### "Could not find prompt property"

The AST parser expects:
```typescript
export const myPrompt: PromptDefinition = {
  prompt: `...`,  // Must be a template literal with backticks
};
```

### "Tool not found in Langfuse"

1. Ensure the tool exports a `*Definition` constant
2. Check the `_meta.toolsDir` path is correct
3. Run `npm run prompts push` to sync

### Cache Issues

Clear the runtime cache:
```typescript
import { clearPromptCache } from "@/prompts";
clearPromptCache();
```
