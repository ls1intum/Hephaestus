import { createFileRoute, redirect } from '@tanstack/react-router'
import { useAuth } from '@/integrations/auth/AuthContext';

export const Route = createFileRoute('/_authenticated/best-practices')({
  loader: ({ context }) => {
      if (context.auth?.username) {
        redirect({
          to: '/user/$username/best-practices',
          throw: true,
          params: { username: context.auth.username }
        })
      }
    }
  }
);
