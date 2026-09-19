const { initializeApp } = require('firebase-admin/app');
const { getFirestore, Timestamp } = require('firebase-admin/firestore');
const { getAuth } = require('firebase-admin/auth');
const { getMessaging } = require('firebase-admin/messaging');
const { setGlobalOptions } = require('firebase-functions/v2');
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { onDocumentCreated } = require('firebase-functions/v2/firestore');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const logger = require('firebase-functions/logger');
const { createService } = require('./service');
const { DomainError } = require('./domain');
initializeApp();
setGlobalOptions({ region: 'us-west1', maxInstances: 10, memory: '256MiB', timeoutSeconds: 60 });
const db = getFirestore();
const service = createService({ db, auth: getAuth(), messaging: getMessaging() });
for (const [name, handler] of Object.entries(service.handlers)) {
  exports[name] = onCall({ enforceAppCheck: true }, async request => {
    try { return await handler(request); }
    catch (error) {
      if (error instanceof DomainError) throw new HttpsError(error.code, error.message);
      logger.error('Callable failed', { action: name, code: error.code ?? 'unknown' });
      throw new HttpsError('internal', 'Unable to complete this request. Please try again.');
    }
  });
}
exports.processAccountDeletion = onDocumentCreated(
  { document: '_deletions/{uid}', retry: true, timeoutSeconds: 540, maxInstances: 2 },
  async event => { if (event.data?.data().status === 'pending') await service.deleteUserData(event.params.uid); }
);
exports.messageNotification = onDocumentCreated(
  { document: 'conversations/{cid}/messages/{mid}', retry: true },
  async event => { if (event.data) await service.notifyMessage(event.params.cid, event.data.data()); }
);
exports.expireListings = onSchedule('every 30 minutes', async () => {
  const rows = await db.collection('listings').where('status', '==', 'active')
    .where('expiresAt', '<=', Timestamp.now()).limit(200).get();
  const batch = db.batch();
  for (const row of rows.docs) batch.update(row.ref, { status: 'expired' });
  if (!rows.empty) await batch.commit();
});

// Firebase Auth can be deleted outside this app; queue the same cleanup worker.
const legacy = require('firebase-functions/v1');
exports.authDeletedCleanup = legacy.region('us-west1').runWith({ failurePolicy: true }).auth.user().onDelete(async user => {
  await db.runTransaction(async tx => {
    const ref = db.doc('_deletions/' + user.uid);
    const previous = await tx.get(ref);
    if (previous.exists) return;
    tx.set(db.doc('users/' + user.uid), { status: 'deleting' }, { merge: true });
    tx.create(ref, { status: 'pending', createdAt: Timestamp.now() });
  });
});
