# Silent Failure Patterns
**Category:** Error Handling

## What This Practice Means
Errors must not be swallowed silently. When an operation fails, the user must receive visible feedback -- not just a console log.

## Positive Signals (-> verdict POSITIVE)
- `catch` block sets a user-visible error state (`errorMessage = "..."`) AND optionally logs
- Error propagated via `throws` to a caller that handles it
- `try?` used only in safe contexts (see exclusions below)

## Negative Signals (-> verdict NEGATIVE)
- **Empty catch block** after `do { try ... }` -- no statements or only a comment (MAJOR)
- **catch-only-print**: catch block calls only `print()`/`return` with no UI state update (MAJOR)
- **`try? modelContext.save()`** -- silently discards whether user data was persisted (MAJOR)
- **`try? await` on network/fetch** where the result is the view's primary purpose, with no error/fallback UI (MAJOR)
- **catch with Logger/os_log only** -- helps debugging but user sees nothing (MINOR)
- **`try?` in `guard let ... else { return [] }`** on URLSession with no logging or error info (MINOR)
- **Save-before-mutate ordering**: `context.save()` or `modelContext.save()` called BEFORE the state mutation it should persist, in the same closure/function — the save captures pre-mutation state (MAJOR)

## False-Positive Exclusions
- `try? Task.sleep(...)` -- always acceptable, idiomatic Swift
- `try?` inside `Decodable init` for optional/defaulted fields with `??` fallback
- `catch { fatalError(...) }` on `ModelContainer` init -- handled by fatal-error-crash practice
- catch block that sets `errorMessage` AND logs -- this is the GOOD pattern
- catch-rethrow or `throws` propagation -- acceptable
- `(try? context.fetch(request)) ?? []` for read-only queries with fallback
- `try?` in `#Preview` / sample data code -- never flag
- Test files, preview files, sample data files: skip entirely
- Do not flag the same pattern more than twice per file

## Severity Guide
- CRITICAL: never
- MAJOR: empty catch, catch-only-print, `try?` on save, `try?` on primary network call without fallback UI
- MINOR: catch with Logger-only (no UI update), `try?` in guard-let with silent empty return
