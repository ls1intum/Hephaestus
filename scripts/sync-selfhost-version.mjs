// Keeps the self-hosted install's pinned release version in step with the root
// package version. Run by the `changeset:version` script (see package.json) as
// part of the Version PR, so `IMAGE_TAG` in the self-host .env.example and the
// `VERSION=` example in the install guide always match the release being cut —
// no manual bump, no drift. CI (ci-compose-validate) fails if they diverge.
import { readFileSync, writeFileSync } from "node:fs";

const version = JSON.parse(readFileSync("package.json", "utf8")).version;

const edits = [
  {
    file: "docker/self-host/.env.example",
    re: /^IMAGE_TAG=.*$/m,
    line: `IMAGE_TAG=${version}`,
  },
  {
    file: "docs/admin/install.mdx",
    re: /^VERSION=\S+(\s+# the release you are installing.*)$/m,
    line: `VERSION=${version}$1`,
  },
];

for (const { file, re, line } of edits) {
  let text;
  try {
    text = readFileSync(file, "utf8");
  } catch {
    continue; // file may not exist in every checkout; skip quietly
  }
  if (!re.test(text)) {
    throw new Error(`sync-selfhost-version: no version literal matched in ${file}`);
  }
  writeFileSync(file, text.replace(re, line));
}

console.log(`Synced self-host version literals to ${version}`);
