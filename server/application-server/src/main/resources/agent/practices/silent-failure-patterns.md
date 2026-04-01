# Silent Failure Patterns
**Category:** Error Handling

**Scope:** Errors swallowed without user-visible feedback. When an operation fails, the user must see something — not just a console log.

## Positive Signals
- `catch` block sets a user-visible error state (`errorMessage = "..."`) AND optionally logs
- Error propagated via `throws` to a caller that handles it
- `try?` used only in safe contexts (see exclusions)

## Negative Signals
- **Empty catch block**: `do { try ... } catch { }` — no statements or only a comment
- **catch-only-print**: catch block calls only `print(error)` with no UI state update
- **`try? modelContext.save()`**: silently discards whether user data was persisted
- **`try? await` on primary network call** with no error/fallback UI — user sees nothing on failure
- **catch with Logger/os_log only**: helps debugging but user gets no indication of failure
- **`try?` in `guard let ... else { return [] }`** on network calls with no logging
- **Save-before-mutate ordering**: `modelContext.save()` called BEFORE the state mutation it should persist — captures pre-mutation state

## Exclusions — Do NOT Flag
- `try? Task.sleep(...)` — idiomatic Swift, no failure consequence
- `try?` inside `Decodable init` for optional fields with `??` fallback
- `catch { fatalError(...) }` on `ModelContainer` init — belongs to fatal-error-crash
- catch block that sets `errorMessage` AND logs — this IS the correct pattern
- catch-rethrow or `throws` propagation
- `(try? context.fetch(request)) ?? []` for read-only queries with fallback
- `try?` in `#Preview` / sample data / test files
- Do not flag the same pattern more than twice per file

## Severity
- **CRITICAL**: Never
- **MAJOR**: Empty catch, catch-only-print, `try?` on save, `try?` on primary network call without fallback, save-before-mutate
- **MINOR**: Catch with Logger-only (no UI update), `try?` in guard-let with silent empty return
