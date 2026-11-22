import { expect, test as base } from "@playwright/test";

import { type ApiClient, createApiClient } from "./api";
import { env } from "./env";

interface Fixtures {
  api: ApiClient;
  freshDatabase: undefined;
}

export const test = base.extend<Fixtures>({
  api: async ({ baseURL }, use) => {
    await use(createApiClient(baseURL ?? env.baseUrl));
  },
  freshDatabase: [
    async ({ api }, use) => {
      const { response } = await api.POST("/admin/reset");
      expect(response.status, "POST /admin/reset").toBe(204);
      await use(undefined);
    },
    { auto: true },
  ],
});

export { expect };
