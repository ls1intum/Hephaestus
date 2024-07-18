Getting Started
===============

Client
------

Angular: Our web framework
^^^^^^^^^^^^^^^^^^^^^^^^^^

**Angular is a web framework that empowers developers to build fast, reliable applications.**
Maintained by a dedicated team at Google, Angular provides a broad suite of tools, APIs, and libraries to simplify and streamline your development workflow. Angular gives you a solid platform on which to build fast, reliable applications that scale with both the size of your team and the size of your codebase.

.. figure:: ./angular_wordmark_gradient.png
  :width: 200px
  :alt: Angular Logo

Get started with Angular, learn the fundamentals and explore advanced topics on the documentation website:

- `Getting Started <https://angular.dev/tutorials/learn-angular>`_: Interactive tutorial of the basics
- `Essentials <https://angular.dev/essentials>`_: Basics to read
- `Angular Overview <https://angular.dev/overview>`_: Read through what interests you
- `Signals <https://angular.dev/guide/signals#what-are-signals>`_: **We build signal-based!**
- `Zoneless <https://angular.dev/guide/experimental/zoneless>`_: We are zoneless! (requires signals)
- `Standalone components <https://angular.dev/guide/components/importing#standalone-components>`_: **Always standalone, avoid NgModule completely!**
- `Animations <https://angular.dev/guide/animations>`_
- `API <https://angular.dev/api>`_

.. attention::
  **We avoid decorators and use signal-based alternatives:**

  - `input <https://angular.dev/api/core/input>`_ instead of `@Input <https://angular.dev/api/core/Input?tab=usage-notes>`_! (`Angular blog on input <https://blog.angular.dev/signal-inputs-available-in-developer-preview-6a7ff1941823>`_)
  - `output <https://angular.dev/api/core/output>`_ instead of `@Output <https://angular.dev/api/core/Output>`_! (`Angular blog on output <https://blog.angular.dev/meet-angulars-new-output-api-253a41ffa13c>`_)
  - `model <https://angular.dev/api/core/model>`_ two way data binding combining a ``input``, ``output`` pair
  - `viewChild <https://angular.dev/api/core/viewChild>`_ and `viewChildren <https://angular.dev/api/core/viewChildren>`_ instead of `@ViewChild <https://angular.dev/api/core/ViewChild>`_ and `@ViewChildren <https://angular.dev/api/core/ViewChildren>`_
  - `contentChild <https://angular.dev/api/core/contentChild>`_ and `contentChildren <https://angular.dev/api/core/contentChildren>`_ instead of `@ContentChild <https://angular.dev/api/core/ContentChild>`_ and `@ContentChildren <https://angular.dev/api/core/ContentChildren>`_

.. important::
    We try to avoid `RxJS <https://rxjs.dev/guide/overview>`_, for the most part, in favor or Angular Query and Signals!


Angular Query: Powerful asynchronous state management
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

**Angular Query**, also known as TanStack Query, is a first-class API for managing server state in Angular applications. It simplifies the challenges of fetching, caching, synchronizing, and updating server state. Learn more about its features and how it can improve the development workflow by reading through the `Angular Query Overview <https://tanstack.com/query/latest/docs/framework/angular/overview>`_.

.. figure:: ./angular_query.png
  :width: 250px
  :alt: Angular Query

**We are using this extensively in the client to query and mutate async state, you should be very comfortable with it.**

.. uml:: angular_query.puml
  :caption: Sketch of query state machine in Angular Query (non-exhaustive)


TailwindCSS: Our Styling Framework
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

**TailwindCSS is a utility-first CSS framework that speeds up UI development with utility classes.** It integrates seamlessly with Angular, making it easy to maintain consistency across your codebase.

.. figure:: ./tailwindcss.svg
  :width: 250px
  :alt: Tailwind Logo

**Quick Reference:** `TailwindCSS Cheat Sheet <https://nerdcave.com/tailwind-cheat-sheet>`_

Core Concepts
"""""""""""""

- `Utility-First Fundamentals <https://tailwindcss.com/docs/utility-first>`_: Building complex components from a constrained set of primitive utilities.
- `Hover, Focus, and Other States <https://tailwindcss.com/docs/hover-focus-and-other-states>`_: Using utilities to style elements on hover, focus, and more.
- `Responsive Design <https://tailwindcss.com/docs/responsive-design>`_: Using responsive utility variants to build adaptive user interfaces.
- `Dark Mode <https://tailwindcss.com/docs/dark-mode>`_: Using Tailwind CSS to style your site in dark mode.
- `Reusing Styles <https://tailwindcss.com/docs/reusing-styles>`_: Managing duplication and creating reusable abstractions.
- `Adding Custom Styles <https://tailwindcss.com/docs/adding-custom-styles>`_: Best practices for adding your own custom styles to Tailwind.
- `Functions & Directives <https://tailwindcss.com/docs/functions-and-directives>`_: A reference for the custom functions and directives Tailwind exposes to your CSS.

Best Practices
""""""""""""""

1. **Ensure IDE Autocomplete:** Set up IDE autocomplete for instant access to utility classes. **Very important!**
2. **Use Predefined Utilities:** Stick to Tailwind's utility classes for consistency. **Avoid CSS!**
3. **Responsive and State Variants:** Leverage responsive design and state variants.
4. **Avoid Premature Abstraction:** Don't use ``@apply`` just to clean up your HTML. **It is fine to repeat yourself!**
5. **Design Tokens:** Utilize design tokens for consistent theming and easy maintenance of your design system. Define and use them in your Tailwind configuration for colors, spacing, typography, etc.


OpenAPI: Type-Safe API Interaction
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

**We use a generated OpenAPI client** to ensure type-safe interactions with our server. This client simplifies communication by generating TypeScript client services from our OpenAPI specification, reducing boilerplate code and ensuring consistency.

.. figure:: ./openapi.png
  :width: 200px
  :alt: OpenAPI Logo

Benefits
""""""""

- **Type Safety**: Automatically generated client ensures all API interactions are type-checked, reducing runtime errors.
- **Consistency**: Ensures all parts of the application interact with the API in a uniform manner.
- **Reduced Boilerplate**: Minimizes repetitive code, making development faster and cleaner.
- **Ease of Use**: Simplifies API consumption with well-defined methods and structures based on the OpenAPI spec.

By leveraging OpenAPI, we enhance the reliability, maintainability, and efficiency of our client-server interactions.

Resources
"""""""""
- `OpenAPI Specification <https://swagger.io/specification/>`_
- `OpenAPI Generator <https://openapi-generator.tech/>`_
- `Swagger UI <https://swagger.io/tools/swagger-ui/>`_