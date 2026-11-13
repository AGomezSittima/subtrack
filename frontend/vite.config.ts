import { fileURLToPath, URL } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import { tanstackRouter } from "@tanstack/router-plugin/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [
    // The router plugin must come before the React plugin.
    tanstackRouter({
      target: "react",
      autoCodeSplitting: true,
      // A route's component and its pending/error states load together, in one chunk.
      codeSplittingOptions: {
        defaultBehavior: [
          [
            "component",
            "pendingComponent",
            "errorComponent",
            "notFoundComponent",
          ],
        ],
      },
    }),
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) },
  },
  server: {
    // `pnpm dev` talks to a stack started with `just up <profile>`.
    proxy: { "/api": "http://127.0.0.1:8080" },
  },
});
