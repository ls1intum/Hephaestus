---
title: Client Guidelines
description: Patterns for building the Hephaestus web client.
---

This section contains guidelines for designing the client side of the application. The client uses modern web technologies and follows best practices for performance, accessibility, and user experience.

### Client-Side Data Handling

When working with data from the server, the client should use the auto-generated TypeScript interfaces from the OpenAPI specification. This ensures type safety and consistency between the client and server.

Example:

```typescript
// This is auto-generated from the OpenAPI specification
// Note that the DTO ending is omitted by the generator
export interface Example {
    name: string;
}

// example.component.ts
@Component({
    selector: 'app-example',
    templateUrl: './example.component.html',
})
export class ExampleComponent {
    // type-safe access to the server
    exampleService = inject(ExampleService);

    // Use Tanstack Query to fetch the actual data
    // The data now inherits the DTO structure and can be accessed in a type-safe manner
    example = injectQuery(() => ({
        queryKey: ['example'],
        // note that this will be correctly typed as Example
        queryFn: async () => lastValueFrom(this.exampleService.getExampleByName('example')),
    }));
}
``` 