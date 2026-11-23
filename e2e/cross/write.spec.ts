import { setClock } from "../support/clock";
import { expect, test } from "../support/fixtures";
import { present, saveState } from "./state";

test("writes catalogue data for the other backend to read", async ({ api }) => {
  await setClock(api, Temporal.Instant.from("2026-11-23T10:00:00.123Z"));
  const gym = await api.POST("/catalog", {
    body: {
      name: "Gym ñ",
      domain: "gym.example",
      category: "other",
      plans: [
        {
          plan: "Monthly",
          price_cents: 3500,
          interval_count: 1,
          interval_unit: "month",
        },
        { plan: "Yearly", interval_count: 1, interval_unit: "year" },
      ],
    },
  });
  const temporary = await api.POST("/catalog", {
    body: { name: "Temporary", category: "news", plans: [] },
  });
  expect([gym.response.status, temporary.response.status]).toEqual([201, 201]);

  await setClock(api, Temporal.Instant.from("2026-11-24T08:30:00.456Z"));
  const { data: spotify } = await api.GET("/catalog", {
    params: { query: { q: "spotify" } },
  });
  const { data: netflix } = await api.GET("/catalog", {
    params: { query: { q: "netflix" } },
  });
  const edits = await Promise.all([
    api.PATCH("/catalog/{id}", {
      params: { path: { id: spotify?.items[0]?.id ?? 0 } },
      body: {
        name: "Spotify Premium",
        plans: [
          {
            plan: "Premium Individual",
            price_cents: 1199,
            interval_count: 1,
            interval_unit: "month",
          },
        ],
      },
    }),
    api.PATCH("/catalog/{id}", {
      params: { path: { id: netflix?.items[0]?.id ?? 0 } },
      body: { hidden: true, domain: null },
    }),
    api.DELETE("/catalog/{id}", {
      params: { path: { id: temporary.data?.id ?? 0 } },
    }),
  ]);
  expect(edits.map(({ response }) => response.status)).toEqual([200, 200, 204]);

  const { data: health } = await api.GET("/health");
  const { data: catalog } = await api.GET("/catalog", {
    params: { query: { include_hidden: true } },
  });
  const { data: visible } = await api.GET("/catalog");
  expect(catalog?.items).toHaveLength(55);
  expect(visible?.items).toHaveLength(54);
  saveState({
    writer: present(health).backend,
    catalog: present(catalog),
    visible: present(visible),
  });
});
