import { expect, test as base } from "@playwright/test";

import { type ApiClient, createApiClient } from "./api";
import { env } from "./env";

export interface TestOptions {
  /** Reset the database before each test; off only for the cross-backend reader. */
  resetDatabase: boolean;
}

interface Fixtures {
  api: ApiClient;
  freshDatabase: undefined;
}

export const test = base.extend<Fixtures & TestOptions>({
  resetDatabase: [true, { option: true }],
  api: async ({ baseURL }, use) => {
    await use(createApiClient(baseURL ?? env.baseUrl));
  },
  freshDatabase: [
    async ({ api, resetDatabase }, use) => {
      if (resetDatabase) {
        const { response } = await api.POST("/admin/reset");
        expect(response.status, "POST /admin/reset").toBe(204);
      }
      await use(undefined);
    },
    { auto: true },
  ],
});

export { expect };
