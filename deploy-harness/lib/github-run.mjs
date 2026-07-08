import { writeFileSync, appendFileSync } from 'node:fs';
import { env } from 'node:process';

/**
 * Write a step output to GITHUB_OUTPUT (or stdout if not set).
 * @param {string} name
 * @param {string} value
 */
export function setOutput(name, value) {
  const outputFile = env.GITHUB_OUTPUT;
  if (outputFile) {
    appendFileSync(outputFile, `${name}=${value}\n`, 'utf8');
  } else {
    console.log(`[output] ${name}=${value}`);
  }
}

/**
 * Emit a warning annotation.
 * @param {string} message
 */
export function warn(message) {
  console.warn(`::warning::${message}`);
}

/**
 * Emit an error annotation.
 * @param {string} message
 */
export function error(message) {
  console.error(`::error::${message}`);
}

/**
 * Require an environment variable, throw if missing.
 * @param {string} name
 * @returns {string}
 */
export function requireEnv(name) {
  const val = env[name];
  if (val === undefined || val === '') {
    throw new Error(`Required environment variable ${name} is not set`);
  }
  return val;
}

/**
 * Get an environment variable with optional fallback.
 * @param {string} name
 * @param {string} [fallback]
 * @returns {string}
 */
export function getEnv(name, fallback = '') {
  return env[name] ?? fallback;
}
