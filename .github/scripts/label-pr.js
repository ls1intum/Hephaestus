/**
 * Automatically applies size and semantic labels to Pull Requests.
 * 
 * @param {object} params
 * @param {import('@actions/github').getOctokit} params.github - Octokit client
 * @param {import('@actions/github').context} params.context - GitHub context
 */
module.exports = async ({ github, context }) => {
  console.log("Calculating PR size via listFiles pagination...");
  const files = await github.paginate(
    github.rest.pulls.listFiles,
    {
      owner: context.repo.owner,
      repo: context.repo.repo,
      pull_number: context.issue.number,
      per_page: 100,
    }
  );
  
  const changedLines = files.reduce((sum, f) => sum + (f.additions || 0) + (f.deletions || 0), 0);
  console.log(`Changed lines (additions+deletions): ${changedLines}`);

  let sizeLabel = '';
  if (changedLines > 1000) sizeLabel = 'size:XXL';
  else if (changedLines > 499) sizeLabel = 'size:XL';
  else if (changedLines > 99) sizeLabel = 'size:L';
  else if (changedLines > 29) sizeLabel = 'size:M';
  else if (changedLines > 9) sizeLabel = 'size:S';
  else sizeLabel = 'size:XS';

  // Conventional Commit Labeling
  const title = context.payload.pull_request.title;
  const typeLabels = [];
  
  if (title.match(/^feat(\(.*\))?!?:/)) typeLabels.push('feature');
  if (title.match(/^fix(\(.*\))?!?:/)) typeLabels.push('bug');
  if (title.match(/^docs(\(.*\))?!?:/)) typeLabels.push('documentation');
  if (title.match(/^chore(\(.*\))?!?:/)) typeLabels.push('maintenance');
  if (title.match(/^refactor(\(.*\))?!?:/)) typeLabels.push('refactor');
  if (title.match(/^perf(\(.*\))?!?:/)) typeLabels.push('performance');
  if (title.match(/^test(\(.*\))?!?:/)) typeLabels.push('test');
  if (title.match(/^build(\(.*\))?!?:/)) typeLabels.push('maintenance');
  if (title.match(/^style(\(.*\))?!?:/)) typeLabels.push('maintenance');
  if (title.match(/^revert(\(.*\))?!?:/)) typeLabels.push('revert');
  if (title.match(/^ci(\(.*\))?!?:/)) typeLabels.push('ci');
  // Breaking change: must have ! immediately before : (e.g., feat!: or feat(scope)!:)
  // [^)]* prevents greedy matching inside the scope parentheses
  if (title.match(/^\w+(\([^)]*\))?!:/) || title.includes('BREAKING CHANGE')) typeLabels.push('breaking');
  
  console.log(`Detected type labels from title "${title}":`, typeLabels);

  // Fetch existing labels
  console.log("Fetching existing labels...");
  const labelsResponse = await github.rest.issues.listLabelsOnIssue({
    owner: context.repo.owner,
    repo: context.repo.repo,
    issue_number: context.issue.number,
  });
  const existingLabels = labelsResponse.data.map(l => l.name);

  // 1. Handle Size Labels
  const sizeLabels = existingLabels.filter(n => n.startsWith('size:'));
  const sizeToRemove = sizeLabels.filter(n => n !== sizeLabel);
  
  if (sizeToRemove.length > 0) {
     console.log(`Removing old size labels: ${sizeToRemove.join(', ')}`);
     for (const l of sizeToRemove) {
       await github.rest.issues.removeLabel({
         owner: context.repo.owner,
         repo: context.repo.repo,
         issue_number: context.issue.number,
         name: l,
       });
     }
  }
  if (!existingLabels.includes(sizeLabel)) {
      console.log(`Adding size label: ${sizeLabel}`);
      await github.rest.issues.addLabels({
        owner: context.repo.owner,
        repo: context.repo.repo,
        issue_number: context.issue.number,
        labels: [sizeLabel]
      });
  }

  // 2. Handle Semantic Labels
  const labelsToAdd = typeLabels.filter(l => !existingLabels.includes(l));
  if (labelsToAdd.length > 0) {
    console.log(`Adding semantic labels: ${labelsToAdd.join(', ')}`);
    await github.rest.issues.addLabels({
      owner: context.repo.owner,
      repo: context.repo.repo,
      issue_number: context.issue.number,
      labels: labelsToAdd
    });
  }
}
