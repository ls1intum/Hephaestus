# Contribution Guidelines for Hephaestus

Read the [setup guide](https://ls1intum.github.io/Hephaestus/dev/setup_guide/) on how to set up your local development environment.

## Identity and Transparency

To ensure a transparent and trustworthy environment, we have established different guidelines for members of our organization and external contributors.

### For Members of Our Organization

1. **Real Names Required**: As a member of our organization, you must use your full real name in your GitHub profile. This is a prerequisite for joining our organization. Using a real name is crucial for building trust within the team and the broader community. It fosters accountability and transparency, which are essential for collaborative work. When members use their real identities, it encourages open communication and strengthens professional relationships. Furthermore, it aligns with best practices in open-source communities, where transparency is key to ensuring the integrity and reliability of contributions.

2. **Profile Picture**: Members are required to upload an authentic profile picture. Use a clear, professional image and avoid comic-like pictures, memojis, or other non-authentic picture styles. Using a professional and authentic profile picture is essential for establishing credibility and fostering trust within the community. It helps others easily identify and connect with you, which is crucial for effective collaboration. By using a real photo, you present yourself as a serious and committed contributor, which in turn encourages others to take your work and interactions seriously. Avoiding non-authentic images ensures that the focus remains on the substance of your contributions rather than on distractions or misunderstandings that might arise from informal or unprofessional visuals.

3. **Direct Branching and PR Creation**: As a member, you are encourages to create branches and pull requests (PRs) directly within the repository.

### For External Contributors

1. **Identity Verification**: External contributions will only be considered if the contributor uses their real name and an authentic profile picture (see above). This ensures accountability and trustworthiness in all external contributions.

2. **Forking the Repository**: External contributors fork the repository and work on changes in their own branches.

3. **Submit a Pull Request**: Once your work is complete, submit a pull request for review. Ensure that your branch is up to date with the main branch before submitting.

4. **Compliance**: Contributions from external contributors that do not adhere to these guidelines may not be accepted.

### References and Best Practices

- We align our guidelines with the [GitHub Acceptable Use Policies](https://docs.github.com/en/site-policy/acceptable-use-policies) which stress the importance of authenticity and transparency in user profiles.
- For more insights on contributing to open-source projects, we recommend reviewing the [Open Source Guides by GitHub](https://opensource.guide/).

By following these guidelines, we foster a collaborative environment built on mutual trust and respect, essential for the success of our project.

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

- `webapp`: Changes to the Angular webapp
- `webapp-react`: Changes to the React webapp
- `application-server`: Changes to the Java application server
- `intelligence-service`: Changes to the Python intelligence service
- `webhook-ingest`: Changes to the webhook ingestion service
- `docs`: Documentation changes
- `ci`: CI/CD related changes
- `deps`: Dependency updates
- `config`: Configuration changes

### Examples

**Valid pull request titles:**

- `fix: correct typo in user authentication`
- `feat(webapp): add leaderboard sorting functionality`
- `docs: update installation instructions`
- `refactor(intelligence-service): improve code analysis performance`
- `chore(deps): update dependencies to latest versions`

**Work-in-progress PRs:**

If your pull request is still in progress, you can prefix it with `[WIP]`:

- `[WIP] feat(webapp): add new dashboard component`

### Guidelines

- Use lowercase for the description
- Don't end the description with a period
- Use the imperative mood in the description (e.g., "add" not "adds" or "added")
  - Think of it as completing the sentence: "If applied, this commit will ..."
  - ✅ "fix authentication bug" → "If applied, this commit will fix authentication bug"
  - ❌ "fixed authentication bug" or "fixes authentication bug"
- Keep the entire title under 72 characters when possible
