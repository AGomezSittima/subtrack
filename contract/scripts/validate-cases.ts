import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

import { Ajv2020 } from "ajv/dist/2020.js";

interface CaseFile {
  rules: string[];
  cases: { id: string; rules: string[] }[];
}

const casesDir = join(import.meta.dirname, "..", "cases");
const schemaDir = join(casesDir, "schema");

function readJson(path: string): unknown {
  return JSON.parse(readFileSync(path, "utf8"));
}

function isIsoDate(value: string): boolean {
  try {
    return (
      Temporal.PlainDate.from(value, { overflow: "reject" }).toString() ===
      value
    );
  } catch {
    return false;
  }
}

const ajv = new Ajv2020({
  allErrors: true,
  strict: true,
  formats: { date: isIsoDate },
});

const schemaFiles = readdirSync(schemaDir).filter((file) =>
  file.endsWith(".schema.json"),
);
for (const file of schemaFiles) {
  ajv.addSchema(readJson(join(schemaDir, file)) as object, file);
}

const problems: string[] = [];
const caseFiles = readdirSync(casesDir).filter((file) =>
  file.endsWith(".json"),
);

for (const file of caseFiles) {
  const schemaName = file.replace(/\.json$/, ".schema.json");
  if (!schemaFiles.includes(schemaName)) {
    problems.push(`${file}: no schema at cases/schema/${schemaName}`);
    continue;
  }
  const validate = ajv.compile<CaseFile>({ $ref: schemaName });
  const data = readJson(join(casesDir, file));
  if (!validate(data)) {
    for (const error of validate.errors ?? []) {
      problems.push(
        `${file}${error.instancePath}: ${error.message ?? "is invalid"}`,
      );
    }
    continue;
  }

  const seen = new Set<string>();
  for (const { id } of data.cases) {
    if (seen.has(id)) {
      problems.push(`${file}: duplicate case id "${id}"`);
    }
    seen.add(id);
  }

  const covered = new Set(data.cases.flatMap((testCase) => testCase.rules));
  for (const rule of data.rules) {
    if (!covered.has(rule)) {
      problems.push(`${file}: no case covers ${rule}`);
    }
  }
}

for (const schemaName of schemaFiles) {
  const caseFile = schemaName.replace(/\.schema\.json$/, ".json");
  if (schemaName !== "common.schema.json" && !caseFiles.includes(caseFile)) {
    problems.push(`cases/schema/${schemaName}: no case file cases/${caseFile}`);
  }
}

if (problems.length > 0) {
  console.error(problems.join("\n"));
  process.exitCode = 1;
} else {
  console.log(`${String(caseFiles.length)} case files are valid.`);
}
