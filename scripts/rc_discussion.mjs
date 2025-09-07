#!/usr/bin/env node
// Maintains a single rolling RC discussion per upcoming release (vX.Y.Z)
// - Creates the discussion once (if missing)
// - Adds a comment for each RC (rc.1, rc.2, ...)
// - Finalizes the thread on stable release

const GQL_URL = 'https://api.github.com/graphql';

import { execSync } from 'node:child_process';

function run(cmd) {
  return execSync(cmd, { encoding: 'utf8' }).trim();
}

function isRc(version) {
  return /-rc\./.test(version);
}

async function gqlFetch(token, query, variables) {
  const res = await fetch(GQL_URL, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ query, variables }),
  });
  const json = await res.json();
  if (!res.ok || json.errors) {
    throw new Error(JSON.stringify(json.errors || { status: res.status }));
  }
  return json.data;
}

async function restJson(token, path, method = 'GET', body) {
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

async function main() {
  const token = process.env.GITHUB_TOKEN;
  const repoFull = process.env.GITHUB_REPOSITORY;
  const nextVersion = process.argv[2] || process.env.SEMREL_NEXT_VERSION;
  if (!token || !repoFull || !nextVersion) return;
  const [owner, name] = repoFull.split('/');

  // Derive base version without rc suffix
  const baseVersion = nextVersion.replace(/-rc\..*$/, '');
  const title = `Release Candidate: v${baseVersion}`;
  const today = new Date().toISOString().slice(0, 10);

  // Determine last stable tag for proper compare link and cumulative notes
  let lastStableTag = '';
  try {
    const tags = run('git tag --sort=-v:refname').split('\n');
    lastStableTag = tags.find((t) => /^v\d+\.\d+\.\d+$/.test(t)) || '';
  } catch {}

  // Fetch repo, categories, and latest discussions
  const data = await gqlFetch(
    token,
    `query RepoData($owner: String!, $name: String!) {
      repository(owner: $owner, name: $name) {
        id
        discussionCategories(first: 100) { nodes { id name } }
        discussions(first: 50, orderBy: {field: CREATED_AT, direction: DESC}) {
          nodes { id number title url category { name } }
        }
      }
    }`,
    { owner, name }
  );

  const repo = data?.repository;
  if (!repo) return;
  const categories = repo.discussionCategories?.nodes || [];
  // Prefer a neutral category if present; else first
  const preferredNames = ['Announcements', 'General'];
  const category = categories.find((c) => preferredNames.includes(c.name)) || categories[0];
  if (!category) return; // nothing to do

  const existing = (repo.discussions?.nodes || []).find((d) => d.title === title);
  const releaseUrl = `https://github.com/${owner}/${name}/releases/tag/v${nextVersion}`;
  const compareUrl = lastStableTag
    ? `https://github.com/${owner}/${name}/compare/${lastStableTag}...v${nextVersion}`
    : '';
  // Try to find previous RC for delta link
  let prevRcTag = '';
  try {
    const tags = run('git tag --sort=-v:refname').split('\n');
    const rcTags = tags.filter((t) => t.startsWith(`v${baseVersion}-rc.`));
    const currentRcNum = Number((nextVersion.match(/-rc\.(\d+)$/) || [])[1] || '0');
    const prevTarget = `v${baseVersion}-rc.${currentRcNum - 1}`;
    if (currentRcNum > 1 && rcTags.includes(prevTarget)) prevRcTag = prevTarget;
  } catch {}
  const deltaUrl = prevRcTag ? `https://github.com/${owner}/${name}/compare/${prevRcTag}...v${nextVersion}` : '';

  // Try to fetch auto-generated release notes from GitHub API (cumulative from last stable)
  let generatedNotes = '';
  try {
    const body = await restJson(
      token,
      `/repos/${owner}/${name}/releases/generate-notes`,
      'POST',
      {
        tag_name: `v${nextVersion}`,
        previous_tag_name: lastStableTag || undefined,
        target_commitish: 'develop',
      }
    );
    generatedNotes = body?.body || '';
  } catch {}

  const note = `Released ${today}: v${nextVersion} — ${releaseUrl}${deltaUrl ? ` | Delta since ${prevRcTag}: ${deltaUrl}` : ''}${compareUrl ? ` | Since last stable: ${compareUrl}` : ''}`;


  const bodyTemplateRc = (v) => {
    return [
      `Tracking thread for the upcoming release v${baseVersion}.`,
      '',
      `Date: ${today}`,
      '',
      `## Latest build`,
      `- RC: v${v}`,
      `- Release: ${releaseUrl}`,
      compareUrl ? `- Since last stable: ${compareUrl}` : undefined,
      deltaUrl ? `- Delta since previous RC (${prevRcTag}): ${deltaUrl}` : undefined,
      '',
      `## Notes`,
      `Feedback welcome — report issues here: https://github.com/${owner}/${name}/issues/new?title=${encodeURIComponent(
        `RC v${v} feedback:`
      )}`,
      generatedNotes
        ? `\n<details><summary>Auto-generated release notes</summary>\n\n${generatedNotes}\n\n</details>`
        : undefined,
    ]
      .filter(Boolean)
      .join('\n');
  };

  const bodyTemplateStable = (v, finalDeltaUrl) => {
    return [
      `Status: Released v${v} on ${today}.`,
      '',
      `## Links`,
      `- Stable: https://github.com/${owner}/${name}/releases/tag/v${v}`,
      finalDeltaUrl ? `- Delta from last RC: ${finalDeltaUrl}` : undefined,
      lastStableTag ? `- Since previous stable: https://github.com/${owner}/${name}/compare/${lastStableTag}...v${v}` : undefined,
    ]
      .filter(Boolean)
      .join('\n');
  };

  if (isRc(nextVersion) && !existing) {
    // Create the rolling RC discussion once
  await gqlFetch(
      token,
      `mutation CreateDiscussion($repoId: ID!, $catId: ID!, $title: String!, $body: String!) {
        createDiscussion(input: { repositoryId: $repoId, categoryId: $catId, title: $title, body: $body }) {
          discussion { id number url }
        }
      }`,
      { repoId: repo.id, catId: category.id, title, body: bodyTemplateRc(nextVersion) }
    );
    // No further comment needed on first creation
    return;
  }

  if (isRc(nextVersion) && existing) {
    // Update the discussion body with latest RC details
    await gqlFetch(
      token,
      `mutation UpdateDiscussion($id: ID!, $body: String!) {
        updateDiscussion(input: { discussionId: $id, body: $body }) { discussion { id url } }
      }`,
      { id: existing.id, body: bodyTemplateRc(nextVersion) }
    );
    // Add a comment to the existing rolling thread
    await gqlFetch(
      token,
      `mutation AddComment($discId: ID!, $body: String!) {
        addDiscussionComment(input: { discussionId: $discId, body: $body }) {
          comment { url }
        }
      }`,
      { discId: existing.id, body: note }
    );
    return;
  }

  // Stable release: finalize thread (if exists)
  if (!isRc(nextVersion) && existing) {
    // Compute delta from last RC to stable
    let lastRcTag = '';
    try {
      const tags = run('git tag --sort=-v:refname').split('\n');
      const rcTags = tags.filter((t) => t.startsWith(`v${baseVersion}-rc.`));
      lastRcTag = rcTags[0] || '';
    } catch {}
    const finalDeltaUrl = lastRcTag
      ? `https://github.com/${owner}/${name}/compare/${lastRcTag}...v${nextVersion}`
      : '';

    // Update body to show Released status and links
    await gqlFetch(
      token,
      `mutation UpdateDiscussion($id: ID!, $body: String!) {
        updateDiscussion(input: { discussionId: $id, body: $body }) { discussion { id url } }
      }`,
      { id: existing.id, body: bodyTemplateStable(nextVersion, finalDeltaUrl) }
    );
    // Post final comment
    await gqlFetch(
      token,
      `mutation AddComment($discId: ID!, $body: String!) {
        addDiscussionComment(input: { discussionId: $discId, body: $body }) {
          comment { url }
        }
      }`,
      { discId: existing.id, body: `Stable released ${today}: v${nextVersion} — https://github.com/${owner}/${name}/releases/tag/v${nextVersion}${finalDeltaUrl ? ` | Delta from last RC: ${finalDeltaUrl}` : ''}` }
    );
  }
}

main().catch(() => {
  // Non-fatal by design
});
