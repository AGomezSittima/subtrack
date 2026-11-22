import type { paths } from "@subtrack/contract";
import createClient, { type Client } from "openapi-fetch";

export type ApiClient = Client<paths>;

export const API_PREFIX = "/api/v1";

export function createApiClient(baseUrl: string): ApiClient {
  return createClient<paths>({ baseUrl: `${baseUrl}${API_PREFIX}` });
}
