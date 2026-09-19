const { test } = require('node:test');
const assert = require('node:assert/strict');
const d = require('../domain');
test('anonymous, unverified and stale identities cannot perform privileged actions', () => {
  assert.throws(() => d.actor({}), { code: 'unauthenticated' });
  assert.throws(() => d.actor({ auth: { uid: 'u', token: {} } }), { code: 'failed-precondition' });
  assert.throws(() => d.actor({ auth: { uid: 'u', token: { email_verified: true, auth_time: 1 } } }, { recent: true }), { code: 'failed-precondition' });
});
test('listing payloads cannot forge ownership or status', () => {
  const result = d.listing({ kind: 'ROOM', title: 'Sunny room', city: 'Anchorage', description: 'A comfortable room near downtown.', monthlyRentUsd: 700, ownerUid: 'victim', status: 'approved' });
  assert.equal(result.ownerUid, undefined); assert.equal(result.status, undefined);
  for (const price of [-1, 0, 1.1, '700', Infinity]) assert.throws(() => d.listing({ ...result, monthlyRentUsd: price }));
});
test('consent, identifier traversal and abusive text are rejected', () => {
  assert.throws(() => d.profile({ displayName: 'Name', city: 'Kenai', termsAccepted: false }));
  for (const bad of ['../victim', 'a/b', '']) assert.throws(() => d.id(bad));
  assert.throws(() => d.text('hello\u0000there', 'Message', 1, 100));
});
test('conversation identifiers are deterministic and isolate listings', () => {
  assert.equal(d.conversationId('listing1', ['alice', 'bob']), d.conversationId('listing1', ['bob', 'alice']));
  assert.notEqual(d.conversationId('listing1', ['alice', 'bob']), d.conversationId('listing2', ['alice', 'bob']));
});
test('quota cannot be exceeded and resets after its window', () => {
  let state; for (let i = 0; i < 3; i++) state = d.nextQuota(state, 1000, 3);
  assert.throws(() => d.nextQuota(state, 1000, 3), { code: 'resource-exhausted' });
  assert.equal(d.nextQuota(state, 62000, 3).count, 1);
});
test('listing limits depend only on an unexpired server entitlement', () => {
  assert.equal(d.activeListingLimit(undefined, 1000), 1);
  assert.equal(d.activeListingLimit({ tier: 'plus', activeUntil: 999 }, 1000), 1);
  assert.equal(d.activeListingLimit({ tier: 'plus', activeUntil: 1001 }, 1000), 10);
  assert.equal(d.activeListingLimit({ tier: 'property_manager', activeUntil: { toMillis: () => 1001 } }, 1000), 25);
  assert.equal(d.activeListingLimit({ tier: 'forged', activeUntil: 999999 }, 1000), 1);
});
