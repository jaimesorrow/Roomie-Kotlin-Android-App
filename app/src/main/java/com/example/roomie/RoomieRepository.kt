package com.example.roomie

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class RoomieRepository {
    val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance().apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
    }
    private val functions = FirebaseFunctions.getInstance("us-west1")
    fun sessions(): Flow<Session?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebase ->
            trySend(firebase.currentUser?.let { Session(it.uid, it.email.orEmpty(), it.isEmailVerified) })
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
    suspend fun signIn(email: String, password: String) { auth.signInWithEmailAndPassword(email.trim(), password).await() }
    suspend fun signUp(email: String, password: String) {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user ?: error("Account unavailable")
        user.sendEmailVerification().await()
    }
    suspend fun resetPassword(email: String) { auth.sendPasswordResetEmail(email.trim()).await() }
    suspend fun resendVerification() { auth.currentUser?.sendEmailVerification()?.await() }
    suspend fun refreshSession(): Session? {
        auth.currentUser?.reload()?.await()
        auth.currentUser?.getIdToken(true)?.await()
        return auth.currentUser?.let { Session(it.uid, it.email.orEmpty(), it.isEmailVerified) }
    }
    suspend fun call(name: String, data: Map<String, Any> = emptyMap()): Map<*, *> =
        functions.getHttpsCallable(name).call(data).await().data as? Map<*, *> ?: emptyMap<Any, Any>()
    fun profile(uid: String): Flow<Profile?> = callbackFlow {
        val listener = db.document("users/$uid").addSnapshotListener { value, error ->
            if (error != null) close(error) else trySend(value?.takeIf { it.exists() }?.let {
                Profile(it.getString("displayName").orEmpty(), it.getString("bio").orEmpty(),
                    it.getString("city").orEmpty(), it.getString("status").orEmpty())
            })
        }
        awaitClose { listener.remove() }
    }
    private fun listingQuery(filter: BrowseFilter): Query {
        var query: Query = db.collection("listings").whereEqualTo("status", "active")
        if (filter.city.isNotBlank()) query = query.whereEqualTo("cityKey", filter.city.trim().lowercase(java.util.Locale.ROOT))
        if (filter.kind != "ALL") query = query.whereEqualTo("kind", filter.kind)
        if (filter.maximum != null) query = query.whereLessThanOrEqualTo("monthlyRentUsd", filter.maximum).orderBy("monthlyRentUsd")
        return query.orderBy("updatedAt", Query.Direction.DESCENDING).limit(50)
    }
    private fun DocumentSnapshot.toListing() = Listing(id, getString("ownerUid").orEmpty(), getString("ownerName").orEmpty(),
        getString("kind").orEmpty(), getString("title").orEmpty(), getString("city").orEmpty(),
        getString("description").orEmpty(), getLong("monthlyRentUsd") ?: 0L, getString("status").orEmpty(),
        getTimestamp("expiresAt")?.toDate()?.time ?: 0L)
    private fun Query.listingFlow(): Flow<Page<Listing>> = callbackFlow {
        val listener = addSnapshotListener { value, error ->
            if (error != null) close(error) else if (value != null)
                trySend(Page(value.documents.map { it.toListing() }, value.documents.lastOrNull(), value.size() == 50))
        }
        awaitClose { listener.remove() }
    }
    fun listings(filter: BrowseFilter) = listingQuery(filter).listingFlow()
    suspend fun nextListings(filter: BrowseFilter, cursor: DocumentSnapshot): Page<Listing> {
        val value = listingQuery(filter).startAfter(cursor).get().await()
        return Page(value.documents.map { it.toListing() }, value.documents.lastOrNull(), value.size() == 50)
    }
    fun mine(uid: String): Flow<Page<Listing>> = db.collection("listings").whereEqualTo("ownerUid", uid)
        .orderBy("updatedAt", Query.Direction.DESCENDING).limit(50).listingFlow()
    private fun conversationQuery(uid: String) = db.collection("conversations").whereArrayContains("memberUids", uid)
        .whereEqualTo("contactAllowed", true).orderBy("updatedAt", Query.Direction.DESCENDING).limit(50)
    private fun DocumentSnapshot.toConversation() = Conversation(id, getString("listingTitle").orEmpty(),
        (get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        (get("memberNames") as? Map<*, *>)?.entries?.mapNotNull { pair ->
            val key = pair.key as? String; val name = pair.value as? String
            if (key != null && name != null) key to name else null
        }?.toMap().orEmpty(), getString("lastMessage").orEmpty())
    fun conversations(uid: String): Flow<Page<Conversation>> = callbackFlow {
        val listener = conversationQuery(uid).addSnapshotListener { value, error ->
            if (error != null) close(error) else if (value != null)
                trySend(Page(value.documents.map { it.toConversation() }, value.documents.lastOrNull(), value.size() == 50))
        }
        awaitClose { listener.remove() }
    }
    suspend fun conversation(id: String): Conversation? {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        return db.document("conversations/$id").get().await().takeIf { it.exists() }?.toConversation()
    }
    suspend fun nextConversations(uid: String, cursor: DocumentSnapshot): Page<Conversation> {
        val value = conversationQuery(uid).startAfter(cursor).get().await()
        return Page(value.documents.map { it.toConversation() }, value.documents.lastOrNull(), value.size() == 50)
    }
    suspend fun nextMine(uid: String, cursor: DocumentSnapshot): Page<Listing> {
        val value = db.collection("listings").whereEqualTo("ownerUid", uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING).limit(50).startAfter(cursor).get().await()
        return Page(value.documents.map { it.toListing() }, value.documents.lastOrNull(), value.size() == 50)
    }
    fun blocks(uid: String): Flow<Set<String>> = callbackFlow {
        val listener = db.collection("users/$uid/blockedUsers").addSnapshotListener { value, error ->
            if (error != null) close(error) else trySend(value?.documents.orEmpty().map { it.id }.toSet())
        }
        awaitClose { listener.remove() }
    }
    fun entitlement(uid: String): Flow<Entitlement> = callbackFlow {
        val listener = db.document("entitlements/$uid").addSnapshotListener { value, error ->
            if (error != null) close(error) else trySend(value?.takeIf { it.exists() }?.let {
                Entitlement(
                    tier = it.getString("tier") ?: "free",
                    activeUntil = it.getTimestamp("activeUntil")?.toDate()?.time ?: 0L,
                    boostCredits = it.getLong("boostCredits") ?: 0L
                )
            } ?: Entitlement())
        }
        awaitClose { listener.remove() }
    }
    private fun messageQuery(cid: String) = db.collection("conversations/$cid/messages")
        .orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
    private fun DocumentSnapshot.toMessage() = Message(id, getString("senderUid").orEmpty(), getString("text").orEmpty(),
        getTimestamp("createdAt")?.toDate()?.time ?: 0L)
    fun messages(cid: String): Flow<Page<Message>> = callbackFlow {
        val listener = messageQuery(cid).addSnapshotListener { value, error ->
            if (error != null) close(error) else if (value != null)
                trySend(Page(value.documents.map { it.toMessage() }, value.documents.lastOrNull(), value.size() == 100))
        }
        awaitClose { listener.remove() }
    }
    suspend fun olderMessages(cid: String, cursor: DocumentSnapshot): Page<Message> {
        val value = messageQuery(cid).startAfter(cursor).get().await()
        return Page(value.documents.map { it.toMessage() }, value.documents.lastOrNull(), value.size() == 100)
    }
    suspend fun enableNotifications() {
        call("registerDevice", mapOf("token" to FirebaseMessaging.getInstance().token.await()))
    }
    suspend fun signOut() {
        runCatching { call("unregisterDevice", mapOf("token" to FirebaseMessaging.getInstance().token.await())) }
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
        auth.signOut()
    }
    suspend fun deleteAccount(password: String) {
        val user = auth.currentUser ?: error("Sign in again")
        user.reauthenticate(EmailAuthProvider.getCredential(user.email.orEmpty(), password)).await()
        user.getIdToken(true).await()
        call("deleteAccount")
        auth.signOut()
    }
}
