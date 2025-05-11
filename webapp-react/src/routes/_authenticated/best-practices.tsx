import { createFileRoute } from '@tanstack/react-router'

export const Route = createFileRoute('/_authenticated/best-practices')({
  component: RouteComponent,
})

function RouteComponent() {
  return <div>Hello "/_authenticated/best-practices"!</div>
}
