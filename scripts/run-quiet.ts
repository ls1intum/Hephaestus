#!/usr/bin/env node
/**
 * Quiet runner for noisy CLI tools.
 *
 * Wraps commands to reduce output noise for AI agents and humans alike.
 * Filters out verbose logging while preserving essential information.
 *
 * Usage:
 *   node --import tsx scripts/run-quiet.ts [tool] [args...]
 *
 * Supported tools:
 *   prettier      - Filters unchanged file listings, shows only changed/errors
 *   openapi-gen   - Filters INFO logs, keeps WARN/ERROR and summary
 *
 * Examples:
 *   node --import tsx scripts/run-quiet.ts prettier --write "src/file.java"
 *   node --import tsx scripts/run-quiet.ts openapi-gen generate -i spec.yaml -g java -o out
 */

import { spawn } from "node:child_process";
import process from "node:process";

type FilterConfig = {
  command: string;
  args: string[];
  filter: (line: string) => boolean;
  transform?: (line: string) => string;
  summary?: (lines: string[], exitCode: number) => string;
};

/**
 * Prettier filter: Only show files that were actually changed or have errors.
 * Filters out "(unchanged)" lines that flood the output.
 */
function prettierFilter(line: string): boolean {
  // Always show errors and warnings
  if (line.includes("[error]") || line.includes("[warn]")) {
    return true;
  }

  // Filter out "(unchanged)" lines - this is the main noise source
  if (line.includes("(unchanged)")) {
    return false;
  }

  // Filter out "Checking formatting..." boilerplate
  if (line.trim() === "Checking formatting...") {
    return false;
  }

  // Show everything else (changed files, errors, summaries)
  return true;
}

/**
 * Transform prettier output to be more concise.
 */
function prettierTransform(line: string): string {
  // Remove timing info for cleaner output: "file.java 40ms" -> "file.java"
  // But keep it for actually changed files so we know something happened
  return line;
}

/**
 * Summary for prettier runs.
 */
function prettierSummary(lines: string[], exitCode: number): string {
  const changedFiles = lines.filter((l) => {
    // Exclude warning/error lines first to avoid false positives
    if (l.includes("[warn]") || l.includes("[error]")) {
      return false;
    }
    return (
      l.match(/\.(java|ts|tsx|js|jsx|json|css|scss|md)$/) &&
      !l.includes("(unchanged)")
    );
  });

  const warnings = lines.filter((l) => l.includes("[warn]"));
  const errors = lines.filter((l) => l.includes("[error]"));

  if (exitCode === 0) {
    if (changedFiles.length === 0) {
      return "All files formatted correctly.";
    }
    return `Formatted ${changedFiles.length} file(s).`;
  }

  if (warnings.length > 0) {
    return `${warnings.length} file(s) need formatting.`;
  }

  if (errors.length > 0) {
    return `${errors.length} error(s) found.`;
  }

  return "";
}

/**
 * OpenAPI Generator filter: Remove verbose INFO logs, keep warnings and errors.
 */
function openApiGenFilter(line: string): boolean {
  // Filter out Java deprecation warnings (sun.misc.Unsafe) - check first
  if (
    line.includes("sun.misc.Unsafe") ||
    line.includes("terminally deprecated") ||
    line.includes("Please consider reporting this to the maintainers")
  ) {
    return false;
  }

  // Filter out the donation/thanks message
  if (
    line.includes("###") ||
    line.includes("opencollective") ||
    line.includes("Thanks for using OpenAPI Generator") ||
    line.includes("Please consider donation")
  ) {
    return false;
  }

  // Filter out repetitive 3.1.0 warnings (keep only first occurrence tracked externally)
  // These are very long and appear multiple times
  if (line.includes("3.1.0 specs is in development")) {
    return false;
  }

  // Filter out routine INFO logs (the main noise source)
  if (line.includes("[main] INFO")) {
    // Keep the generator identification line only
    if (line.includes("OpenAPI Generator:")) return true;
    return false;
  }

  // Show actual errors and warnings (except filtered ones above)
  if (line.includes("[main] WARN") || line.includes("[main] ERROR")) {
    return true;
  }

  // Filter empty lines and hash decorations
  if (line.trim() === "" || line.trim().match(/^#+$/)) {
    return false;
  }

  // Keep everything else
  return true;
}

/**
 * Transform OpenAPI generator output.
 */
function openApiGenTransform(line: string): string {
  // Shorten absolute paths to relative
  const cwd = process.cwd();
  return line.replace(new RegExp(cwd + "/", "g"), "");
}

/**
 * Summary for OpenAPI generator runs.
 */
function openApiGenSummary(lines: string[], exitCode: number): string {
  // Count generated files by counting "written file" lines
  const generatedFilesCount = lines.filter((l) =>
    l.includes("written file"),
  ).length;
  if (generatedFilesCount > 0) {
    return `Generated ${generatedFilesCount} files.`;
  }

  if (exitCode === 0) {
    return "Generation completed.";
  }

  return "Generation failed.";
}

const TOOL_CONFIGS: Record<string, Omit<FilterConfig, "command" | "args">> = {
  prettier: {
    filter: prettierFilter,
    transform: prettierTransform,
    summary: prettierSummary,
  },
  "openapi-gen": {
    filter: openApiGenFilter,
    transform: openApiGenTransform,
    summary: openApiGenSummary,
  },
};

async function run(tool: string, args: string[]): Promise<number> {
  const config = TOOL_CONFIGS[tool];
  if (!config) {
    console.error(`Unknown tool: ${tool}`);
    console.error(`Supported tools: ${Object.keys(TOOL_CONFIGS).join(", ")}`);
    return 1;
  }

  // Determine actual command to run
  let command: string;
  let commandArgs: string[];

  if (tool === "prettier") {
    command = "npx";
    commandArgs = ["prettier", ...args];
  } else if (tool === "openapi-gen") {
    command = "npx";
    commandArgs = ["openapi-generator-cli", ...args];
  } else {
    command = tool;
    commandArgs = args;
  }

  return new Promise((resolve) => {
    const proc = spawn(command, commandArgs, {
      stdio: ["inherit", "pipe", "pipe"],
      shell: false,
    });

    const allLines: string[] = [];

    const processOutput = (data: Buffer, isStderr: boolean) => {
      const lines = data.toString().split("\n");
      for (const rawLine of lines) {
        if (!rawLine) continue;

        allLines.push(rawLine);

        // Apply filter
        if (!config.filter(rawLine)) {
          continue;
        }

        // Apply transform
        const line = config.transform ? config.transform(rawLine) : rawLine;

        // Output to appropriate stream
        if (isStderr) {
          console.error(line);
        } else {
          console.log(line);
        }
      }
    };

    proc.stdout.on("data", (data: Buffer) => processOutput(data, false));
    proc.stderr.on("data", (data: Buffer) => processOutput(data, true));

    proc.on("close", (code) => {
      const exitCode = code ?? 0;

      // Print summary if available
      if (config.summary) {
        const summary = config.summary(allLines, exitCode);
        if (summary) {
          console.log(summary);
        }
      }

      resolve(exitCode);
    });

    proc.on("error", (err) => {
      console.error(`Failed to start ${command}:`, err.message);
      resolve(1);
    });
  });
}

async function main(): Promise<void> {
  const args = process.argv.slice(2);

  if (args.length === 0) {
    console.error("Usage: run-quiet.ts <tool> [args...]");
    console.error("Supported tools: prettier, openapi-gen");
    process.exitCode = 1;
    return;
  }

  const [tool, ...toolArgs] = args;
  // tool is guaranteed to be defined since args.length > 0
  const exitCode = await run(tool as string, toolArgs);
  process.exitCode = exitCode;
}

main().catch((error: unknown) => {
  console.error(error);
  process.exitCode = 1;
});
