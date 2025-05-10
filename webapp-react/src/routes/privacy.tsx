import { createFileRoute } from "@tanstack/react-router";
import { PrivacyContainer } from "@/features/info/legal/PrivacyContainer";

export const Route = createFileRoute("/privacy")({
  component: PrivacyContainer,
});
