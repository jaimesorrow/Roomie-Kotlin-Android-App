package com.example.roomie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class RoomieViewModel(private val repo: RoomieRepository = RoomieRepository()) : ViewModel() {
    private val mutable = MutableStateFlow(RoomieState())
    val state = mutable.asStateFlow()
    private var profileJob: Job? = null
    private var readJobs: Job? = null
    private var feedJob: Job? = null
    private var chatJob: Job? = null
    private var operationJob: Job? = null
    private var operationId = 0L
    private var operationMayChangeSession = false
    private var listingCursor: DocumentSnapshot? = null
    private var messageCursor: DocumentSnapshot? = null
    private var olderLoaded = false
    private var mineCursor: DocumentSnapshot? = null
    private var conversationCursor: DocumentSnapshot? = null
    private var olderMine = emptyList<Listing>()
    private var recentMine = emptyList<Listing>()
    private var olderConversations = emptyList<Conversation>()
    private var recentConversations = emptyList<Conversation>()
    private var olderListings = emptyList<Listing>()
    private var recentListings = emptyList<Listing>()
    init { viewModelScope.launch { repo.sessions().collect { acceptSession(it) } } }
    private fun acceptSession(session: Session?) {
        if (mutable.value.session == session) return
        if (mutable.value.session?.uid != session?.uid && !operationMayChangeSession) {
            operationId++
            operationJob?.cancel()
        }
        val authenticationBusy = operationMayChangeSession && mutable.value.busy
        profileJob?.cancel(); readJobs?.cancel(); feedJob?.cancel(); chatJob?.cancel()
        readJobs = null; listingCursor = null; messageCursor = null
        olderListings = emptyList(); recentListings = emptyList(); olderMine = emptyList(); recentMine = emptyList()
        olderConversations = emptyList(); recentConversations = emptyList(); mineCursor = null; conversationCursor = null
        mutable.value = RoomieState(session = session, loadingProfile = session?.verified == true, busy = authenticationBusy)
        if (session?.verified == true) profileJob = viewModelScope.launch {
            repo.profile(session.uid).catch { error -> mutable.update { it.copy(loadingProfile = false, error = friendly(error)) } }.collect { profile ->
                mutable.update { it.copy(profile = profile, loadingProfile = false) }
                if (profile?.status == "active" && readJobs == null) startReads(session.uid)
                if (profile?.status != "active") {
                    readJobs?.cancel(); feedJob?.cancel(); chatJob?.cancel(); readJobs = null
                    mutable.update { it.copy(listings = emptyList(), mine = emptyList(), conversations = emptyList(),
                        messages = emptyList(), selectedListing = null, chat = null) }
                }
            }
        }
    }
    private fun startReads(uid: String) {
        readJobs = viewModelScope.launch {
            supervisorScope {
                launch { repo.mine(uid).catch { showError(it) }.collect { page ->
                    recentMine = page.items
                    if (olderMine.isEmpty()) mineCursor = page.cursor
                    mutable.update { it.copy(mine = (recentMine + olderMine).distinctBy(Listing::id), moreMine = if (olderMine.isEmpty()) page.hasMore else it.moreMine) }
                } }
                launch { repo.conversations(uid).catch { showError(it) }.collect { page ->
                    recentConversations = page.items
                    if (olderConversations.isEmpty()) conversationCursor = page.cursor
                    mutable.update { it.copy(conversations = (recentConversations + olderConversations).distinctBy(Conversation::id).filter { row -> row.memberUids.none(it.blocked::contains) },
                        moreConversations = if (olderConversations.isEmpty()) page.hasMore else it.moreConversations) }
                } }
                launch { repo.blocks(uid).catch { showError(it) }.collect { blocked ->
                    mutable.update { it.copy(blocked = blocked, conversations = it.conversations.filter { row -> row.memberUids.none(blocked::contains) },
                        selectedListing = it.selectedListing?.takeUnless { item -> item.ownerUid in blocked },
                        chat = it.chat?.takeUnless { chat -> chat.memberUids.any(blocked::contains) }) }
                    if (mutable.value.chat == null) { chatJob?.cancel(); mutable.update { it.copy(messages = emptyList()) } }
                } }
                launch { repo.entitlement(uid).catch { showError(it) }.collect { entitlement ->
                    mutable.update { it.copy(entitlement = entitlement) }
                } }
            }
        }
        watchFeed()
    }
    private fun watchFeed() {
        feedJob?.cancel(); olderListings = emptyList(); recentListings = emptyList(); listingCursor = null
        val filter = mutable.value.filter
        mutable.update { it.copy(listings = emptyList(), moreListings = false) }
        feedJob = viewModelScope.launch {
            repo.listings(filter).catch { showError(it) }.collect { page ->
                recentListings = page.items
                if (olderListings.isEmpty()) listingCursor = page.cursor
                mutable.update { it.copy(listings = (recentListings + olderListings).distinctBy(Listing::id),
                    moreListings = if (olderListings.isEmpty()) page.hasMore else it.moreListings) }
            }
        }
    }
    fun filters(filter: BrowseFilter) { mutable.update { it.copy(filter = filter, error = null) }; watchFeed() }
    fun loadMoreListings() {
        val cursor = listingCursor ?: return
        operation {
            val page = repo.nextListings(mutable.value.filter, cursor)
            olderListings = (olderListings + page.items).distinctBy(Listing::id); listingCursor = page.cursor
            mutable.update { it.copy(listings = (recentListings + olderListings).distinctBy(Listing::id), moreListings = page.hasMore) }
        }
    }
    fun loadMoreMine() {
        val cursor = mineCursor ?: return; val uid = mutable.value.session?.uid ?: return
        operation {
            val page = repo.nextMine(uid, cursor); olderMine = (olderMine + page.items).distinctBy(Listing::id); mineCursor = page.cursor
            mutable.update { it.copy(mine = (recentMine + olderMine).distinctBy(Listing::id), moreMine = page.hasMore) }
        }
    }
    fun loadMoreConversations() {
        val cursor = conversationCursor ?: return; val uid = mutable.value.session?.uid ?: return
        operation {
            val page = repo.nextConversations(uid, cursor)
            olderConversations = (olderConversations + page.items).distinctBy(Conversation::id); conversationCursor = page.cursor
            mutable.update { it.copy(conversations = (recentConversations + olderConversations).distinctBy(Conversation::id).filter { row -> row.memberUids.none(it.blocked::contains) }, moreConversations = page.hasMore) }
        }
    }
    fun selectListing(listing: Listing?) { mutable.update { it.copy(selectedListing = listing) } }
    fun openRequestedConversation(id: String) = operation {
        val conversation = repo.conversation(id) ?: error("Conversation unavailable")
        val current = mutable.value
        val uid = current.session?.uid ?: error("Sign in again")
        require(uid in conversation.memberUids && conversation.memberUids.none(current.blocked::contains))
        openChat(conversation)
    }
    fun openChat(conversation: Conversation) {
        chatJob?.cancel(); messageCursor = null; olderLoaded = false
        mutable.update { it.copy(chat = conversation, selectedListing = null, messages = emptyList(), moreMessages = false) }
        chatJob = viewModelScope.launch {
            repo.messages(conversation.id).catch { showError(it) }.collect { page ->
                if (!olderLoaded) messageCursor = page.cursor
                mutable.update { current -> current.copy(messages = (current.messages + page.items).associateBy(Message::id).values.sortedWith(compareBy(Message::createdAt, Message::id)),
                    moreMessages = if (!olderLoaded) page.hasMore else current.moreMessages) }
            }
        }
    }
    fun closeChat() { chatJob?.cancel(); mutable.update { it.copy(chat = null, messages = emptyList()) } }
    fun loadOlderMessages() {
        val cursor = messageCursor ?: return; val cid = mutable.value.chat?.id ?: return
        operation {
            val page = repo.olderMessages(cid, cursor); olderLoaded = true; messageCursor = page.cursor
            mutable.update { current -> current.copy(messages = (current.messages + page.items).distinctBy(Message::id).sortedWith(compareBy(Message::createdAt, Message::id)), moreMessages = page.hasMore) }
        }
    }
    fun signIn(email: String, password: String, signup: Boolean) = operation(allowSessionChange = true) {
        require(password.length >= 8 || !signup)
        if (signup) repo.signUp(email, password) else repo.signIn(email, password)
    }
    fun resetPassword(email: String) = operation { repo.resetPassword(email); notice("If an account exists, a reset email has been sent.") }
    fun resendVerification() = operation { repo.resendVerification(); notice("Verification email sent.") }
    fun refreshVerification() = operation(allowSessionChange = true) { val session = repo.refreshSession(); acceptSession(session) }
    fun saveProfile(name: String, bio: String, city: String, accepted: Boolean) = operation {
        repo.call("saveProfile", mapOf("displayName" to name, "bio" to bio, "city" to city, "termsAccepted" to accepted, "termsVersion" to 1))
    }
    fun publish(id: String, kind: String, title: String, city: String, description: String, rent: Long, onSuccess: () -> Unit) = operation {
        repo.call("publishListing", mapOf("id" to id, "kind" to kind, "title" to title, "city" to city, "description" to description, "monthlyRentUsd" to rent))
        selectListing(null); onSuccess()
    }
    fun closeListing(id: String) = operation { repo.call("closeListing", mapOf("id" to id)); selectListing(null) }
    fun contact(listing: Listing) = operation {
        val result = repo.call("startConversation", mapOf("listingId" to listing.id)); val cid = result["id"] as? String ?: error("Unavailable")
        val session = mutable.value.session ?: return@operation
        openChat(Conversation(cid, listing.title, listOf(session.uid, listing.ownerUid),
            mapOf(session.uid to (mutable.value.profile?.displayName.orEmpty()), listing.ownerUid to listing.ownerName), ""))
    }
    fun sendMessage(text: String, id: String, onSuccess: () -> Unit) = operation {
        val cid = mutable.value.chat?.id ?: return@operation
        repo.call("sendMessage", mapOf("conversationId" to cid, "text" to text, "id" to id)); onSuccess()
    }
    fun block(uid: String) = operation { repo.call("blockUser", mapOf("uid" to uid)); closeChat(); selectListing(null); notice("User blocked. New contact is disabled.") }
    fun report(type: String, id: String, reason: String, detail: String) = operation {
        repo.call("reportContent", mapOf("type" to type, "targetId" to id, "reason" to reason, "detail" to detail)); notice("Report sent for review.")
    }
    fun notifications() = operation { repo.enableNotifications(); notice("Message notifications enabled.") }
    fun signOut() {
        viewModelScope.launch { withTimeoutOrNull(5000) { repo.signOut() }; repo.auth.signOut() }
    }
    fun deleteAccount(password: String) = operation(allowSessionChange = true) { repo.deleteAccount(password) }
    fun clearFeedback() { mutable.update { it.copy(error = null, notice = null) } }
    private fun notice(value: String) { mutable.update { it.copy(notice = value) } }
    private fun showError(error: Throwable) { mutable.update { it.copy(error = friendly(error)) } }
    private fun operation(allowSessionChange: Boolean = false, block: suspend () -> Unit) {
        if (mutable.value.busy) return
        val id = ++operationId
        operationMayChangeSession = allowSessionChange
        mutable.update { it.copy(busy = true, error = null, notice = null) }
        operationJob = viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError(error)
            } finally {
                if (id == operationId) {
                    operationMayChangeSession = false
                    mutable.update { it.copy(busy = false) }
                }
            }
        }
    }
    private fun friendly(error: Throwable): String = when (error) {
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "That email already has an account. Sign in or reset your password."
            "ERROR_WEAK_PASSWORD" -> "Use a password with at least 8 characters."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Try again shortly."
            else -> "Check your email and password, then try again."
        }
        is FirebaseFunctionsException -> when (error.code) {
            FirebaseFunctionsException.Code.PERMISSION_DENIED -> "This action or contact is unavailable."
            FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> "You reached a posting or request limit. Try again later."
            FirebaseFunctionsException.Code.NOT_FOUND -> "This listing or account is no longer available."
            FirebaseFunctionsException.Code.INVALID_ARGUMENT -> "Check your fields and try again."
            FirebaseFunctionsException.Code.FAILED_PRECONDITION -> "Verify your email and complete your profile. Account deletion requires signing in again."
            else -> "Unable to connect. Check your connection and try again."
        }
        is IllegalArgumentException -> "Check your fields and try again."
        else -> "Unable to connect. Check your connection and try again."
    }
}
