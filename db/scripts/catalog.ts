import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { DatabaseSync } from "node:sqlite";
import { isDeepStrictEqual } from "node:util";

import { Ajv2020 } from "ajv/dist/2020.js";

export interface CatalogPlan {
  plan: string;
  interval_count: number;
  interval_unit: string;
}

export interface CatalogEntry {
  key: string;
  name: string;
  domain: string;
  category: string;
  plans: CatalogPlan[];
}

export interface Migration {
  version: number;
  path: string;
}

interface InstalledBuiltin {
  name: string;
  domain: string | null;
  category: string;
  plans: unknown;
}

const dbDir = join(import.meta.dirname, "..");
export const migrationsDir = join(dbDir, "migrations");
const catalogFile = join(dbDir, "catalog", "catalog.json");
const schemaFile = join(dbDir, "catalog", "catalog.schema.json");

const VERSIONED = /^V(?<version>\d+)__\w+\.sql$/;

function readJson(path: string): unknown {
  return JSON.parse(readFileSync(path, "utf8"));
}

export function loadCatalog(): {
  services: CatalogEntry[];
  problems: string[];
} {
  const ajv = new Ajv2020({ allErrors: true, strict: true });
  const validate = ajv.compile<{ services: CatalogEntry[] }>(
    readJson(schemaFile) as object,
  );
  const data = readJson(catalogFile);
  if (!validate(data)) {
    const problems = (validate.errors ?? []).map(
      (error) =>
        `catalog.json${error.instancePath}: ${error.message ?? "is invalid"}`,
    );
    return { services: [], problems };
  }

  const problems: string[] = [];
  const keys = new Set<string>();
  const names = new Set<string>();
  for (const [index, service] of data.services.entries()) {
    const at = `catalog.json/services/${String(index)}`;
    if (keys.has(service.key)) {
      problems.push(`${at}: duplicate key "${service.key}"`);
    }
    if (names.has(service.name)) {
      problems.push(`${at}: duplicate name "${service.name}"`);
    }
    keys.add(service.key);
    names.add(service.name);
    const plans = new Set<string>();
    for (const [planIndex, plan] of service.plans.entries()) {
      const id = JSON.stringify([
        plan.plan,
        plan.interval_count,
        plan.interval_unit,
      ]);
      if (plans.has(id)) {
        problems.push(
          `${at}/plans/${String(planIndex)}: "${plan.plan}" every ${String(plan.interval_count)} ${plan.interval_unit} is listed twice`,
        );
      }
      plans.add(id);
    }
  }
  return { services: data.services, problems };
}

export function migrations(): Migration[] {
  return readdirSync(migrationsDir)
    .flatMap((file) => {
      const version = VERSIONED.exec(file)?.groups?.version;
      return version === undefined
        ? []
        : [{ version: Number(version), path: join(migrationsDir, file) }];
    })
    .sort((a, b) => a.version - b.version);
}

export function migratedDatabase(): DatabaseSync {
  // Flyway's sqlite-jdbc connection leaves foreign keys off, and the rebuild migrations require it.
  const db = new DatabaseSync(":memory:", {
    enableForeignKeyConstraints: false,
  });
  for (const { path } of migrations()) {
    try {
      db.exec(`BEGIN;\n${readFileSync(path, "utf8")}\nCOMMIT;`);
    } catch (error) {
      if (db.isTransaction) {
        db.exec("ROLLBACK");
      }
      throw new Error(`${path} failed`, { cause: error });
    }
  }
  return db;
}

export function installedBuiltins(
  db: DatabaseSync,
): Map<string, InstalledBuiltin> {
  const rows = db
    .prepare(
      "SELECT builtin_key, name, domain, category, default_plans FROM catalog_service WHERE builtin_key IS NOT NULL",
    )
    .all();
  return new Map(
    rows.map((row) => [
      String(row.builtin_key),
      {
        name: String(row.name),
        domain: row.domain === null ? null : String(row.domain),
        category: String(row.category),
        plans: JSON.parse(String(row.default_plans)),
      },
    ]),
  );
}

export function storedPlans(plans: CatalogPlan[]): {
  plan: string;
  price_cents: null;
  interval_count: number;
  interval_unit: string;
}[] {
  return plans.map((plan) => ({
    plan: plan.plan,
    price_cents: null,
    interval_count: plan.interval_count,
    interval_unit: plan.interval_unit,
  }));
}

export function differences(
  entry: CatalogEntry,
  installed: InstalledBuiltin,
): string[] {
  const expected: InstalledBuiltin = {
    name: entry.name,
    domain: entry.domain,
    category: entry.category,
    plans: storedPlans(entry.plans),
  };
  return (Object.keys(expected) as (keyof InstalledBuiltin)[]).filter(
    (field) => !isDeepStrictEqual(expected[field], installed[field]),
  );
}

export function checkCatalog(): string[] {
  const { services, problems } = loadCatalog();
  if (problems.length > 0) {
    return problems;
  }
  const installed = installedBuiltins(migratedDatabase());
  const keys = new Set(services.map((service) => service.key));
  for (const service of services) {
    const row = installed.get(service.key);
    if (row === undefined) {
      problems.push(
        `"${service.key}" is not in any migration yet: run \`just catalog-migration\``,
      );
      continue;
    }
    for (const field of differences(service, row)) {
      problems.push(
        `"${service.key}": ${field} differs from what its migration inserted; released entries are never edited (docs/adr/0007-catalogue-ownership.md)`,
      );
    }
  }
  for (const key of installed.keys()) {
    if (!keys.has(key)) {
      problems.push(
        `"${key}" is in a migration but no longer in catalog.json; released entries are never removed`,
      );
    }
  }
  return problems;
}
