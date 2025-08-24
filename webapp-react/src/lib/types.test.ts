import { describe, expect, it } from "vitest";
import { makeArtifactId, parseArtifactId, type ArtifactId } from "./types";

describe("artifact id helpers", () => {
  it("makeArtifactId composes kind and payload", () => {
    const id = makeArtifactId("text", "abc123") as ArtifactId<"text">;
    expect(id).toBe("text:abc123");
  });

  it("parseArtifactId splits into kind and payload", () => {
    const { kind, payload } = parseArtifactId("text:abc123");
    expect(kind).toBe("text");
    expect(payload).toBe("abc123");
  });

  it("parseArtifactId handles invalid inputs", () => {
    expect(parseArtifactId(undefined).kind).toBeNull();
    expect(parseArtifactId("").payload).toBeNull();
  });
});
