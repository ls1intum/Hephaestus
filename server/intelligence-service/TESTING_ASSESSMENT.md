# 🔥 BRUTAL ASSESSMENT: intelligence-service Testing & Implementation

**Date:** 2025-12-16  
**Assessor:** Principal Engineer (Ego-Free, Cut-Throat)  
**Reference Standard:** AI SDK v6 (17k+ LOC in stream-text.test.ts alone)

---

## GRADING RUBRIC (A+ is 95-100)

| Category | Weight | AI SDK Standard | Our Score | Grade |
|----------|--------|-----------------|-----------|-------|
| **Test Value & Coverage** | 25% | Tests behavior, not implementation | 58/100 | **D+** |
| **Type Safety** | 20% | Zero `any`, full inference | 45/100 | **F** |
| **Mock Quality** | 15% | Precise stream simulation, call tracking | 55/100 | **D** |
| **Test Isolation** | 10% | No shared state, deterministic | 70/100 | **C** |
| **Assertion Precision** | 10% | Inline snapshots, exact expectations | 40/100 | **F** |
| **Code Bloat** | 10% | Zero duplication, minimal setup | 50/100 | **D** |
| **Error Path Coverage** | 10% | Every failure mode tested | 20/100 | **F** |

**OVERALL: 49/100 = F**

---

## CATEGORY BREAKDOWNS

### 1. Test Value & Coverage: D+ (58/100)

**What AI SDK Does:**
```typescript
// AI SDK tests STREAMING BEHAVIOR with history tracking
it('should update the messages during the streaming', async () => {
  expect(chat.history).toMatchInlineSnapshot(`...`);
});
```

**What We Do:**
```typescript
// We test CRUD. Boring. Low value.
it("should save a user message with text parts", async () => {
  expect(messages).toHaveLength(1);
  expect(messages[0]?.role).toBe("user");
});
```

**Problems:**
- ❌ ZERO streaming behavior tests
- ❌ ZERO stream consumption tests 
- ❌ ZERO tool execution tests
- ❌ ZERO onFinish callback tests
- ❌ ZERO abort/disconnect handling
- ❌ Testing CRUD is kindergarten-level value

**What's Missing (Critical):**
1. Test `streamText` produces correct stream parts
2. Test `createUIMessageStream` assembles messages correctly
3. Test tool calls invoke and return properly
4. Test abort mid-stream behavior
5. Test error recovery in stream

---

### 2. Type Safety: F (45/100)

**What AI SDK Does:**
```typescript
// Precise types from provider package
import { LanguageModelV3, LanguageModelV3StreamPart } from '@ai-sdk/provider';

const testUsage: LanguageModelV3Usage = { ... }; // Exact type

function createTestModel(): LanguageModelV3 { ... } // Returns actual interface
```

**What We Do:**
```typescript
// Cast to unknown and pray
return { ...options } as LanguageModel; // Line 89 - TYPE ASSERTION CANCER

type StreamPart = 
  | { type: "stream-start"; warnings?: unknown[] }  // unknown[] = GARBAGE
```

**Problems:**
- ❌ `as LanguageModel` cast without implementing interface
- ❌ Using `unknown[]` as a cop-out
- ❌ No proper type imports from `@ai-sdk/provider`
- ❌ `Promise.reject(new Error(...))` without proper typing
- ❌ Test fixtures use raw object literals instead of typed builders

---

### 3. Mock Quality: D (55/100)

**What AI SDK Does:**
```typescript
// MockLanguageModelV3 tracks ALL calls
doStreamCalls: Parameters<LanguageModelV3['doStream']>[0][] = [];

// Tests verify what was passed to the model
expect(prompt).toStrictEqual([
  { role: 'user', content: [{ type: 'text', text: 'test-input' }], ... }
]);
```

**What We Do:**
```typescript
// Mock produces data but DOESN'T TRACK CALLS
doStream = () => Promise.resolve({ stream: ... })

// We never verify:
// - What prompt was sent
// - What tools were passed
// - How many times it was called
```

**Problems:**
- ❌ No call tracking (`doStreamCalls`, `doGenerateCalls`)
- ❌ No request body verification
- ❌ Can't test prompt construction
- ❌ Can't test tool configuration
- ❌ No `mockId()` for deterministic IDs

---

### 4. Assertion Precision: F (40/100)

**What AI SDK Does:**
```typescript
// INLINE SNAPSHOTS - exact, reviewable, captures evolution
expect(chat.messages).toMatchInlineSnapshot(`
  [
    { "id": "id-0", "role": "user", "parts": [...] },
    { "id": "id-1", "role": "assistant", "parts": [...] }
  ]
`);
```

**What We Do:**
```typescript
// WEAK ASSERTIONS - could pass with wrong data
expect(messages).toHaveLength(1);
expect(messages[0]?.role).toBe("user");
expect(messages[0]?.parts).toHaveLength(1);
// What about the CONTENT? The structure? The metadata?
```

**Problems:**
- ❌ Zero inline snapshots
- ❌ `.toHaveLength()` without verifying content
- ❌ Optional chaining `?.` hides potential null bugs
- ❌ No assertion on complete object shape
- ❌ Assertions are too shallow to catch regressions

---

### 5. Error Path Coverage: F (20/100)

**What AI SDK Does:**
```typescript
describe('send handle a disconnected response stream', () => { ... });
describe('send handle a stop and an aborted response stream', () => { ... });
// Tests isAbort, isDisconnect, isError flags
expect(letOnFinishArgs[0].isDisconnect).toBe(true);
expect(letOnFinishArgs[0].isError).toBe(true);
```

**What We Do:**
```typescript
// ONE error test in entire mock module
error(message: string): LanguageModel {
  return createMockLanguageModel({
    doStream: () => Promise.reject(new Error(message)),
  });
}
// NEVER USED IN ANY TEST
```

**Problems:**
- ❌ No disconnect handling test
- ❌ No abort handling test
- ❌ No stream error test
- ❌ No database failure test
- ❌ No timeout test
- ❌ No malformed response test

---

### 6. Code Bloat: D (50/100)

**What AI SDK Does:**
```typescript
// Reusable test utilities
const defaultSettings = () => ({ ... }) as const;

// One-liner model creation
const model = createTestModel({ stream: convertArrayToReadableStream([...]) });
```

**What We Do:**
```typescript
// MASSIVE duplication in every test
const threadId = testUuid();
createdThreadIds.push(threadId);

await createThread({
  id: threadId,
  workspaceId: fixtures.workspace.id,
});
// Repeated 15+ times
```

**Problems:**
- ❌ Same 4-line thread creation pattern repeated everywhere
- ❌ Same message creation pattern repeated everywhere
- ❌ No test builders or factories
- ❌ Cleanup logic spread across multiple afterEach hooks
- ❌ `convertArrayToReadableStream` duplicated from AI SDK instead of imported

---

## IMMEDIATE FIXES REQUIRED

### Fix #1: Kill Type Assertions (HIGH PRIORITY)

```typescript
// BEFORE (garbage)
return { ...options } as LanguageModel;

// AFTER (proper)
import type { LanguageModelV3 } from '@ai-sdk/provider';

class MockLanguageModel implements LanguageModelV3 {
  specificationVersion = 'v3' as const;
  provider: string;
  modelId: string;
  doStreamCalls: Parameters<LanguageModelV3['doStream']>[0][] = [];
  // ... implement interface properly
}
```

### Fix #2: Add Call Tracking (HIGH PRIORITY)

```typescript
// Track every invocation
doStream: async (options) => {
  this.doStreamCalls.push(options);
  return { stream: ... };
}

// Verify in tests
expect(model.doStreamCalls[0].prompt).toMatchInlineSnapshot(`...`);
```

### Fix #3: Use Inline Snapshots (MEDIUM PRIORITY)

```typescript
// BEFORE
expect(messages).toHaveLength(1);
expect(messages[0]?.role).toBe("user");

// AFTER
expect(messages).toMatchInlineSnapshot(`
  [
    {
      "id": "...",
      "role": "user",
      "parts": [{ "type": "text", "content": { "text": "Hello" } }],
      ...
    }
  ]
`);
```

### Fix #4: Create Test Builders (MEDIUM PRIORITY)

```typescript
// Factory pattern
const createTestThread = async (overrides = {}) => {
  const id = testUuid();
  await createThread({
    id,
    workspaceId: fixtures.workspace.id,
    ...overrides,
  });
  createdThreadIds.push(id);
  return id;
};

// Usage
const threadId = await createTestThread({ title: 'Test' });
```

### Fix #5: Add Streaming Tests (CRITICAL)

```typescript
describe('streamText integration', () => {
  it('should produce text deltas in correct order', async () => {
    const model = mockModels.streamingText(['Hello', ', ', 'world!']);
    const result = streamText({ model, prompt: 'test' });
    
    const chunks = await convertAsyncIterableToArray(result.textStream);
    expect(chunks).toEqual(['Hello', ', ', 'world!']);
  });
  
  it('should call onFinish with complete message', async () => {
    let finishArgs: unknown;
    // ... test actual finish callback
  });
});
```

### Fix #6: Add Error Path Tests (CRITICAL)

```typescript
describe('error handling', () => {
  it('should handle stream disconnect gracefully', async () => { ... });
  it('should handle abort signal correctly', async () => { ... });
  it('should persist partial message on error', async () => { ... });
  it('should not corrupt thread state on db failure', async () => { ... });
});
```

---

## TARGET STATE (A+ Grade)

| Metric | Current | Target |
|--------|---------|--------|
| Test count | 59 | 150+ |
| Streaming tests | 0 | 30+ |
| Error path tests | 0 | 20+ |
| Inline snapshots | 0 | 50+ |
| Type assertions | 5+ | 0 |
| Test utilities duplication | HIGH | NONE |
| Call tracking in mocks | NO | YES |

---

## VERDICT

This testing suite is **amateur hour**. It tests that data gets stored and retrieved - congratulations, PostgreSQL works. 

What it DOESN'T test:
- The actual LLM streaming integration
- Tool execution flow
- Error recovery
- Abort handling
- Stream transformation correctness
- Prompt construction
- Message assembly during streaming
- OnFinish callbacks with correct data

The mock LanguageModel is a toy. It produces streams but doesn't track what was passed to it. You can't verify the system is using the model correctly.

The AI SDK has **17,000 lines** in a single test file for `streamText`. We have **296 lines** total in our integration tests.

**Path to A+:** Delete what doesn't add value. Rewrite mocks with proper interfaces and call tracking. Add streaming behavior tests. Use inline snapshots everywhere. Cover every error path. Zero type assertions.

---

**Current Grade: F (49/100)**  
**Target Grade: A+ (95/100)**  
**Gap: 46 points**
