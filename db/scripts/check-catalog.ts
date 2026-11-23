import { checkCatalog, loadCatalog } from "./catalog.ts";

const problems = checkCatalog();
if (problems.length > 0) {
  console.error(problems.join("\n"));
  process.exitCode = 1;
} else {
  const { services } = loadCatalog();
  console.log(
    `${String(services.length)} built-in catalogue services match the migrations.`,
  );
}
