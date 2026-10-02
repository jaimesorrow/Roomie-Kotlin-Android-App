const { randomUUID } = require('node:crypto');
const { Timestamp } = require('firebase-admin/firestore');
const d = require('./domain');
function createService({ db, auth, messaging, clock = Date.now }) {
  const userRef = uid => db.doc('users/' + uid);
  const blockedRef = (uid, other) => userRef(uid).collection('blockedUsers').doc(other);
  async function transaction(uid, action, maximum, work, allowMissing = false) {
    return db.runTransaction(async tx => {
      const accountRef = userRef(uid);
      const limitRef = db.doc('_limits/' + uid);
      const [account, deleted, limits] = await tx.getAll(accountRef, db.doc('_deletions/' + uid), limitRef);
      if (deleted.exists || (account.exists && account.data().status !== 'active') || (!allowMissing && !account.exists))
        d.fail('failed-precondition', 'Complete your profile or contact support about your account.');
      const quota = d.nextQuota(limits.data()?.[action], clock(), maximum);
      const result = await work(tx, account.data());
      tx.set(limitRef, { [action]: quota }, { merge: true });
      return result;
    });
  }
  async function contact(tx, uid, other) {
    const [a, b, target] = await tx.getAll(blockedRef(uid, other), blockedRef(other, uid), userRef(other));
    if (a.exists || b.exists || !target.exists || target.data().status !== 'active') d.fail('permission-denied', 'Contact is unavailable.');
    return target.data();
  }
  const handlers = {
    async saveProfile(request) {
      const uid = d.actor(request), data = d.profile(request.data);
      return transaction(uid, 'profile', 10, async (tx, previous) => {
        const now = Timestamp.fromMillis(clock());
        tx.set(userRef(uid), { ...data, status: 'active', termsVersion: 1, termsAcceptedAt: previous?.termsAcceptedAt ?? now, updatedAt: now });
        tx.set(db.doc('publicProfiles/' + uid), { ...data, updatedAt: now });
        return { uid };
      }, true);
    },
    async publishListing(request) {
      const uid = d.actor(request), data = d.listing(request.data), listingId = d.id(request.data.id);
      return transaction(uid, 'listing', 10, async (tx, account) => {
        const ref = db.doc('listings/' + listingId);
        const existing = await tx.get(ref);
        if (existing.exists && existing.data().ownerUid !== uid) d.fail('permission-denied', 'You do not own this listing.');
        if (existing.data()?.status === 'removed') d.fail('permission-denied', 'This listing was removed by moderation.');
        const entitlement = await tx.get(db.doc('entitlements/' + uid));
        const activeLimit = d.activeListingLimit(entitlement.data(), clock());
        const active = await tx.get(db.collection('listings').where('ownerUid', '==', uid)
          .where('status', '==', 'active').limit(activeLimit + 1));
        if ((!existing.exists || existing.data().status !== 'active') && active.size >= activeLimit)
          d.fail('resource-exhausted', 'Close an existing listing before posting another.');
        const now = Timestamp.fromMillis(clock());
        tx.set(ref, { ...data, ownerUid: uid, ownerName: account.displayName, cityKey: data.city.toLowerCase(), status: 'active',
          createdAt: existing.data()?.createdAt ?? now, updatedAt: now, expiresAt: Timestamp.fromMillis(clock() + 30 * 86400000) });
        return { id: listingId };
      });
    },
    async closeListing(request) {
      const uid = d.actor(request), ref = db.doc('listings/' + d.id(request.data.id));
      return transaction(uid, 'listing', 10, async tx => {
        const snapshot = await tx.get(ref);
        if (!snapshot.exists || snapshot.data().ownerUid !== uid) d.fail('permission-denied', 'You do not own this listing.');
        if (snapshot.data().status === 'removed') return { closed: true };
        tx.update(ref, { status: 'closed', updatedAt: Timestamp.fromMillis(clock()) });
        return { closed: true };
      });
    },
    async startConversation(request) {
      const uid = d.actor(request), listingId = d.id(request.data.listingId);
      return transaction(uid, 'contact', 10, async (tx, account) => {
        const item = await tx.get(db.doc('listings/' + listingId));
        if (!item.exists || item.data().status !== 'active' || item.data().expiresAt.toMillis() <= clock()) d.fail('not-found', 'This listing is no longer available.');
        const owner = item.data().ownerUid;
        if (owner === uid) d.fail('failed-precondition', 'This is your listing.');
        const target = await contact(tx, uid, owner);
        const cid = d.conversationId(listingId, [uid, owner]), ref = db.doc('conversations/' + cid);
        const previous = await tx.get(ref);
        if (!previous.exists) {
          tx.set(ref, { memberUids: [uid, owner], memberNames: { [uid]: account.displayName, [owner]: target.displayName },
            listingId, listingTitle: item.data().title, contactAllowed: true, lastMessage: '', updatedAt: Timestamp.fromMillis(clock()) });
        } else if (!previous.data().contactAllowed) d.fail('permission-denied', 'Contact is unavailable.');
        return { id: cid };
      });
    },
    async sendMessage(request) {
      const uid = d.actor(request), cid = d.id(request.data.conversationId), messageId = d.id(request.data.id);
      const body = d.text(request.data.text, 'Message', 1, 2000);
      return transaction(uid, 'message', 30, async tx => {
        const ref = db.doc('conversations/' + cid), snapshot = await tx.get(ref);
        if (!snapshot.exists || !snapshot.data().memberUids.includes(uid) || !snapshot.data().contactAllowed)
          d.fail('permission-denied', 'This conversation is unavailable.');
        const recipient = snapshot.data().memberUids.find(value => value !== uid);
        await contact(tx, uid, recipient);
        const msg = ref.collection('messages').doc(messageId), existing = await tx.get(msg);
        if (existing.exists) {
          if (existing.data().senderUid !== uid || existing.data().text !== body) d.fail('already-exists', 'Message identifier already used.');
          return { id: messageId };
        }
        const now = Timestamp.fromMillis(clock());
        tx.create(msg, { senderUid: uid, text: body, createdAt: now });
        tx.update(ref, { lastMessage: body.slice(0, 100), updatedAt: now });
        return { id: messageId };
      });
    },
    async blockUser(request) {
      const uid = d.actor(request), targetUid = d.id(request.data.uid);
      if (uid === targetUid) d.fail('invalid-argument', 'You cannot block yourself.');
      await transaction(uid, 'block', 10, async tx => {
        const target = await tx.get(userRef(targetUid));
        if (!target.exists) d.fail('not-found', 'This user is unavailable.');
        tx.set(blockedRef(uid, targetUid), { targetUid, createdAt: Timestamp.fromMillis(clock()) });
        return {};
      });
      // The block is authoritative immediately; closing inbox rows can be retried.
      const rows = await db.collection('conversations').where('memberUids', 'array-contains', uid).get();
      for (const row of rows.docs) if (row.data().memberUids.includes(targetUid)) await row.ref.update({ contactAllowed: false });
      return { blocked: true };
    },
    async reportContent(request) {
      const uid = d.actor(request), targetId = d.id(request.data.targetId);
      if (!['listing', 'user'].includes(request.data.type) || !['SCAM', 'HARASSMENT', 'DISCRIMINATION', 'OTHER'].includes(request.data.reason))
        d.fail('invalid-argument', 'Choose a report category.');
      const detail = d.text(request.data.detail ?? '', 'Report', 0, 500);
      return transaction(uid, 'report', 5, async tx => {
        const target = await tx.get(db.doc((request.data.type === 'listing' ? 'listings/' : 'publicProfiles/') + targetId));
        if (!target.exists) d.fail('not-found', 'This content is unavailable.');
        tx.create(db.collection('reports').doc(randomUUID()), { submittedBy: uid, targetId, type: request.data.type,
          reason: request.data.reason, detail, status: 'open', createdAt: Timestamp.fromMillis(clock()),
          expiresAt: Timestamp.fromMillis(clock() + 30 * 86400000) });
        return { reported: true };
      });
    },
    async registerDevice(request) {
      const uid = d.actor(request), token = d.text(request.data.token, 'Device token', 20, 4096);
      return transaction(uid, 'device', 10, async tx => {
        tx.set(userRef(uid).collection('devices').doc(d.deviceId(token)), { token, updatedAt: Timestamp.fromMillis(clock()) });
        return { registered: true };
      });
    },
    async unregisterDevice(request) {
      const uid = d.actor(request), token = d.text(request.data.token, 'Device token', 20, 4096);
      return transaction(uid, 'device', 10, async tx => {
        tx.delete(userRef(uid).collection('devices').doc(d.deviceId(token)));
        return { removed: true };
      });
    },
    async deleteAccount(request) {
      const uid = d.actor(request, { verified: false, recent: true, now: clock() });
      await db.runTransaction(async tx => {
        const ref = db.doc('_deletions/' + uid), previous = await tx.get(ref);
        if (previous.exists) return;
        tx.set(userRef(uid), { status: 'deleting' }, { merge: true });
        tx.create(ref, { status: 'pending', createdAt: Timestamp.fromMillis(clock()) });
      });
      return { queued: true };
    }
  };
  async function deleteUserData(uid) {
    // Each step is idempotent. The tombstone blocks recreation from old tokens.
    for (const query of [
      db.collection('listings').where('ownerUid', '==', uid),
      db.collection('conversations').where('memberUids', 'array-contains', uid),
      db.collection('reports').where('submittedBy', '==', uid),
      db.collection('reports').where('targetId', '==', uid),
      db.collectionGroup('blockedUsers').where('targetUid', '==', uid)
    ]) {
      for (;;) {
        const page = await query.limit(100).get();
        if (page.empty) break;
        for (const row of page.docs) await db.recursiveDelete(row.ref);
      }
    }
    await db.recursiveDelete(userRef(uid));
    await db.doc('publicProfiles/' + uid).delete();
    await db.doc('entitlements/' + uid).delete();
    await db.doc('_limits/' + uid).delete();
    try { await auth.deleteUser(uid); } catch (error) { if (error.code !== 'auth/user-not-found') throw error; }
    await db.doc('_deletions/' + uid).set({ status: 'complete', completedAt: Timestamp.fromMillis(clock()), expiresAt: Timestamp.fromMillis(clock() + 7 * 86400000) }, { merge: true });
  }
  async function notifyMessage(conversationId, message) {
    const conversation = await db.doc('conversations/' + conversationId).get();
    if (!conversation.exists || !conversation.data().contactAllowed) return;
    const uid = conversation.data().memberUids.find(value => value !== message.senderUid);
    const [account, block1, block2] = await db.getAll(userRef(uid), blockedRef(uid, message.senderUid), blockedRef(message.senderUid, uid));
    if (!account.exists || account.data().status !== 'active' || block1.exists || block2.exists) return;
    const devices = await userRef(uid).collection('devices').orderBy('updatedAt', 'desc').limit(20).get();
    if (devices.empty) return;
    const result = await messaging.sendEachForMulticast({ tokens: devices.docs.map(row => row.data().token),
      data: { conversationId, recipientUid: uid, title: 'Roomie', body: 'You have a new message.' }, android: { priority: 'normal' } });
    for (let i = 0; i < result.responses.length; i++) {
      if (['messaging/registration-token-not-registered', 'messaging/invalid-registration-token'].includes(result.responses[i].error?.code))
        await devices.docs[i].ref.delete();
    }
    if (result.responses.some(item => item.error && !['messaging/registration-token-not-registered', 'messaging/invalid-registration-token'].includes(item.error.code))) throw new Error('Notification delivery failed.');
  }
  return { handlers, deleteUserData, notifyMessage };
}
module.exports = { createService };
