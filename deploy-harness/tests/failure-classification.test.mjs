import { test } from 'node:test';
import assert from 'node:assert/strict';
import { classifyFailure, sanitizeSnippet } from '../lib/failure-classification.mjs';

// T-H6: Failure classification — transient patterns

test('classifyFailure returns transient for Flux-not-ready output', () => {
  const output = 'Timeout waiting for Kustomization agents-api not ready after 5 minutes';
  const result = classifyFailure(output, 'non-playwright', '1/1', 1);
  assert.equal(result.type, 'transient');
  assert.equal(result.pattern, 'flux-not-ready');
  assert.ok(result.snippet.length > 0);
});

test('classifyFailure returns transient for OCIRepository not ready', () => {
  const output = 'Error: OCIRepository auth-api not ready: fetch failed';
  const result = classifyFailure(output, 'non-playwright', '1/1', 1);
  assert.equal(result.type, 'transient');
  assert.equal(result.pattern, 'flux-not-ready');
});

test('classifyFailure returns transient for OOMKilled', () => {
  const output = 'Container terminated: OOMKilled in pod auth-api-6d7f9';
  const result = classifyFailure(output, 'non-playwright', '1/1', 1);
  assert.equal(result.type, 'transient');
  assert.equal(result.pattern, 'oom-killed');
});

test('classifyFailure returns transient for network timeout', () => {
  const output = 'dial tcp 10.0.0.1:443: i/o timeout after 30s';
  const result = classifyFailure(output, 'playwright', '2/4', 1);
  assert.equal(result.type, 'transient');
  assert.equal(result.pattern, 'network-timeout');
});

test('classifyFailure returns transient for DNS resolution failure', () => {
  const output = 'lookup auth.jorisjonkers.test: no such host';
  const result = classifyFailure(output, 'non-playwright', '1/1', 2);
  assert.equal(result.type, 'transient');
  assert.equal(result.pattern, 'dns-resolution');
});

test('classifyFailure returns transient for VSO mount pending', () => {
  const output = 'VaultStaticSecret app-secret not synced: waiting for initial sync';
  const result = classifyFailure(output, 'non-playwright', '1/1', 1);
  assert.equal(result.type, 'transient');
  assert.equal(result.pattern, 'vso-mount-pending');
});

test('classifyFailure returns transient for context deadline exceeded', () => {
  const output = 'context deadline exceeded waiting for Ready condition';
  const result = classifyFailure(output, 'playwright', '3/4', 1);
  assert.equal(result.type, 'transient');
  assert.equal(result.pattern, 'context-cancelled');
});

test('classifyFailure returns deterministic for unknown test assertion failure', () => {
  const output = 'Expected HTTP 200 but got 500 Internal Server Error\nat StackHarness.kt:42\nAssertionError: expected 200 but was 500';
  const result = classifyFailure(output, 'playwright', '2/4', 1);
  assert.equal(result.type, 'deterministic');
  assert.equal(result.pattern, 'deterministic-test-failure');
});

// T-H6: snippet sanitization

test('classifyFailure sanitizes IP addresses from snippet', () => {
  // Use a string that matches the network-timeout pattern
  const output = 'dial tcp 10.0.0.1:8080: i/o timeout';
  const result = classifyFailure(output, 'non-playwright', '1/1', 1);
  assert.equal(result.type, 'transient');
  assert.equal(result.pattern, 'network-timeout');
  assert.ok(!result.snippet.includes('10.0.0.1'), 'IP should be redacted');
  assert.ok(result.snippet.includes('<ip-redacted>'), 'Should have redaction marker');
});

test('sanitizeSnippet redacts Bearer tokens in non-Authorization context', () => {
  // Test with Bearer token NOT prefixed by "Authorization: " so Authorization regex doesn't catch it
  const raw = 'X-Custom-Header: Bearer eyJhbGciOiJSUzI1NiJ9payload';
  const sanitized = sanitizeSnippet(raw);
  assert.ok(!sanitized.includes('eyJhbGci'), 'Bearer token should be redacted');
  assert.ok(sanitized.includes('<token-redacted>'));
});

test('sanitizeSnippet redacts Authorization Bearer via Authorization regex', () => {
  // "Authorization: Bearer ..." is caught by the Authorization header regex (before Bearer regex)
  const raw = 'Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.payload.sig';
  const sanitized = sanitizeSnippet(raw);
  assert.ok(!sanitized.includes('eyJhbGci'), 'token should be redacted');
  assert.ok(sanitized.includes('Authorization: <redacted>'), 'Authorization regex should catch it');
});

test('sanitizeSnippet redacts Authorization header value', () => {
  const raw = 'Authorization: Basic dXNlcjpwYXNzd29yZA==\nContent-Type: application/json';
  const sanitized = sanitizeSnippet(raw);
  assert.ok(!sanitized.includes('dXNlcjpwYXNzd29yZA=='));
  assert.ok(sanitized.includes('Authorization: <redacted>'));
});

test('sanitizeSnippet truncates at 1000 chars', () => {
  const raw = 'x'.repeat(2000);
  const sanitized = sanitizeSnippet(raw);
  assert.equal(sanitized.length, 1000);
});
