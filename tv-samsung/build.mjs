// Buduje js/app.js zgodny ze starszymi telewizorami Samsung (Tizen 3.0 / Chromium 47+).
import * as esbuild from "esbuild";
import babel from "@babel/core";
import fs from "node:fs";

const bundled = await esbuild.build({
  entryPoints: ["src/main.js"],
  bundle: true,
  format: "iife",
  target: "es2017",
  write: false,
  charset: "utf8",
});

const result = await babel.transformAsync(bundled.outputFiles[0].text, {
  babelrc: false,
  configFile: false,
  compact: false,
  sourceType: "script",
  presets: [["@babel/preset-env", { targets: { chrome: "47" }, modules: false, bugfixes: true }]],
});

fs.mkdirSync("app/js", { recursive: true });
fs.writeFileSync("app/js/app.js", '"use strict";\n' + result.code);
console.log("Zbudowano app/js/app.js (" + Math.round(result.code.length / 1024) + " KB)");
