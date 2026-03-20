# Lessons Learned

## NEVER stop infrastructure services
- **DO NOT** stop Coolify proxy, databases, or other shared infra to free ports
- Instead, change the port of the *tool you're running* via env vars
- Example: `SERVER_PORT=8090 MANAGEMENT_SERVER_PORT=8092 mvn verify -DskipTests=true -Dapp.profiles=specs`

## OpenAPI spec generation
- springdoc plugin downloads spec from `http://localhost:8080/v3/api-docs.yaml` by default
- If port 8080 is occupied, override with `SERVER_PORT=<free-port>` — never kill the occupier
- After regenerating spec, also regenerate the client: `npm run generate:api:application-server:client`

## Maven in worktree
- Use `cd server/application-server` first, or the `-pl` flag won't resolve modules from the worktree root
- `mvn clean` deletes generated GraphQL sources — run `mvn generate-sources` afterwards

## Architecture tests
- Run with `-Dsurefire.includedGroups="architecture"` (not `"unit"`)
- Max 6 parameters per `@Bean` factory method enforced by arch tests

## DB model regeneration
- Requires a fresh DB: `docker compose down -v && docker compose up -d`
- Then start app server to apply Liquibase, then run `npm run db:generate-erd-docs` and `npm run db:generate-models:intelligence-service`
