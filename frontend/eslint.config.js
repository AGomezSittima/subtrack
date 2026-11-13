// ESLint finds problems; Prettier owns formatting (eslint-config-prettier goes last).
import js from "@eslint/js";
import pluginQuery from "@tanstack/eslint-plugin-query";
import pluginRouter from "@tanstack/eslint-plugin-router";
import { defineConfig, globalIgnores } from "eslint/config";
import prettier from "eslint-config-prettier";
import i18next from "eslint-plugin-i18next";
import jsxA11y from "eslint-plugin-jsx-a11y";
import reactHooks from "eslint-plugin-react-hooks";
import simpleImportSort from "eslint-plugin-simple-import-sort";
import unusedImports from "eslint-plugin-unused-imports";
import globals from "globals";
import tseslint from "typescript-eslint";

export default defineConfig(
  globalIgnores(["dist/", "src/routeTree.gen.ts"]),
  {
    files: ["**/*.{js,ts,tsx}"],
    extends: [
      js.configs.recommended,
      tseslint.configs.strictTypeChecked,
      tseslint.configs.stylisticTypeChecked,
    ],
    languageOptions: {
      parserOptions: {
        projectService: { allowDefaultProject: ["eslint.config.js"] },
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      "simple-import-sort": simpleImportSort,
      "unused-imports": unusedImports,
    },
    rules: {
      // Side effects (polyfill first, in source order), packages, `@/` aliases, relative, styles.
      "simple-import-sort/imports": [
        "error",
        {
          groups: [
            ["^\\u0000"],
            ["^node:", "^@?\\w"],
            ["^@/"],
            ["^\\."],
            ["^.+\\.css$"],
          ],
        },
      ],
      "simple-import-sort/exports": "error",
      "@typescript-eslint/no-unused-vars": "off",
      "unused-imports/no-unused-imports": "error",
      "unused-imports/no-unused-vars": [
        "error",
        {
          vars: "all",
          varsIgnorePattern: "^_",
          args: "after-used",
          argsIgnorePattern: "^_",
          caughtErrorsIgnorePattern: "^_",
        },
      ],
    },
  },
  {
    files: ["src/**/*.{ts,tsx}"],
    extends: [
      reactHooks.configs.flat["recommended-latest"],
      jsxA11y.flatConfigs.strict,
      pluginQuery.configs["flat/recommended"],
      pluginRouter.configs["flat/recommended"],
    ],
    languageOptions: { globals: globals.browser },
    plugins: { i18next },
    rules: {
      "i18next/no-literal-string": [
        "error",
        {
          mode: "jsx-only",
          // Only attributes a user can see or hear; data-*, className, variants etc. are code.
          "jsx-attributes": {
            include: [
              "alt",
              "title",
              "label",
              "placeholder",
              "aria-label",
              "aria-description",
              "aria-placeholder",
              "aria-roledescription",
              "aria-valuetext",
            ],
          },
        },
      ],
      "no-restricted-globals": [
        "error",
        {
          name: "Date",
          message:
            "Use Temporal. Date is allowed only in src/components/date-picker.tsx.",
        },
      ],
      "no-restricted-properties": [
        "error",
        {
          object: "Temporal",
          property: "Now",
          message:
            "Read the current time from the injected clock (src/lib/time).",
        },
      ],
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              group: ["@base-ui/*"],
              message: "Import UI primitives from @/components/ui.",
            },
          ],
        },
      ],
    },
  },
  {
    // shadcn/ui code we own: the only place that imports Base UI.
    files: ["src/components/ui/**"],
    rules: { "no-restricted-imports": "off" },
  },
  {
    files: ["src/components/date-picker.tsx"],
    rules: { "no-restricted-globals": "off" },
  },
  {
    files: ["*.config.{js,ts}"],
    languageOptions: { globals: globals.node },
  },
  {
    // Several plugins ship no types, so type-aware rules can't check the config itself.
    files: ["eslint.config.js"],
    extends: [tseslint.configs.disableTypeChecked],
  },
  prettier,
);
