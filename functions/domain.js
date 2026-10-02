const { createHash } = require('node:crypto');
class DomainError extends Error {
  constructor(code, message) { super(message); this.code = code; }
}
function fail(code, message) { throw new DomainError(code, message); }
function text(value, field, min, max) {
  if (typeof value !== 'string' || value.trim().length < min || value.trim().length > max ||
      /[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(value)) fail('invalid-argument', field + ' is invalid.');
  return value.trim();
}
function id(value) {
  if (typeof value !== 'string' || !/^[a-zA-Z0-9_-]{1,128}$/.test(value)) fail('invalid-argument', 'Invalid identifier.');
  return value;
}
function actor(request, { verified = true, recent = false, now = Date.now() } = {}) {
  if (!request.auth) fail('unauthenticated', 'Sign in to continue.');
  const uid = id(request.auth.uid);
  if (verified && request.auth.token.email_verified !== true) fail('failed-precondition', 'Verify your email before continuing.');
  const age = now / 1000 - request.auth.token.auth_time;
  if (recent && (!Number.isFinite(age) || age < -30 || age > 300)) fail('failed-precondition', 'Sign in again before deleting your account.');
  return uid;
}
function profile(data) {
  if (data?.termsAccepted !== true || data?.termsVersion !== 1) fail('failed-precondition', 'Accept the current community terms.');
  return { displayName: text(data.displayName, 'Name', 2, 80), bio: text(data.bio ?? '', 'Bio', 0, 600), city: text(data.city, 'City', 2, 80) };
}
function listing(data) {
  if (!['ROOM', 'ROOMMATE'].includes(data?.kind)) fail('invalid-argument', 'Choose a listing type.');
  if (!Number.isInteger(data.monthlyRentUsd) || data.monthlyRentUsd < 1 || data.monthlyRentUsd > 100000) fail('invalid-argument', 'Enter a monthly amount in whole US dollars.');
  return { kind: data.kind, title: text(data.title, 'Title', 5, 100), city: text(data.city, 'City', 2, 80),
    description: text(data.description, 'Description', 20, 2000), monthlyRentUsd: data.monthlyRentUsd };
}
function conversationId(listingId, members) {
  return createHash('sha256').update(JSON.stringify([id(listingId), [...members].map(id).sort()])).digest('hex');
}
function deviceId(token) { return createHash('sha256').update(text(token, 'Device token', 20, 4096)).digest('hex'); }
function nextQuota(previous, now, maximum, windowMs = 60000) {
  const current = previous && now - previous.since < windowMs ? previous : { since: now, count: 0 };
  if (current.count >= maximum) fail('resource-exhausted', 'Too many requests. Try again shortly.');
  return { since: current.since, count: current.count + 1 };
}
function entitlementTier(value, now = Date.now()) {
  const activeUntil = value?.activeUntil?.toMillis?.() ?? value?.activeUntil ?? 0;
  return activeUntil > now && ['plus', 'property_manager'].includes(value?.tier) ? value.tier : 'free';
}
function activeListingLimit(value, now = Date.now()) {
  return entitlementTier(value, now) === 'property_manager' ? 25 : entitlementTier(value, now) === 'plus' ? 10 : 1;
}
module.exports = { DomainError, fail, text, id, actor, profile, listing, conversationId, deviceId, nextQuota,
  entitlementTier, activeListingLimit };
