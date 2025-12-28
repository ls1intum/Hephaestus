#!/usr/bin/env node
import { promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '..');
const targetDir = path.join(
  repoRoot,
  'server',
  'application-server',
  'src',
  'main',
  'java',
  'de',
  'tum',
  'in',
  'www1',
  'hephaestus',
  'intelligenceservice',
);

const replacementText =
  'The version of the OpenAPI document is defined in server/intelligence-service/openapi.yaml.';

async function walk(dir) {
  const entries = await fs.readdir(dir, { withFileTypes: true });
  const files = await Promise.all(
    entries.map(async (entry) => {
      const fullPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        return walk(fullPath);
      }
      if (entry.isFile() && entry.name.endsWith('.java')) {
        return [fullPath];
      }
      return [];
    }),
  );
  return files.flat();
}

/**
 * Adds @SuppressWarnings("unused") annotation to the class to suppress unused import warnings.
 * This is necessary because the OpenAPI generator produces imports that may not be used in every file.
 */
function addSuppressWarnings(content) {
  // Add @SuppressWarnings("unused") before the class declaration if not already present
  if (content.includes('@SuppressWarnings')) {
    return content;
  }
  // Match class/enum/interface declarations preceded by @jakarta.annotation.Generated
  // and add @SuppressWarnings before them
  const classPattern = /(@jakarta\.annotation\.Generated\([^)]+\)\s*\n)(public\s+(?:class|enum|interface)\s+)/g;
  return content.replace(classPattern, '$1@SuppressWarnings("unused")\n$2');
}

async function processFile(filePath) {
  let content = await fs.readFile(filePath, 'utf8');
  let changed = false;

  // Update version documentation
  if (content.includes('The version of the OpenAPI document:')) {
    const updated = content.replace(
      /^\s*\* The version of the OpenAPI document.*$/gm,
      ` * ${replacementText}`,
    );
    if (updated !== content) {
      content = updated;
      changed = true;
    }
  }

  // Add @SuppressWarnings to suppress unused import warnings
  const withSuppressWarnings = addSuppressWarnings(content);
  if (withSuppressWarnings !== content) {
    content = withSuppressWarnings;
    changed = true;
  }

  if (changed) {
    await fs.writeFile(filePath, content, 'utf8');
  }
  return changed;
}

async function main() {
  try {
    await fs.access(targetDir);
  } catch (error) {
    if (error && error.code === 'ENOENT') {
      console.warn(`Target directory not found: ${targetDir}`);
      return;
    }
    throw error;
  }

  const javaFiles = await walk(targetDir);
  let changedCount = 0;

  for (const file of javaFiles) {
    if (await processFile(file)) {
      changedCount += 1;
    }
  }

  console.log(`Processed ${javaFiles.length} Java files. Updated ${changedCount} files.`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
