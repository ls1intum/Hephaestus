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
  "feat", // New feature (triggers minor release)
  "fix", // Bug fix (triggers patch release)
  "docs", // Documentation only
  "style", // Code style (formatting, semicolons, etc.)
  "refactor", // Code refactoring (no feature/fix)
  "perf", // Performance improvement (triggers patch release)
  "test", // Test changes
  "build", // Build system or dependencies
  "ci", // CI/CD changes
  "chore", // Maintenance tasks
  "revert", // Revert previous commit (triggers patch release)
];

// Allowed scopes (aligned with pull-request.yml)
const SCOPES = [
  // === SERVICE SCOPES (where the code lives) - WILL trigger release ===
  "webapp", // React frontend, webapp Dockerfile
  "server", // Java backend, server Dockerfile
  "ai", // Intelligence service, ai Dockerfile
  "webhooks", // Webhook ingestion, webhooks Dockerfile
  "docs", // Documentation site

  // === INFRASTRUCTURE SCOPES that WILL trigger release (affect runtime) ===
  "deps", // Production dependencies (security patches, bug fixes)
  "security", // Security fixes (CRITICAL - must release)
  "db", // Database migrations (affect runtime)
  "docker", // Dockerfiles, production compose (affect deployed containers)

  // === INFRASTRUCTURE SCOPES that will NOT trigger release ===
  "ci", // GitHub Actions, CI workflows only
  "config", // TOOLING ONLY: .prettierrc, renovate.json, eslint, vscode
  //          NOT for: application.yml (use 'server'), Dockerfiles (use service scope)
  "deps-dev", // Dev dependencies only (test libs, linters)
  "scripts", // Build/dev helper scripts
  "no-release", // Explicit opt-out

  // === FEATURE SCOPES (domain-specific) - WILL trigger release ===
  "gitprovider",
  "leaderboard",
  "mentor",
  "notifications",
  "profile",
  "teams",
  "workspace",
];

// Custom plugin to provide helpful error messages
const helpfulErrorsPlugin = {
  rules: {
    "type-enum-helpful": (parsed, _when, _value) => {
      const { type } = parsed;
      if (!type) return [true];
      const valid = TYPES.includes(type);
      return [
        valid,
        valid
          ? ""
          : `type "${type}" is not allowed.\n\n` +
            `Allowed types:\n` +
            `  ${TYPES.join(", ")}\n\n` +
            `Format: <type>(<scope>): <description>\n` +
            `Example: feat(webapp): add user profile page`,
      ];
    },
    "scope-enum-helpful": (parsed, _when, _value) => {
      const { scope } = parsed;
      if (!scope) return [true]; // Scope is optional
      const valid = SCOPES.includes(scope);
      return [
        valid,
        valid
          ? ""
          : `scope "${scope}" is not allowed.\n\n` +
            `Allowed scopes:\n` +
            `  Services (release):    webapp, server, ai, webhooks, docs\n` +
            `  Infra (release):       deps, security, db, docker\n` +
            `  Infra (NO release):    ci, config, deps-dev, scripts, no-release\n` +
            `  Features (release):    gitprovider, leaderboard, mentor, notifications, profile, teams, workspace\n\n` +
            `⚠️  'config' is for TOOLING only (.prettierrc, renovate.json)\n` +
            `    For runtime config use 'server', for Dockerfiles use service scope\n\n` +
            `Format: <type>(<scope>): <description>\n` +
            `Example: fix(server): resolve null pointer exception`,
      ];
    },
  },
};

export default {
  extends: ["@commitlint/config-conventional"],
  plugins: [helpfulErrorsPlugin],
  helpUrl: "https://github.com/ls1intum/Hephaestus/blob/main/CONTRIBUTING.md",
  rules: {
    // Use custom rules for helpful error messages
    "type-enum-helpful": [2, "always"],
    "scope-enum-helpful": [2, "always"],
    // Disable default enum rules (replaced by helpful versions)
    "type-enum": [0],
    "scope-enum": [0],
    // Allow empty scope (scope is optional per CONTRIBUTING.md)
    "scope-empty": [0],
    // Disable subject-case: lower-case is too strict for technical terms
    // (API, URL, GraphQL, OAuth, class names, env vars like APPLICATION_HOST_URL).
    // Angular convention says "don't capitalize first letter" which we enforce
    // via convention/review, not tooling. This avoids false positives.
    "subject-case": [0],
    // No period at end of subject
    "subject-full-stop": [2, "never", "."],
    // Subject shouldn't be empty
    "subject-empty": [2, "never"],
    // Type shouldn't be empty
    "type-empty": [2, "never"],
    // Type must be lowercase
    "type-case": [2, "always", "lower-case"],
    // Header (full first line) max length
    "header-max-length": [2, "always", 100],
  },
};
