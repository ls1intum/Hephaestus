# Getting Started

## Client Development

### React + Vite

We use React with Vite for a fast DX and modern build tooling. The app lives in `webapp-react`.

- React docs: [react.dev](https://react.dev/)
- Vite server options (ports/proxy): [vite.dev/config/server-options](https://vite.dev/config/server-options)
- TanStack Router: [tanstack.com/router](https://tanstack.com/router/latest)
- TanStack Query: [tanstack.com/query (React)](https://tanstack.com/query/latest/docs/framework/react/overview)

### TailwindCSS: Our Styling Framework

**TailwindCSS is a utility-first CSS framework that speeds up UI development with utility classes.** It integrates seamlessly with React, making it easy to maintain consistency across your codebase.

```{figure} ./tailwindcss.svg
:width: 250px
:alt: Tailwind Logo
```

**Quick Reference:** [TailwindCSS Cheat Sheet](https://nerdcave.com/tailwind-cheat-sheet)

#### Core Concepts

- [Utility-First Fundamentals](https://tailwindcss.com/docs/utility-first): Building complex components from a constrained set of primitive utilities.
- [Hover, Focus, and Other States](https://tailwindcss.com/docs/hover-focus-and-other-states): Using utilities to style elements on hover, focus, and more.
- [Responsive Design](https://tailwindcss.com/docs/responsive-design): Using responsive utility variants to build adaptive user interfaces.
- [Dark Mode](https://tailwindcss.com/docs/dark-mode): Using Tailwind CSS to style your site in dark mode.
- [Reusing Styles](https://tailwindcss.com/docs/reusing-styles): Managing duplication and creating reusable abstractions.
- [Adding Custom Styles](https://tailwindcss.com/docs/adding-custom-styles): Best practices for adding your own custom styles to Tailwind.
- [Functions & Directives](https://tailwindcss.com/docs/functions-and-directives): A reference for the custom functions and directives Tailwind exposes to your CSS.

#### Best Practices

1. **Ensure IDE Autocomplete:** Set up IDE autocomplete for instant access to utility classes. **Very important!**
2. **Use Predefined Utilities:** Stick to Tailwind's utility classes for consistency. **Avoid CSS!**
3. **Responsive and State Variants:** Leverage responsive design and state variants.
4. **Avoid Premature Abstraction:** Don't use `@apply` just to clean up your HTML. **It is fine to repeat yourself!**
5. **Design Tokens:** Utilize design tokens for consistent theming and easy maintenance of your design system. Define and use them in your Tailwind configuration for colors, spacing, typography, etc.

### Storybook: Component Driven UI

**Storybook.js is a frontend workshop for building UI components and pages in isolation.** Thousands of teams, including ours, use it for UI development, testing, and documentation. It's open source and free, and it transforms how we develop user interfaces by enabling us to focus on creating high-quality, reusable components without the grunt work.

```{figure} ./storybook.svg
:width: 250px
:alt: Storybook Logo
```

#### Storybook's Core Strengths

1. **Isolation:** Develop components in isolation, ensuring they work independently of the app. This prevents issues arising from dependencies on other components or global application state.
2. **Component-Driven Development (CDD):** Focus on building individual UI components first and then composing them into complete user interfaces. This methodology aligns with modern frontend best practices and enhances reusability and maintainability.
3. **Interactive Documentation:** Each component can be documented interactively, allowing developers and designers to see the components in various states and configurations.
4. **Automated Testing:** Storybook supports a variety of testing tools for visual regression testing, accessibility checks, and behavior testing, ensuring components meet quality standards before integration.

#### Getting Started with Storybook

- [Storybook.js](https://storybook.js.org/): Official website for Storybook.
- [Review our Storybook](https://develop--66a8981a27ced8fef3190d41.chromatic.com/): Explore our Storybook to see an overview of our components for reference (state of `develop` branch).
- [Storybook Docs](https://storybook.js.org/docs): Official documentation for Storybook.
- [What's a story?](https://storybook.js.org/docs/get-started/whats-a-story): Learn the basics of Storybook and how to create stories.
- [Storybook Tutorial](https://storybook.js.org/tutorials/): Step-by-step guide to creating a Storybook for your project.
- [Chromatic](https://www.chromatic.com/): Automated visual testing and review tool for Storybook that we use.
- [Addons](https://storybook.js.org/addons): Extend Storybook's functionality with a rich ecosystem of addons for actions, accessibility, backgrounds, and more.

#### Best Practices for Using Storybook

1. **Organize Stories Logically:** Group stories by component type or feature to maintain a clean and navigable Storybook.
2. **Comprehensive Stories:** Ensure each component has a story and the stories cover all meaningful states, including edge cases, to thoroughly test and document its behavior.
3. **Automated Testing Integration:** Perform visual regression testing, accessibility checks, and behavior testing in Storybook to catch issues early and ensure components meet quality standards.
4. **Visual Regression Testing:** Use Chromatic for visual regression testing for Storybook stories to catch visual bugs early and ensure consistent UI across components. Ensure you have a Chromatic account that is added to the team and you are familiar with the tool.

```{important}
Chromatic runs on every PR, make sure to add stories and check the visual diffs and get them approved if they are expected! Build results are located in the `Chromatic: Run Chromatic` CI check under `Details` at `Summary`.
```

```{tip}
Refer to [Shadcn/ui](https://ui.shadcn.com/) for theming and component examples. We follow their styling approach with [Class Variance Authority](https://cva.style/docs). Check our Storybook for examples. Avoid niche UI libs; prefer headless Radix primitives.
```

### OpenAPI: Type-Safe API Interaction

**We use a generated OpenAPI client** (via @hey-api/openapi-ts) to ensure type-safe interactions with our server. This client simplifies communication by generating TypeScript types and client calls from our OpenAPI specification, reducing boilerplate code and ensuring consistency.

```{figure} ./openapi.png
:width: 200px
:alt: OpenAPI Logo
```

#### Benefits

- **Type Safety**: Automatically generated client ensures all API interactions are type-checked, reducing runtime errors.
- **Consistency**: Ensures all parts of the application interact with the API in a uniform manner.
- **Reduced Boilerplate**: Minimizes repetitive code, making development faster and cleaner.
- **Ease of Use**: Simplifies API consumption with well-defined methods and structures based on the OpenAPI spec.

By leveraging OpenAPI, we enhance the reliability, maintainability, and efficiency of our client-server interactions.

#### Resources

- [OpenAPI Specification](https://swagger.io/specification/)
- [OpenAPI Generator](https://openapi-generator.tech/)
- [Swagger UI](https://swagger.io/tools/swagger-ui/)


## Intelligence Service Development

### FastAPI: Intelligence Service Interaction

FastAPI provides the **API layer that connects the AI tools and core logic to the client-server**.
```{figure} ./fastapi.png
:width: 200px
:alt: FastAPI Logo
```
FastAPI is a modern, fast (high-performance) web framework for building APIs with Python 3.7+ based on standard Python type hints. It enables rapid development of robust, production-ready web services and APIs with minimal boilerplate.

#### Benefits
- **Automatic Docs**: FastAPI automatically generates interactive API documentation with Swagger UI and ReDoc, improving development speed and client onboarding.
- **Type Safety**: Leverages Python type hints to validate input and output data, catching bugs early and improving code quality.
- **Dependency Injection System**: Clean architecture is encouraged by FastAPI's built-in DI system, making it easier to test and extend.
- **Validation and Serialization**: Uses Pydantic for easy request validation and response serialization, reducing boilerplate and improving clarity.
