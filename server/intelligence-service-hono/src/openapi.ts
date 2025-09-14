import { OpenAPIHono, createRoute, z } from '@hono/zod-openapi'
import type { Context } from 'hono'
import YAML from 'yaml'
import { swaggerUI } from '@hono/swagger-ui'

// Initialize OpenAPI-enabled Hono app
export const createApp = () => {
  const app = new OpenAPIHono()

  // Register common components (e.g., error schema)
  const ErrorSchema = z
    .object({
      code: z.number().openapi({ example: 400 }),
      message: z.string().openapi({ example: 'Bad Request' }),
    })
    .openapi('Error')
  app.openAPIRegistry.register('Error', ErrorSchema)

  // Security scheme (Bearer)
  app.openAPIRegistry.registerComponent('securitySchemes', 'Bearer', {
    type: 'http',
    scheme: 'bearer',
  })

  // Health route
  const healthRoute = createRoute({
    method: 'get',
    path: '/health',
    responses: {
      200: {
        description: 'Service health status',
        content: {
          'application/json': {
            schema: z
              .object({ status: z.literal('ok'), service: z.string() })
              .openapi('HealthResponse'),
          },
        },
      },
    },
    tags: ['system'],
  })

  app.openapi(healthRoute, (c: Context) =>
    c.json({ status: 'ok', service: 'intelligence-service-hono' }, 200)
  )

  // Root route (simple text)
  const rootRoute = createRoute({
    method: 'get',
    path: '/',
    responses: {
      200: {
        description: 'Welcome',
        content: { 'text/plain': { schema: z.string().openapi({ example: 'Hello Hono!' }) } },
      },
    },
    tags: ['system'],
  })
  app.openapi(rootRoute, (c: Context) => c.text('Hello Hono!', 200))

  // Swagger UI at /docs (loads spec from YAML endpoint)
  app.get('/docs', swaggerUI({ url: '/openapi.yaml' }))

  app.get('/openapi.yaml', (c: Context) => {
    const doc = app.getOpenAPI31Document({
      openapi: '3.1.0',
      info: {
        title: 'Hephaestus Intelligence Service (Hono) API',
        version: '0.9.0-rc.27',
      },
    })
    const body = YAML.stringify(doc)
    return c.body(body, 200, { 'content-type': 'application/yaml; charset=utf-8' })
  })

  return app
}

export type AppType = ReturnType<typeof createApp>
