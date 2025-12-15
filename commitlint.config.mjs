/**
 * Commitlint Configuration
 *
 * This config validates commit messages and PR titles against Conventional Commits.
 * Primary use case: CI validation of PR titles (which become commit messages after squash merge).
 *
 * @see https://commitlint.js.org
 * @see CONTRIBUTING.md for guidelines
 */

// Allowed commit types (aligned with .releaserc and semantic-release)
const TYPES = [
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
];

// Allowed scopes (aligned with pull-request.yml)
const SCOPES = [
  // Service scopes (where the code lives)
  'webapp',
  'server',
  'ai',
  'webhooks',
  'docs',
  // Infrastructure scopes (will NOT trigger release)
  'ci',
  'config',
  'deps',
  'deps-dev',
  'docker',
  'scripts',
  'security',
  'db',
  'no-release',
  // Feature scopes (domain-specific)
  'gitprovider',
  'leaderboard',
  'mentor',
  'notifications',
  'profile',
  'teams',
  'workspace',
];

// Custom plugin to provide helpful error messages
const helpfulErrorsPlugin = {
  rules: {
    'type-enum-helpful': (parsed, _when, _value) => {
      const { type } = parsed;
      if (!type) return [true];
      const valid = TYPES.includes(type);
      return [
        valid,
        valid
          ? ''
          : `type "${type}" is not allowed.\n\n` +
            `Allowed types:\n` +
            `  ${TYPES.join(', ')}\n\n` +
            `Format: <type>(<scope>): <description>\n` +
            `Example: feat(webapp): add user profile page`,
      ];
    },
    'scope-enum-helpful': (parsed, _when, _value) => {
      const { scope } = parsed;
      if (!scope) return [true]; // Scope is optional
      const valid = SCOPES.includes(scope);
      return [
        valid,
        valid
          ? ''
          : `scope "${scope}" is not allowed.\n\n` +
            `Allowed scopes:\n` +
            `  Services:       webapp, server, ai, webhooks, docs\n` +
            `  Infrastructure: ci, config, deps, deps-dev, docker, scripts, security, db, no-release\n` +
            `  Features:       gitprovider, leaderboard, mentor, notifications, profile, teams, workspace\n\n` +
            `Format: <type>(<scope>): <description>\n` +
            `Example: fix(server): resolve null pointer exception`,
      ];
    },
  },
};

export default {
  extends: ['@commitlint/config-conventional'],
  plugins: [helpfulErrorsPlugin],
  helpUrl: 'https://github.com/ls1intum/Hephaestus/blob/main/CONTRIBUTING.md',
  rules: {
    // Use custom rules for helpful error messages
    'type-enum-helpful': [2, 'always'],
    'scope-enum-helpful': [2, 'always'],
    // Disable default enum rules (replaced by helpful versions)
    'type-enum': [0],
    'scope-enum': [0],
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
