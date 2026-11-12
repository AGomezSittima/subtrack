import { createFileRoute } from "@tanstack/react-router";

import { healthQueryOptions } from "@/features/health/api/use-health";
import { BackendStatus } from "@/features/health/components/backend-status";

export const Route = createFileRoute("/")({
  // Returns cached data or fetches it (the replacement for the deprecated ensureQueryData).
  loader: ({ context }) =>
    context.queryClient.query({ ...healthQueryOptions, staleTime: "static" }),
  // BackendStatus renders the pending and error states of the same query itself.
  component: BackendStatus,
  pendingComponent: BackendStatus,
  errorComponent: BackendStatus,
});
