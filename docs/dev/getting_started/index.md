# Getting Started

## Client Development

### Angular: Our web framework

**Angular is a web framework that empowers developers to build fast, reliable applications.**
Maintained by a dedicated team at Google, Angular provides a broad suite of tools, APIs, and libraries to simplify and streamline your development workflow. Angular gives you a solid platform on which to build fast, reliable applications that scale with both the size of your team and the size of your codebase.

```{figure} ./angular_wordmark_gradient.png
:width: 200px
:alt: Angular Logo
```

Get started with Angular, learn the fundamentals and explore advanced topics on the documentation website:

- [Getting Started](https://angular.dev/tutorials/learn-angular): Interactive tutorial of the basics
- [Essentials](https://angular.dev/essentials): Basics to read
- [Angular Overview](https://angular.dev/overview): Read through what interests you
- [Signals](https://angular.dev/guide/signals#what-are-signals): **We build signal-based!**
- [Zoneless](https://angular.dev/guide/experimental/zoneless): We are zoneless! (requires signals)
- [Standalone components](https://angular.dev/guide/components/importing#standalone-components): **Always standalone, avoid NgModule completely!**
- [Animations](https://angular.dev/guide/animations)
- [API Reference](https://angular.dev/api)

```{attention}
**We avoid decorators and use signal-based alternatives:**
```

- [input](https://angular.dev/api/core/input) instead of [@Input](https://angular.dev/api/core/Input?tab=usage-notes)! ([Angular blog on input](https://blog.angular.dev/signal-inputs-available-in-developer-preview-6a7ff1941823))
- [output](https://angular.dev/api/core/output) instead of [@Output](https://angular.dev/api/core/Output)! ([Angular blog on output](https://blog.angular.dev/meet-angulars-new-output-api-253a41ffa13c))
- [model](https://angular.dev/api/core/model) two way data binding combining a `input`, `output` pair
- [viewChild](https://angular.dev/api/core/viewChild) and [viewChildren](https://angular.dev/api/core/viewChildren) instead of [@ViewChild](https://angular.dev/api/core/ViewChild) and [@ViewChildren](https://angular.dev/api/core/ViewChildren)
- [contentChild](https://angular.dev/api/core/contentChild) and [contentChildren](https://angular.dev/api/core/contentChildren) instead of [@ContentChild](https://angular.dev/api/core/ContentChild) and [@ContentChildren](https://angular.dev/api/core/ContentChildren)

```{important}
We try to avoid [RxJS](https://rxjs.dev/guide/overview), for the most part, in favor or Angular Query and Signals!
```

### Angular Query: Powerful asynchronous state management

**Angular Query**, also known as TanStack Query, is a first-class API for managing server state in Angular applications. It simplifies the challenges of fetching, caching, synchronizing, and updating server state. Learn more about its features and how it can improve the development workflow by reading through the [Angular Query Overview](https://tanstack.com/query/latest/docs/framework/angular/overview).

```{figure} ./angular_query.png
:width: 250px
:alt: Angular Query
```

**We are using this extensively in the client to query and mutate async state, you should be very comfortable with it.**

```{uml} angular_query.puml
:caption: Sketch of query state machine in Angular Query (non-exhaustive)
```

### TailwindCSS: Our Styling Framework

**TailwindCSS is a utility-first CSS framework that speeds up UI development with utility classes.** It integrates seamlessly with Angular, making it easy to maintain consistency across your codebase.

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
Refer to [Shadcn/ui](https://ui.shadcn.com/) (React components) for theming and component examples. We are copying their styles and also use [Class Variance Authority](https://cva.style/docs) for our components like them. The Shadcn/ui Angular port [Spartan/ui](https://www.spartan.ng/documentation/installation) can also be used as reference, they are also using Storybook but we are not directly copying their components' code. Refer to existing components in the project for [examples](https://develop--66a8981a27ced8fef3190d41.chromatic.com/?path=/docs/ui-button--docs). For more complex components, we might want to use [Angular CDK](https://material.angular.io/cdk/categories) as a base while avoiding libraries that are not widely used or maintained.
```

### OpenAPI: Type-Safe API Interaction

**We use a generated OpenAPI client** to ensure type-safe interactions with our server. This client simplifies communication by generating TypeScript client services from our OpenAPI specification, reducing boilerplate code and ensuring consistency.

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

FastAPI is providing the **API layer that connects the AI tools and core logic to the client-server**.

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