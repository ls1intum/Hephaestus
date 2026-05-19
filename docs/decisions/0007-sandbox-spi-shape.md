# ADR 0007: Sandbox SPI shape — sealed VolumeMount + typed NetworkPolicy

**Status:** Accepted
**Date:** 2026-05-20
**Authors:** Server foundations epic (#1097)

## Context

The sandbox SPI (`SandboxSpec`, `NetworkPolicy`, `InteractiveSandboxSpec`) needs to
support multiple adapters over time: today's Docker tar-injection adapter,
tomorrow's Kubernetes adapter, and possibly Firecracker / microVM for hostile code
(Anthropic moved Claude Code execution to microVM in January 2026).

The existing `Map<String, String> volumeMounts` (host path → container path) can't
express:

- Ephemeral scratch space with size limits (EmptyDir)
- Config files vs. secret files (different file modes; secrets need redacted logs)
- Read-only flags on host directory mounts
- Anything other than the host-path semantic

`NetworkPolicy` originally had `String llmProxyUrl` with no validation. Adding a
future `gitProxyUrl` field would mean another breaking change to ~48 callsites.

## Decision drivers

- Reserve the right abstractions for K8s / microVM adapters without forcing them today
- YAGNI-honest: declare the four variants but don't implement what nothing uses
- SecretMount needs first-class log-leak prevention (redacted toString + ArchUnit rule)
- Backward compatibility for the 48 existing `NetworkPolicy` and ~30 `SandboxSpec`
  callsites (don't break them on the way to a better shape)

## Considered options

1. **Sealed `VolumeMount` with 4 records; only `HostPathMount` implemented in
   Docker** — type system reserves the shape; future K8s adapter fills in the
   blanks via compiler-enforced exhaustive switch.
2. **Sealed `VolumeMount` with all 4 implemented in Docker today** — argued by an
   earlier principal-engineer review; rejected as 3 hours of plumbing for code with
   no current consumer.
3. **Keep `Map<String, String>`; defer the shape entirely** — easiest today, but
   then every future adapter epic carries the migration cost.

## Decision

Option 1 for `VolumeMount`. Declare the four variants as records implementing
`sealed interface VolumeMount`:

- `HostPathMount(Path hostPath, Path containerPath, boolean readOnly)`
- `EmptyDirMount(Path containerPath, long sizeLimitBytes)`
- `ConfigMapMount(Map<String, String> data, Path containerPath)`
- `SecretMount(Map<String, String> data, Path containerPath)` — redacting `toString()`

The Docker adapter still operates on `SandboxSpec.volumeMounts() : Map<String, String>`
in this epic — that's equivalent to a `List<HostPathMount>`. Migrating the
`SandboxSpec` field to `List<VolumeMount>` is ~30 callsites of mechanical sweep,
filed as a follow-up.

`NetworkPolicy` gains `gitProxyUrl` as a 5th field plus absolute-http(s) validation
in the compact constructor. The 4-arg constructor is kept as a delegate so all 48
existing callsites compile unchanged. `llmProxyUrl` stays `String` (not
`java.net.URI`) — typing migration is also a follow-up; the constructor validation
catches the same bad inputs either way.

`SecretMountLeakTest` (ArchUnit) forbids `agent.*` from passing `SecretMount` to
slf4j / `java.util.logging` APIs. Defense-in-depth alongside redacted `toString()`.

**Explicitly NOT shipped: `WorkerAuthProvider` sealed SPI.** Premature abstraction
without a real BYO-runner consumer. The actual auth design (two-stage identity
bootstrap, runner registration tokens, scoped credentials per industry research)
lands with the BYO-runner epic when real requirements exist.

## Consequences

- Future K8s adapter implements `EmptyDir`/`ConfigMap`/`SecretMount` and the
  compile-time exhaustive switch in `DockerSandboxAdapter` forces an explicit
  decision rather than a silent fall-through.
- Adding a 5th `VolumeMount` variant fails the build in every adapter until
  handled — that's the value of sealed.
- `SecretMount.toString()` redaction + `SecretMountLeakTest` together close the
  credential-in-logs leak class for the type.
- 48 `NetworkPolicy` callsites untouched today; future `gitProxyUrl` consumers
  pass the 5th arg explicitly.
- Document explicitly that `WorkerAuthProvider` is intentional non-scope.

## Revisit trigger

K8s adapter epic lands and implements 1+ of the unimplemented `VolumeMount`
variants; or the BYO-runner epic surfaces a concrete auth model that needs an SPI;
or `NetworkPolicy` consumers hit a wall with `String llmProxyUrl` that `URI` typing
would have caught earlier.
