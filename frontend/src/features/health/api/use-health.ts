import type { components } from "@subtrack/contract";
import { queryOptions, useQuery } from "@tanstack/react-query";

import { apiClient } from "@/lib/api/client";

type HealthDto = components["schemas"]["Health"];

export type Backend = HealthDto["backend"];

export interface Health {
  backend: Backend;
  version: string;
  lastSchedulerRun: Temporal.Instant | null;
}

function toHealth(dto: HealthDto): Health {
  return {
    backend: dto.backend,
    version: dto.version,
    lastSchedulerRun:
      dto.last_scheduler_run === null
        ? null
        : Temporal.Instant.from(dto.last_scheduler_run),
  };
}

export const healthQueryOptions = queryOptions({
  queryKey: ["health"],
  queryFn: async ({ signal }) => {
    const { data, response } = await apiClient.GET("/health", { signal });
    if (data === undefined) {
      throw new Error(
        `GET /health failed with HTTP ${String(response.status)}`,
      );
    }
    return toHealth(data);
  },
});

export function useHealth() {
  return useQuery(healthQueryOptions);
}
