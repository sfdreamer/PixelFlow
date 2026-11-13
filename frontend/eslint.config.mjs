import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

export default defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    rules: {
      // Result/cover previews intentionally use <img>: the Next image optimizer would fetch the
      // authenticated URL without the session cookie.
      "@next/next/no-img-element": "off",
      // Pages load same-origin API data after mount (the session cookie is issued by POST /session),
      // so a data-fetch effect that sets loading state is the intended pattern here.
      "react-hooks/set-state-in-effect": "off",
    },
  },
  globalIgnores([".next/**", "out/**", "node_modules/**", "next-env.d.ts"]),
]);
