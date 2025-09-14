import { serve } from '@hono/node-server'
import { createApp } from './openapi.js'

const app = createApp()

serve({
  fetch: app.fetch,
  port: 8000
}, (info) => {
  console.log(`Server is running on http://localhost:${info.port}`)
})
