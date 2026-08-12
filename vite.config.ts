import { defineConfig } from "vite";

export default defineConfig({
  base: process.env.GITHUB_ACTIONS ? "/BlackDesertEnhancer/" : "/",
  build: {
    target: "es2022",
    sourcemap: true,
  },
});
