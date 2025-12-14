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

### Allowed Types

- `fix`: A bug fix
- `feat`: A new feature
- `docs`: Documentation only changes
- `style`: Changes that do not affect the meaning of the code (white-space, formatting, missing semi-colons, etc.)
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
- `server`: Java application server
- `ai`: Python intelligence service
- `webhooks`: Webhook ingestion service
- `docs`: Documentation

**Infrastructure scopes**:

- `ci`: CI/CD workflows
- `deps`: Dependencies
- `docker`: Container configuration
- `db`: Database/Liquibase changes

**Feature scopes** (domain-specific):

- `leaderboard`: Leaderboard and rankings
- `mentor`: AI mentor (Heph)
- `profile`: User profiles
- `workspace`: Workspace management
- `teams`: Team competitions
- `github`: GitHub integration
- `notifications`: Email/notification system

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
