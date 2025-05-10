import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/_authenticated/workspace/teams')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/_authenticated/workspace/teams"!</div>
}
