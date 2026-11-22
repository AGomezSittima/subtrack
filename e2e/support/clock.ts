import type { ApiClient } from "./api";

export async function setClock(
  api: ApiClient,
  now: Temporal.Instant,
): Promise<void> {
  const { response } = await api.POST("/admin/clock", {
    body: { now: now.toString({ fractionalSecondDigits: 3 }) },
  });
  if (response.status !== 204) {
    throw new Error(`Setting the clock failed with ${String(response.status)}`);
  }
}
