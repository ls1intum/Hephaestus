import { writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import YAML from 'yaml'
import { createApp } from '../src/openapi.js'

async function main() {
  const app = createApp()
  const doc = app.getOpenAPI31Document({
    openapi: '3.1.0',
    info: {
      title: 'Hephaestus Intelligence Service (Hono) API',
      version: '0.9.0-rc.27',
    },
  })

  const yaml = YAML.stringify(doc)
  const outPath = resolve(process.cwd(), 'openapi.yaml')
  writeFileSync(outPath, yaml, { encoding: 'utf-8' })
  // eslint-disable-next-line no-console
  console.log(`OpenAPI spec written to ${outPath}`)
}

main().catch((err) => {
  // eslint-disable-next-line no-console
  console.error('Failed to export OpenAPI spec:', err)
  process.exit(1)
})
