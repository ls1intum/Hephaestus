import { afterEach, describe, expect, it } from "bun:test";
import { mkdtemp, mkdir, rm } from "node:fs/promises";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { findFiles, grep } from "./grep";

const tempDirs: string[] = [];

async function createTempDir(): Promise<string> {
	const dir = await mkdtemp(join(tmpdir(), "grep helper "));
	tempDirs.push(dir);
	return dir;
}

afterEach(async () => {
	await Promise.all(tempDirs.splice(0).map((dir) => rm(dir, { recursive: true, force: true })));
});

describe("grep", () => {
	it("treats fixed-string patterns literally without shell interpretation", async () => {
		const dir = await createTempDir();
		const nestedDir = join(dir, "nested dir");
		await mkdir(nestedDir, { recursive: true });
		await Bun.write(
			join(nestedDir, "example.ts"),
			"const marker = \"literal $(echo nope) 'quotes'\";\n",
		);

		const matches = await grep("literal $(echo nope) 'quotes'", dir, {
			fixedString: true,
			glob: "**/*.ts",
		});

		expect(matches).toHaveLength(1);
		expect(matches[0]?.file).toBe("nested dir/example.ts");
	});

	it("enforces maxResults globally across files", async () => {
		const dir = await createTempDir();
		await Bun.write(join(dir, "one.txt"), "needle\nneedle\n");
		await Bun.write(join(dir, "two.txt"), "needle\nneedle\n");
		await Bun.write(join(dir, "three.txt"), "needle\nneedle\n");

		const matches = await grep("needle", dir, {
			fixedString: true,
			maxResults: 2,
		});

		expect(matches).toHaveLength(2);
		expect(matches.every((match) => match.content === "needle")).toBe(true);
	});

	it("applies path-aware glob filters instead of basename-only includes", async () => {
		const dir = await createTempDir();
		const nestedDir = join(dir, "src", "nested");
		await mkdir(nestedDir, { recursive: true });
		await Bun.write(join(nestedDir, "match.ts"), "needle\n");
		await Bun.write(join(nestedDir, "skip.js"), "needle\n");

		const matches = await grep("needle", dir, {
			fixedString: true,
			glob: "src/**/*.ts",
		});

		expect(matches).toHaveLength(1);
		expect(matches[0]?.file).toBe("src/nested/match.ts");
	});

	it("auto-expands basename-only globs to recursive matching", async () => {
		const dir = await createTempDir();
		await mkdir(join(dir, "src", "Views"), { recursive: true });
		await Bun.write(join(dir, "src", "Views", "ContentView.swift"), "print(\"hello\")\n");
		await Bun.write(join(dir, "RootFile.swift"), "print(\"root\")\n");

		const matches = await grep("print", dir, {
			fixedString: true,
			glob: "*.swift",
		});

		expect(matches).toHaveLength(2);
		const files = matches.map((m) => m.file).sort();
		expect(files).toContain("RootFile.swift");
		expect(files).toContain("src/Views/ContentView.swift");
	});

	it("finds extension matches without shelling out and skips ignored paths", async () => {
		const dir = await createTempDir();
		await mkdir(join(dir, "src", "nested"), { recursive: true });
		await mkdir(join(dir, ".hidden"), { recursive: true });
		await mkdir(join(dir, "node_modules", "pkg"), { recursive: true });
		await mkdir(join(dir, ".build"), { recursive: true });

		await Bun.write(join(dir, "src", "nested", "match.swift"), "struct Match {}\n");
		await Bun.write(join(dir, ".hidden", "hidden.swift"), "struct Hidden {}\n");
		await Bun.write(join(dir, "node_modules", "pkg", "dep.swift"), "struct Dep {}\n");
		await Bun.write(join(dir, ".build", "generated.swift"), "struct Generated {}\n");

		const files = await findFiles(dir, "swift");

		expect(files).toHaveLength(1);
		expect(files[0]).toBe(join(dir, "src", "nested", "match.swift"));
	});
});