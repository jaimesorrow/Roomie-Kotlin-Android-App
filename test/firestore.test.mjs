import { readFileSync } from 'node:fs';
import { test, before, after } from 'node:test';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { collection, doc, getDoc, getDocs, query, where, limit, setDoc } from 'firebase/firestore';
let env;
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-roomie',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') } });
  await env.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    for (const uid of ['alice', 'bob', 'outsider']) await setDoc(doc(db, 'users/' + uid), { status: 'active' });
    await setDoc(doc(db, 'users/deleting'), { status: 'deleting' });
    await setDoc(doc(db, 'listings/active'), { ownerUid: 'alice', status: 'active' });
    await setDoc(doc(db, 'listings/closed'), { ownerUid: 'bob', status: 'closed' });
    await setDoc(doc(db, 'conversations/thread'), { memberUids: ['alice', 'bob'], contactAllowed: true });
    await setDoc(doc(db, 'conversations/thread/messages/m1'), { text: 'Private message' });
    await setDoc(doc(db, 'publicProfiles/alice'), { displayName: 'Alice', bio: 'Room seeker' });
    await setDoc(doc(db, 'users/alice/devices/private'), { token: 'private' });
    await setDoc(doc(db, 'reports/report1'), { status: 'open' });
    await setDoc(doc(db, 'entitlements/alice'), { tier: 'plus' });
    await setDoc(doc(db, '_deletions/private'), { status: 'pending' });
  });
});
after(async () => { await env?.cleanup(); });
const dbFor = (uid, verified = true, extra = {}) => env.authenticatedContext(uid, { email_verified: verified, ...extra }).firestore();
test('anonymous, unverified and deleting accounts cannot browse', async () => {
  for (const db of [env.unauthenticatedContext().firestore(), dbFor('alice', false), dbFor('deleting')])
    await assertFails(getDoc(doc(db, 'listings/active')));
});
test('listing reads must follow visibility and pagination constraints', async () => {
  const db = dbFor('alice');
  await assertSucceeds(getDocs(query(collection(db, 'listings'), where('status', '==', 'active'), limit(50))));
  await assertFails(getDocs(collection(db, 'listings')));
  await assertFails(getDoc(doc(db, 'listings/closed')));
  await assertSucceeds(getDoc(doc(dbFor('bob'), 'listings/closed')));
});
test('participants can read their thread and outsiders cannot read or enumerate it', async () => {
  await assertSucceeds(getDoc(doc(dbFor('alice'), 'conversations/thread/messages/m1')));
  await assertFails(getDoc(doc(dbFor('outsider'), 'conversations/thread/messages/m1')));
  await assertFails(getDocs(query(collection(dbFor('outsider'), 'conversations'), limit(50))));
  await assertSucceeds(getDocs(query(collection(dbFor('alice'), 'conversations'), where('memberUids', 'array-contains', 'alice'), limit(50))));
});
test('no client can forge a listing, message, profile, report or device', async () => {
  const db = dbFor('alice');
  for (const path of ['listings/active', 'conversations/thread/messages/forged', 'publicProfiles/alice', 'users/alice', 'reports/forged', 'users/alice/devices/private'])
    await assertFails(setDoc(doc(db, path), { forged: true }));
});
test('private accounts, tokens, reports and deletion records are isolated', async () => {
  const db = dbFor('bob');
  for (const path of ['users/alice', 'users/alice/devices/private', 'reports/report1', '_deletions/private'])
    await assertFails(getDoc(doc(db, path)));
  await assertSucceeds(getDoc(doc(db, 'publicProfiles/alice')));
  await assertSucceeds(getDoc(doc(dbFor('moderator', true, { moderator: true }), 'reports/report1')));
});
test('users can read only their own server-managed entitlement', async () => {
  await assertSucceeds(getDoc(doc(dbFor('alice'), 'entitlements/alice')));
  await assertFails(getDoc(doc(dbFor('bob'), 'entitlements/alice')));
  await assertFails(setDoc(doc(dbFor('alice'), 'entitlements/alice'), { tier: 'property_manager' }));
});
