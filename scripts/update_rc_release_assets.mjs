#!/usr/bin/env node
/**
 * Update RC release notes to be cumulative since last stable and maintain a single GitHub Discussion for the current RC cycle.
 *
 * Inputs via env (provided by semantic-release):
 * - GITHUB_TOKEN
 * - GITHUB_REPOSITORY (owner/repo)
 * - GITHUB_REF_NAME (branch)
 * - SEMREL_NEXT_VERSION (optional fallback from semantic-release variable ${nextRelease.version} passed by caller)
 * - SEMREL_NEXT_NOTES (optional fallback from semantic-release variable ${nextRelease.notes} passed by caller)
 */

import { execSync } from 'node:child_process';
// Use native fetch (Node 18+)

function run(cmd) {
  return execSync(cmd, { encoding: 'utf8' }).trim();
}

function isRc(version) {
  return /-rc\./.test(version);
}

async function main() {
  const token = process.env.GITHUB_TOKEN;
  const repoFull = process.env.GITHUB_REPOSITORY;
  if (!token || !repoFull) {
    console.error('Missing GITHUB_TOKEN or GITHUB_REPOSITORY');
    process.exit(0); // non-fatal for local runs
  }
  const [owner, repo] = repoFull.split('/');

  async function gh(path, { method = 'GET', body } = {}) {
    const res = await fetch(`https://api.github.com${path}`, {
      method,
      headers: {
        Accept: 'application/vnd.github+json',
        Authorization: `Bearer ${token}`,
        'X-GitHub-Api-Version': '2022-11-28',
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    if (!res.ok) {
      const txt = await res.text();
      throw new Error(`${method} ${path} failed: ${res.status} ${txt}`);
    }
    return res.json();
  }

  // Determine next version from args or env
  const versionArg = process.argv[2];
  const nextVersion = versionArg || process.env.SEMREL_NEXT_VERSION;
  const defaultNotes = process.env.SEMREL_NEXT_NOTES || '';
  if (!nextVersion) {
    console.error('No next version provided');
    process.exit(0);
  }

  // Find last stable tag
  let lastStableTag = '';
  try {
    const tags = run('git tag --sort=-v:refname').split('\n');
    lastStableTag = tags.find((t) => /^v\d+\.\d+\.\d+$/.test(t)) || '';
  } catch {}

  // Compute range for cumulative notes when RC
  // For RCs we will generate cumulative release notes since last stable via GitHub API

  // Get release corresponding to tag vX.Y.Z[-rc.N] just created or to update latest RC
  const tagName = `v${nextVersion}`;
  // Fetch releases
  const rels = await gh(`/repos/${owner}/${repo}/releases?per_page=100`);
  const existing = rels.find((r) => r.tag_name === tagName);

  // Build notes using GitHub auto-generated notes for consistency
  const branch = process.env.GITHUB_REF_NAME || (isRc(nextVersion) ? 'develop' : 'main');
  let body = defaultNotes || (existing?.body ?? '');
  try {
    const baseTag = lastStableTag || undefined;
    const notesResp = await gh(`/repos/${owner}/${repo}/releases/generate-notes`, {
      method: 'POST',
      body: {
        tag_name: `v${nextVersion}`,
        previous_tag_name: baseTag,
        target_commitish: branch,
      },
    });
    if (isRc(nextVersion)) {
      const header = baseTag
        ? `This release candidate aggregates all changes since ${baseTag}.\n\n`
        : 'This release candidate aggregates changes since the first commit.\n\n';
      body = `${header}${notesResp.body}`;
    } else {
      body = notesResp.body;
    }
  } catch (e) {
    // Fallback to default body
    // eslint-disable-next-line no-console
    console.warn('Falling back to default generated notes:', e.message);
  }

  if (existing) {
    await gh(`/repos/${owner}/${repo}/releases/${existing.id}`, {
      method: 'PATCH',
      body: { body },
    });
  }

  // Maintain a single discussion for the current RC cycle
  if (isRc(nextVersion)) {
  const category = 'Announcements';
    // Find existing open discussion for current major.minor patch-free cycle
    const baseVersion = nextVersion.replace(/-rc\..*$/, '');
    const desiredTitle = `Release Candidate: v${baseVersion}`;
    // Search discussions (GraphQL for category filtering would be ideal; REST lists all)
  const discussions = await gh(`/repos/${owner}/${repo}/discussions?per_page=100`);
  const existingRc = discussions.find(
      (d) => d.title === desiredTitle && d.category?.name === category
    );

    const note = `Updated to v${nextVersion}. See GitHub release notes for details.`;

    if (existingRc) {
      // Add a comment instead of creating a new discussion
      await gh(`/repos/${owner}/${repo}/discussions/${existingRc.number}/comments`, {
        method: 'POST',
        body: { body: note },
      });
    } else {
      // Create a new discussion in category, if available
      const cats = await gh(`/repos/${owner}/${repo}/discussions/categories`);
      const cat = cats.find((c) => c.name === category) || cats[0];
      await gh(`/repos/${owner}/${repo}/discussions`, {
        method: 'POST',
        body: {
          category_id: cat.id,
          title: desiredTitle,
          body: `Tracking thread for the upcoming release v${baseVersion}.\n\n${note}`,
        },
      });
    }
  }
  else {
    // On stable release, add a final comment to the matching RC thread if present
    const category = 'Announcements';
    const baseVersion = nextVersion; // stable version
    const desiredTitle = `Release Candidate: v${baseVersion}`;
    try {
      const discussions = await gh(`/repos/${owner}/${repo}/discussions?per_page=100`);
      const existingRc = discussions.find(
        (d) => d.title === desiredTitle && d.category?.name === category
      );
      if (existingRc) {
        const note = `Final release v${nextVersion} is published. Closing out the RC cycle.`;
        await gh(`/repos/${owner}/${repo}/discussions/${existingRc.number}/comments`, {
          method: 'POST',
          body: { body: note },
        });
      }
    } catch {}
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(0); // do not fail release when discussion maintenance errors
});
