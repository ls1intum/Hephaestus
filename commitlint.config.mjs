/**
 * Commitlint Configuration
 *
 * This config validates commit messages and PR titles against Conventional Commits.
 * Primary use case: CI validation of PR titles (which become commit messages after squash merge).
 *
 * @see https://commitlint.js.org
 * @see CONTRIBUTING.md for guidelines
 */

export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    // Allowed commit types (aligned with .releaserc and semantic-release)
    'type-enum': [
      2,
      'always',
      [
        'feat', // New feature (triggers minor release)
        'fix', // Bug fix (triggers patch release)
        'docs', // Documentation only
        'style', // Code style (formatting, semicolons, etc.)
        'refactor', // Code refactoring (no feature/fix)
        'perf', // Performance improvement (triggers patch release)
        'test', // Test changes
        'build', // Build system or dependencies
        'ci', // CI/CD changes
        'chore', // Maintenance tasks
        'revert', // Revert previous commit (triggers patch release)
      ],
    ],
    // Scopes are strictly enforced to ensure correct release behavior
    // Infrastructure scopes (ci, deps, docker) prevent accidental releases
    'scope-enum': [
      2, // Error level - blocking
      'always',
      [
        // Service scopes (where the code lives)
        'webapp',
        'server',
        'ai',
        'webhooks',
        'docs',
        // Infrastructure scopes (will NOT trigger release)
        'ci',
        'deps',
        'deps-dev',
        'docker',
        'scripts',
        'security',
        'db',
        'no-release',
        // Feature scopes (domain-specific)
        'gitprovider', // Server module for Git integration
        'leaderboard',
        'mentor',
        'notifications',
        'profile',
        'teams',
        'workspace',
      ],
    ],
    // Allow empty scope (scope is optional per CONTRIBUTING.md)
    'scope-empty': [0],
    // Subject must be lowercase
    'subject-case': [2, 'always', 'lower-case'],
    // No period at end of subject
    'subject-full-stop': [2, 'never', '.'],
    // Subject shouldn't be empty
    'subject-empty': [2, 'never'],
    // Type shouldn't be empty
    'type-empty': [2, 'never'],
    // Type must be lowercase
    'type-case': [2, 'always', 'lower-case'],
    // Header (full first line) max length
    'header-max-length': [2, 'always', 100],
  },
};
