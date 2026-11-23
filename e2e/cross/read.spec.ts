import { env } from "../support/env";
import { expect, test } from "../support/fixtures";
import { loadState } from "./state";

test("NFR-6 NFR-9 the other backend reads the catalogue written by the first identically", async ({
  api,
}) => {
  const state = loadState();

  const { data: health } = await api.GET("/health");
  const { data: catalog } = await api.GET("/catalog", {
    params: { query: { include_hidden: true } },
  });
  const { data: visible } = await api.GET("/catalog");

  expect(health?.backend).not.toBe(state.writer);
  expect(health?.backend).toBe(env.expectedBackend ?? health?.backend);
  expect(catalog).toEqual(state.catalog);
  expect(visible).toEqual(state.visible);
});
