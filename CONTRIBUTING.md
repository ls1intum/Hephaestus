# Contribution Guidelines for Hephaestus

Read the [local development guide](https://ls1intum.github.io/Hephaestus/contributor/local-development) on how to set up your environment.

## Maintenance Status

Hephaestus is a research project at TUM, actively developed but maintained primarily by one person. Issues and pull requests are triaged on a best-effort basis. Security reports are the exception and get priority — see [SECURITY.md](SECURITY.md) for how to report vulnerabilities privately.

## Identity and Transparency

To ensure a transparent and trustworthy environment, we have established different guidelines for members of our organization and external contributors.

### For Members of Our Organization

1. **Real Names Required**: Members must use their full real name in their GitHub profile to ensure accountability.
2. **Profile Picture**: Members must upload an authentic, professional profile picture. Comic-style images or avatars are not permitted.
3. **Internal Workflow**: Members should create branches and pull requests directly within the repository.

### For External Contributors

1. **Identity Verification**: Contributions are only accepted from users with real names and authentic profile pictures.
2. **Forking**: Fork the repository and work on changes in your own branch.
3. **Pull Request**: Submit a PR from your fork. Ensure your branch is up to date with `main`.

### Compliance

Contributions that do not adhere to these guidelines will be rejected. We align with [GitHub Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies).

## Contribution Process

1. **External contributors only**: Fork the Repository and create a branch.
2. **Create a feature branch**: Work on your changes in a separate branch.
3. **Follow pull request title guidelines**: Ensure your PR title follows the [Conventional Commits](https://www.conventionalcommits.org/) specification.
4. **Submit a pull request**: Once your work is complete, submit a pull request for review.

## Pull Request Title Guidelines

We use automated semantic pull request validation to ensure consistent and meaningful commit history. Your pull request titles must follow the [Conventional Commits](https://www.conventionalcommits.org/) specification.

### Format

```text
<type>[optional scope]: <description>
```

### Releases and Changesets (Important)

**Release ≠ deploy.** Every PR that changes shipped code (anything under `server/`, `webapp/`, or `docker/` except tests and in-tree docs) must carry a
**changeset** — an operator-facing description of the change plus its version bump. CI enforces this
(`verify-changesets`); commit types and scopes never affect versioning.

```bash
pnpm changeset          # user-facing change: pick the bump, describe it
pnpm changeset --empty  # no user-facing effect — then write why in the file body
```

> ⚠️ **Pre-1.0:** never pick a `major` bump — on `0.x` it cuts 1.0.0 (CI rejects it). Use `minor` for
> breaking changes until the [1.0 milestone](https://github.com/ls1intum/Hephaestus/issues/1378).

Changesets accumulate into a **Version PR**; merging it cuts the release. How releases are cut, how to
write good changesets, and what a version number promises all live in the
[release management guide](https://ls1intum.github.io/Hephaestus/contributor/release-management) and the
[compatibility policy](https://ls1intum.github.io/Hephaestus/admin/compatibility-policy).

### Allowed Types

- `fix`: A bug fix
- `feat`: A new feature
- `docs`: Documentation only changes
- `style`: Changes that do not affect the meaning of the code
- `refactor`: A code change that neither fixes a bug nor adds a feature
- `perf`: A code change that improves performance
- `test`: Adding missing tests or correcting existing tests
- `build`: Changes that affect the build system or external dependencies
- `ci`: Changes to our CI configuration files and scripts
- `chore`: Other changes that don't modify src or test files
- `revert`: Reverts a previous commit

### Recommended Scopes

**Service scopes** (where the code lives):

- `webapp`: React frontend
- `server`: Java application server (includes the in-process Pi mentor agent and the webhook receiver)
- `docs`: Documentation

**Infrastructure scopes** (affect runtime):

- `deps`: Production dependencies (security patches, bug fixes)
- `security`: Security fixes are critical
- `db`: Database migrations affect runtime
- `docker`: Dockerfiles, production compose files

**Infrastructure scopes** (tooling and process):

- `ci`: CI/CD workflows
- `config`: Tooling configuration (renovate, eslint, tsconfig, etc.)
- `deps-dev`: Dev dependencies only
- `scripts`: Helper scripts
- `release`: Release engineering (also used by the automated Version PR)

> ⚠️ **`config` scope warning:** Only use for tooling config files like `.prettierrc`, `renovate.json`, `eslint.config.js`. Do NOT use for:
> - Runtime config (`application.yml`) → use `server`
> - Dockerfiles → use service scope (`webapp`, `server`, etc.)
> - Production compose files → use `docker`

**Feature scopes** (domain-specific):

- `auth`: Authentication / identity (Account, IdentityLink, JWT, oauth2Login)
- `integration`: Cross-cutting integration framework (webhook, oauth, registry, SPI)
- `scm`: Source-control management (GitHub, GitLab) — formerly `gitprovider`
- `leaderboard`: Leaderboard and rankings
- `mentor`: AI mentor (Heph)
- `notifications`: Email/notification system
- `profile`: User profiles
- `teams`: Team competitions
- `workspace`: Workspace management

### Examples

**Valid pull request titles:**

- `fix(profile): correct avatar upload logic`
- `feat(leaderboard): add sorting functionality`
- `feat(mentor): add conversation history`
- `feat(server): add user profile endpoint`
- `docs: update installation instructions`
- `refactor(mentor): improve code analysis performance`
- `fix(deps): update vulnerable dependency`
- `fix(security): patch authentication bypass`
- `fix(db): add missing index for performance`
- `chore(deps-dev): update test dependencies`

**Draft Pull Requests:**

If your pull request is still in progress, please open it as a **Draft Pull Request**. This signals that the work is not yet ready for review without cluttering the title with `[WIP]`.

### Guidelines

- Use lowercase for the description
- Don't end the description with a period
- Use the imperative mood in the description (e.g., "add" not "adds" or "added")
  - Think of it as completing the sentence: "If applied, this commit will ..."
  - ✅ "fix authentication bug" → "If applied, this commit will fix authentication bug"
  - ❌ "fixed authentication bug" or "fixes authentication bug"
- Keep the entire title under 72 characters when possible
