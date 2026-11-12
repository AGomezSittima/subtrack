import type { paths } from "@subtrack/contract";
import createClient from "openapi-fetch";

/** Typed client for /api/v1. Only feature `api/` hooks use it; components never fetch. */
export const apiClient = createClient<paths>({ baseUrl: "/api/v1" });
