# Additional Hints

## Shadcn instructions (UI components)

If needed, check `src/components/ui` for existing components before you install new ones.

Use the latest version of Shadcn to install new components, like this command to add a button component:

```bash
npx shadcn@latest add button
```

## Ensure build success

After being done with implementation, make sure to run the build command to check for any issues:

```bash
npm run build
```

Ensure that Typescript throws no errors:

```bash
npx tsc
```

Ensure that the linter throws no errors:

```bash
npm run check -- --fix
```

## Storybook

Our components are purely presentational and documented using Storybook. Strive for simple, reusable components with thoughtful props and naming.
Colocate your stories with the component files.

## UX Writing

Think of our platform’s voice as that of a friendly, knowledgeable senior engineer: clear and supportive in guidance, encouraging and playful in celebrating progress, and always approachable to make learning fun.

## Quality Attributes

Strive for simplicity and clarity in your code. Avoid unnecessary complexity and aim for a clean, maintainable codebase. Use descriptive variable and function names, and keep your code organized and well-structured.
