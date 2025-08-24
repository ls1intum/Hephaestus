#!/usr/bin/env node
import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';
import { existsSync } from 'node:fs';

const require = createRequire(import.meta.url);

function log(msg) {
  console.log(`[platform-binaries] ${msg}`);
}

function getVersionSafe(getter, label) {
  try {
    const v = getter();
    if (typeof v === 'string' && v.trim()) return v;
  } catch (e) {
    log(`WARN: Unable to resolve version for ${label}: ${e.message}`);
  }
  return '';
}

// Detect platform triplet we support
const isLinux = process.platform === 'linux';
const isX64 = process.arch === 'x64';
const suffix = isLinux && isX64 ? 'linux-x64-gnu' : '';

if (!suffix) {
  log('INFO: Non-linux/x64 platform detected; no native platform binaries to install.');
  process.exit(0);
}

// Resolve versions from installed/declared packages
const rollupVer = getVersionSafe(() => require('rollup/package.json').version, 'rollup');
const lightningCssVer = getVersionSafe(() => require('lightningcss/package.json').version, 'lightningcss');
const biomeVer = getVersionSafe(() => require('../webapp/package.json').devDependencies['@biomejs/biome'], '@biomejs/biome');

const pkgs = [];
if (rollupVer) pkgs.push(`@rollup/rollup-${suffix}@${rollupVer}`);
if (lightningCssVer) pkgs.push(`lightningcss-${suffix}@${lightningCssVer}`);
if (biomeVer) pkgs.push(`@biomejs/cli-${suffix}@${biomeVer}`);

if (pkgs.length === 0) {
  log('INFO: No platform-specific packages to install (versions unresolved).');
  process.exit(0);
}

log(`Installing platform binaries: ${pkgs.join(', ')}`);
try {
  // Install at repo root to match hoisted module locations; avoid lockfile churn
  execSync(`npm i --no-save --no-audit --progress=false ${pkgs.join(' ')}`, {
    stdio: 'inherit',
    env: { ...process.env, npm_config_package_lock: 'false' },
  });
} catch (e) {
  log(`ERROR: Failed to install platform binaries: ${e.status || ''}`);
  process.exit(1);
}

// Verify presence
const verifyPkgs = [
  '@rollup/rollup-linux-x64-gnu',
  'lightningcss-linux-x64-gnu',
  '@biomejs/cli-linux-x64',
];

for (const p of verifyPkgs) {
  try {
    execSync(`npm ls ${p}`, { stdio: 'inherit' });
  } catch {
    log(`WARN: ${p} not found after install (may be unused or version unresolved).`);
  }
}

log('Done installing platform-specific binaries.');
