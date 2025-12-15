# Contribution Guidelines for Hephaestus

Read the [local development guide](https://ls1intum.github.io/Hephaestus/contributor/local-development) on how to set up your environment.

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

### Release Behavior (Important)

We use **Semantic Release** to automatically version and release our application based on commit messages.

**❌ Will NOT Trigger Release** (Scope Overrides):

| Pattern | Example | Why |
|---------|---------|-----|
| `*(ci):` | `fix(ci): update workflow` | `ci` scope blocks release |
| `*(config):` | `chore(config): update renovate.json` | `config` scope blocks release |
| `*(deps):` | `fix(deps): update library` | `deps` scope blocks release |
| `*(docker):` | `fix(docker): update base image` | `docker` scope blocks release |
| `*(scripts):` | `fix(scripts): fix db backup` | `scripts` scope blocks release |
| `*(security):` | `chore(security): update policy` | `security` scope blocks release |
| `*(no-release):`| `feat(no-release): internal feature` | Explicit block |

**✅ WILL Trigger Release**:

| Type | Version | Example |
|------|---------|---------|
| `feat:` | **Minor** | `feat(webapp): add dark mode` |
| `fix:` | **Patch** | `fix(api): handle null response` |
| `perf:` | **Patch** | `perf: optimize query` |
| `revert:` | **Patch** | `revert: undo change` |
| `!:` | **Major** | `feat!: new api structure` |

**❌ Will NOT Trigger Release** (Type-Based):
`docs:`, `style:`, `refactor:`, `test:`, `build:`, `chore:`, `ci:`

### Allowed Types

- `fix`: A bug fix (triggers patch release)
- `feat`: A new feature (triggers minor release)
- `docs`: Documentation only changes
- `style`: Changes that do not affect the meaning of the code
- `refactor`: A code change that neither fixes a bug nor adds a feature
- `perf`: A code change that improves performance (triggers patch release)
- `test`: Adding missing tests or correcting existing tests
- `build`: Changes that affect the build system or external dependencies
- `ci`: Changes to our CI configuration files and scripts
- `chore`: Other changes that don't modify src or test files
- `revert`: Reverts a previous commit

### Recommended Scopes

**Service scopes** (where the code lives):

- `webapp`: React frontend
- `server`: Java application server
- `ai`: Python intelligence service
- `webhooks`: Webhook ingestion service
- `docs`: Documentation

**Infrastructure scopes** (⚠️ These prevent releases):

- `ci`: CI/CD workflows
- `config`: Tooling configuration (renovate, eslint, tsconfig, etc.)
- `deps`: Dependencies
- `deps-dev`: Dev dependencies
- `docker`: Container configuration
- `scripts`: Helper scripts
- `security`: Security policies and config
- `db`: Database/Liquibase changes
- `no-release`: Explicit release prevention

**Feature scopes** (domain-specific):

- `gitprovider`: Git integration module
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
- `refactor(ai): improve code analysis performance`
- `chore(deps): update dependencies to latest versions`
- `fix(db): add missing index for performance`

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
