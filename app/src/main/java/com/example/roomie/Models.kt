package com.example.roomie

import com.google.firebase.firestore.DocumentSnapshot

data class Session(val uid: String, val email: String, val verified: Boolean)
data class Profile(val displayName: String = "", val bio: String = "", val city: String = "", val status: String = "active")
data class Listing(val id: String, val ownerUid: String, val ownerName: String, val kind: String,
    val title: String, val city: String, val description: String, val monthlyRentUsd: Long,
    val status: String, val expiresAt: Long)
data class Conversation(val id: String, val listingTitle: String, val memberUids: List<String>,
    val memberNames: Map<String, String>, val lastMessage: String)
data class Message(val id: String, val senderUid: String, val text: String, val createdAt: Long)
data class BrowseFilter(val city: String = "", val kind: String = "ALL", val maximum: Long? = null)
data class Entitlement(
    val tier: String = "free",
    val activeUntil: Long = 0L,
    val boostCredits: Long = 0L
) {
    val isPaid: Boolean get() = tier in setOf("plus", "property_manager") && activeUntil > System.currentTimeMillis()
    val displayName: String get() = when {
        !isPaid -> "Roomie Free"
        tier == "property_manager" -> "Property Manager Pro"
        else -> "Roomie Plus"
    }
}
data class Page<T>(val items: List<T>, val cursor: DocumentSnapshot?, val hasMore: Boolean)
data class RoomieState(
    val session: Session? = null, val profile: Profile? = null, val loadingProfile: Boolean = false,
    val busy: Boolean = false, val error: String? = null, val notice: String? = null,
    val listings: List<Listing> = emptyList(), val mine: List<Listing> = emptyList(),
    val conversations: List<Conversation> = emptyList(), val blocked: Set<String> = emptySet(),
    val selectedListing: Listing? = null, val chat: Conversation? = null,
    val messages: List<Message> = emptyList(), val entitlement: Entitlement = Entitlement(), val moreListings: Boolean = false,
    val moreMessages: Boolean = false, val moreMine: Boolean = false, val moreConversations: Boolean = false, val filter: BrowseFilter = BrowseFilter()
)
