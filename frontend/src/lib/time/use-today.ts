import { useQuery } from "@tanstack/react-query";

import { healthQueryOptions } from "@/features/health/api/use-health";

const ONE_MINUTE_MS = 60_000;

/** Today in the home time zone, as the backend sees it; never the device clock. */
export function useToday() {
  return useQuery({
    ...healthQueryOptions,
    select: (health) => health.today,
    refetchInterval: ONE_MINUTE_MS,
    refetchOnWindowFocus: true,
  });
}
