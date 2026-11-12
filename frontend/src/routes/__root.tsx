import type { QueryClient } from "@tanstack/react-query";
import { createRootRouteWithContext, Outlet } from "@tanstack/react-router";

import { BackendFooter } from "@/features/health/components/backend-footer";

export interface RouterContext {
  queryClient: QueryClient;
}

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootLayout,
});

function RootLayout() {
  return (
    <div className="flex min-h-svh flex-col">
      <main className="flex flex-1 items-center justify-center p-4">
        <Outlet />
      </main>
      <BackendFooter />
    </div>
  );
}
