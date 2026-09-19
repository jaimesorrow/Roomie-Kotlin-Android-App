const { test, before, after } = require('node:test');
const assert = require('node:assert/strict');
const { initializeApp, deleteApp, getApps } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const { createService } = require('../service');
let app, db, service;
const deletedUsers = [];
const request = (uid, data = {}) => ({ auth: { uid, token: { email_verified: true, auth_time: Math.floor(Date.now()/1000) } }, data });
before(async () => {
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Use the Firestore emulator for integration tests.');
  app = initializeApp({ projectId: 'demo-roomie' }, 'integration');
  db = getFirestore(app);
  service = createService({ db, auth: { deleteUser: async uid => { deletedUsers.push(uid); } }, messaging: {} });
  for (const uid of ['owner', 'seeker', 'stranger']) await service.handlers.saveProfile(request(uid,
    { displayName: uid, city: 'Anchorage', bio: '', termsVersion: 1, termsAccepted: true }));
  await service.handlers.publishListing(request('owner', { id: 'available-room', kind: 'ROOM', title: 'Sunny room available',
    city: 'Anchorage', monthlyRentUsd: 700, description: 'Private room near downtown with shared kitchen.' }));
});
after(async () => { await Promise.all(getApps().map(deleteApp)); });
test('ownership, private contact, idempotent delivery, blocking and deletion are enforced', async () => {
  await assert.rejects(service.handlers.publishListing(request('stranger', { id: 'available-room', kind: 'ROOM',
    title: 'Stolen listing', city: 'Anchorage', monthlyRentUsd: 1, description: 'Attempting to replace someone else listing.' })), { code: 'permission-denied' });
  await assert.rejects(service.handlers.publishListing(request('owner', { id: 'second-free-room', kind: 'ROOM',
    title: 'Second free listing', city: 'Anchorage', monthlyRentUsd: 800, description: 'Free accounts may keep one active listing at a time.' })), { code: 'resource-exhausted' });
  await db.doc('entitlements/owner').set({ tier: 'plus', activeUntil: Timestamp.fromMillis(Date.now() + 86400000), boostCredits: 0 });
  await service.handlers.publishListing(request('owner', { id: 'second-plus-room', kind: 'ROOM',
    title: 'Second Plus listing', city: 'Anchorage', monthlyRentUsd: 800, description: 'A verified Plus entitlement permits additional active listings.' }));
  const { id } = await service.handlers.startConversation(request('seeker', { listingId: 'available-room' }));
  assert.equal((await service.handlers.startConversation(request('seeker', { listingId: 'available-room' }))).id, id);
  await assert.rejects(service.handlers.sendMessage(request('stranger', { conversationId: id, id: 'm1', text: 'Intrusion' })), { code: 'permission-denied' });
  const message = request('seeker', { conversationId: id, id: 'm1', text: 'Is the room available?' });
  await service.handlers.sendMessage(message); await service.handlers.sendMessage(message);
  assert.equal((await db.collection('conversations/' + id + '/messages').get()).size, 1);
  await assert.rejects(service.handlers.sendMessage(request('seeker', { conversationId: id, id: 'm1', text: 'Different text' })), { code: 'already-exists' });
  await service.handlers.blockUser(request('owner', { uid: 'seeker' }));
  await assert.rejects(service.handlers.sendMessage(request('seeker', { conversationId: id, id: 'm2', text: 'Blocked contact' })), { code: 'permission-denied' });
  await assert.rejects(service.handlers.startConversation(request('seeker', { listingId: 'available-room' })), { code: 'permission-denied' });
  await service.handlers.deleteAccount(request('owner', { uid: 'stranger' }));
  assert.equal((await db.doc('_deletions/owner').get()).data().expiresAt, undefined);
  await assert.rejects(service.handlers.publishListing(request('owner', { id: 'new-room', kind: 'ROOM', title: 'New room',
    city: 'Anchorage', monthlyRentUsd: 700, description: 'This should fail while deletion is pending.' })), { code: 'failed-precondition' });
  await service.deleteUserData('owner'); await service.deleteUserData('owner');
  assert.equal((await db.doc('listings/available-room').get()).exists, false);
  assert.equal((await db.doc('conversations/' + id).get()).exists, false);
  assert.equal((await db.collection('conversations/' + id + '/messages').get()).size, 0);
  assert.equal((await db.doc('users/owner').get()).exists, false);
  assert.equal((await db.doc('publicProfiles/owner').get()).exists, false);
  assert.equal((await db.doc('entitlements/owner').get()).exists, false);
  assert.equal(deletedUsers.every(uid => uid === 'owner'), true);
  assert.equal((await db.doc('users/stranger').get()).data().status, 'active');
  await assert.rejects(service.handlers.saveProfile(request('owner', { displayName: 'Return', city: 'Kenai', bio: '', termsVersion: 1, termsAccepted: true })), { code: 'failed-precondition' });
});
test('expired listings cannot create new contact', async () => {
  await db.doc('listings/expired').set({ ownerUid: 'stranger', status: 'active', expiresAt: Timestamp.fromMillis(1) });
  await assert.rejects(service.handlers.startConversation(request('seeker', { listingId: 'expired' })), { code: 'not-found' });
});

test('direct Auth deletion queues a durable cleanup and blocks old-token recreation', async () => {
  const uid = 'externally-deleted';
  await service.handlers.saveProfile(request(uid, { displayName: 'External', city: 'Kenai', bio: '', termsVersion: 1, termsAccepted: true }));
  await require('../index').authDeletedCleanup.run({ uid });
  const job = (await db.doc('_deletions/' + uid).get()).data();
  assert.equal(job.status, 'pending'); assert.equal(job.expiresAt, undefined);
  await assert.rejects(service.handlers.saveProfile(request(uid, { displayName: 'Return', city: 'Kenai', bio: '', termsVersion: 1, termsAccepted: true })), { code: 'failed-precondition' });
  await service.deleteUserData(uid);
  assert.equal((await db.doc('_deletions/' + uid).get()).data().status, 'complete');
});
