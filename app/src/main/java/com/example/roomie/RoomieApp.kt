package com.example.roomie

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomieApp(vm: RoomieViewModel, requestedConversation: String?, consumed: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf("Browse") }
    var editor by remember { mutableStateOf<Listing?>(null) }
    var newPost by rememberSaveable { mutableStateOf(false) }
    var report by remember { mutableStateOf<Pair<String, String>?>(null) }
    var terms by rememberSaveable { mutableStateOf(false) }
    var delete by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) vm.notifications()
    }
    LaunchedEffect(requestedConversation, state.session?.uid, state.profile?.status, state.busy) {
        if (requestedConversation != null && state.profile?.status == "active" && !state.busy) {
            vm.openRequestedConversation(requestedConversation)
            consumed()
        }
    }
    LaunchedEffect(state.session?.uid) { tab = "Browse"; editor = null; newPost = false; report = null; delete = false }
    BackHandler(enabled = state.chat != null || state.selectedListing != null) {
        if (state.chat != null) vm.closeChat() else vm.selectListing(null)
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (state.chat != null) "Conversation" else "Roomie") },
            actions = { TextButton(onClick = { terms = true }) { Text("Info") } }) },
        bottomBar = {
            if (state.session?.verified == true && state.profile?.status == "active" && state.chat == null && state.selectedListing == null)
                NavigationBar { listOf("Browse", "My posts", "Inbox", "Profile").forEach { label ->
                    NavigationBarItem(selected = tab == label, onClick = { tab = label },
                        icon = { Text(when (label) { "Browse" -> "⌂"; "My posts" -> "+"; "Inbox" -> "✉"; else -> "●" }) },
                        label = { Text(label) })
                } }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            (state.error ?: state.notice)?.let { message ->
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(Modifier.padding(12.dp)) {
                        Text(message, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::clearFeedback) { Text("Dismiss") }
                    }
                }
            }
            val session = state.session
            when {
                session == null -> AuthenticationScreen(state.busy, vm::signIn, vm::resetPassword)
                !session.verified -> Page {
                    Text("Verify your email", style = MaterialTheme.typography.headlineSmall)
                    Text("Check the verification link sent to " + session.email + ".")
                    Button(onClick = vm::refreshVerification, enabled = !state.busy) { Text("I've verified my email") }
                    OutlinedButton(onClick = vm::resendVerification, enabled = !state.busy) { Text("Resend email") }
                    TextButton(onClick = vm::signOut, enabled = !state.busy) { Text("Sign out") }
                    TextButton(onClick = { delete = true }, enabled = !state.busy) { Text("Delete account") }
                }
                state.loadingProfile -> Page { CircularProgressIndicator() }
                state.profile?.status == "deleting" -> Page { Text("Your account deletion is being processed.") }
                state.profile == null -> Page {
                    ProfileForm(null, state.busy, vm::saveProfile, { terms = true })
                    TextButton(onClick = vm::signOut, enabled = !state.busy) { Text("Sign out") }
                    TextButton(onClick = { delete = true }, enabled = !state.busy) { Text("Delete account") }
                }
                state.profile?.status != "active" -> Page { Text("Your account is unavailable. Contact support for help."); TextButton(onClick = vm::signOut) { Text("Sign out") }; TextButton(onClick = { delete = true }) { Text("Delete account") } }
                state.chat != null -> ChatScreen(state, vm, { report = "user" to it })
                state.selectedListing != null -> ListingDetails(state, vm,
                    onEdit = { editor = it }, onReport = { report = "listing" to it },
                    onReportUser = { report = "user" to it })
                tab == "Browse" -> BrowseScreen(state, vm)
                tab == "My posts" -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                    item { Button(onClick = { newPost = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Create a post") } }
                    if (state.mine.isEmpty()) item { Text("Offer a room or post that you're looking for a roommate.") }
                    items(state.mine, key = { it.id }) { listing -> ListingCard(listing) { vm.selectListing(listing) } }
                    if (state.moreMine) item { OutlinedButton(onClick = vm::loadMoreMine, enabled = !state.busy) { Text("Load older posts") } }
                }
                tab == "Inbox" -> Page {
                    Text("Your conversations", style = MaterialTheme.typography.headlineSmall)
                    OutlinedButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.notifications()
                    }, enabled = !state.busy) { Text("Enable message notifications") }
                    if (state.conversations.isEmpty()) Text("Contact someone from a post to start a conversation.")
                    state.conversations.forEach { conversation ->
                        Card(onClick = { vm.openChat(conversation) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(conversation.memberNames[conversation.memberUids.firstOrNull { it != session.uid }.orEmpty()].orEmpty(),
                                    style = MaterialTheme.typography.titleMedium)
                                Text(conversation.listingTitle)
                                Text(conversation.lastMessage.ifEmpty { "Start the conversation" })
                            }
                        }
                    }
                    if (state.moreConversations) OutlinedButton(onClick = vm::loadMoreConversations, enabled = !state.busy) { Text("Load older conversations") }
                }
                else -> Page {
                    ProfileForm(state.profile, state.busy, vm::saveProfile, { terms = true })
                    Text(session.email)
                    Text("Plan: " + state.entitlement.displayName)
                    if (state.entitlement.boostCredits > 0) Text("Listing boosts available: " + state.entitlement.boostCredits)
                    Text("Paid plans are activated only after a purchase is verified by the Roomie backend.")
                    Text("Blocked accounts: " + state.blocked.size)
                    OutlinedButton(onClick = vm::signOut, enabled = !state.busy) { Text("Sign out") }
                    TextButton(onClick = { delete = true }, enabled = !state.busy) { Text("Delete account") }
                }
            }
        }
    }
    if (newPost || editor != null) ListingEditor(editor, state.busy,
        onDismiss = { newPost = false; editor = null },
        onSave = { id, kind, title, city, description, price ->
            vm.publish(id, kind, title, city, description, price) { newPost = false; editor = null }
        })
    report?.let { target -> ReportDialog(state.busy, onDismiss = { report = null },
        onSubmit = { reason, detail -> vm.report(target.first, target.second, reason, detail); report = null }) }
    if (delete) DeleteDialog(state.busy, { delete = false }) { password -> vm.deleteAccount(password); delete = false }
    if (terms) AlertDialog(onDismissRequest = { terms = false }, confirmButton = { TextButton(onClick = { terms = false }) { Text("Close") } },
        title = { Text("Roomie community and privacy") }, text = { InfoText() })
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun AuthenticationScreen(busy: Boolean, onSubmit: (String, String, Boolean) -> Unit, reset: (String) -> Unit) {
    var signup by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Page {
        Text("Find your next room or roommate", style = MaterialTheme.typography.headlineMedium)
        Text("Post an available room, share what you're looking for, and talk privately before you decide.")
        OutlinedTextField(email, { email = it.take(254) }, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it.take(128) }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        if (signup) Text("Use at least 8 characters. You'll verify your email before posting or contacting someone.")
        Button(onClick = { onSubmit(email, password, signup) }, enabled = !busy && email.contains("@") && password.isNotEmpty() && (!signup || password.length >= 8),
            modifier = Modifier.fillMaxWidth()) { Text(if (signup) "Create account" else "Sign in") }
        TextButton(onClick = { signup = !signup }) { Text(if (signup) "I have an account" else "Create an account") }
        TextButton(onClick = { reset(email) }, enabled = !busy && email.contains("@")) { Text("Forgot password") }
    }
}

@Composable
private fun ProfileForm(profile: Profile?, busy: Boolean, save: (String, String, String, Boolean) -> Unit, info: () -> Unit) {
    var name by rememberSaveable(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var bio by rememberSaveable(profile?.bio) { mutableStateOf(profile?.bio.orEmpty()) }
    var city by rememberSaveable(profile?.city) { mutableStateOf(profile?.city.orEmpty()) }
    var accepted by rememberSaveable { mutableStateOf(profile != null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
        Text(if (profile == null) "Set up your profile" else "Your public profile", style = MaterialTheme.typography.headlineSmall)
        Text("Use a first name and a city. Keep your exact address and personal documents out of public posts.")
        OutlinedTextField(name, { name = it.take(80) }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(city, { city = it.take(80) }, label = { Text("City") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(bio, { bio = it.take(600) }, label = { Text("About you") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Row {
            Checkbox(accepted, { accepted = it })
            Text("I am 18 or older and agree to the community terms and privacy information.", modifier = Modifier.weight(1f))
        }
        TextButton(onClick = info) { Text("Read community terms and privacy") }
        Button(onClick = { save(name, bio, city, accepted) }, enabled = !busy && accepted && name.trim().length >= 2 && city.trim().length >= 2) { Text("Save profile") }
    }
}

@Composable
private fun BrowseScreen(state: RoomieState, vm: RoomieViewModel) {
    var city by rememberSaveable { mutableStateOf(state.filter.city) }
    var kind by rememberSaveable { mutableStateOf(state.filter.kind) }
    var price by rememberSaveable { mutableStateOf(state.filter.maximum?.toString().orEmpty()) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Text("Rooms and roommate requests", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(city, { city = it.take(80) }, label = { Text("City · leave empty for all cities") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "All", "ROOM" to "Rooms", "ROOMMATE" to "Roommates").forEach { (value, label) ->
                    FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(label) })
                }
            }
            OutlinedTextField(price, { price = it.filter(Char::isDigit).take(6) }, label = { Text("Maximum monthly US dollars") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.filters(BrowseFilter(city.trim(), kind, price.toLongOrNull())) }, enabled = !state.busy) { Text("Apply filters") }
        }
        val listings = state.listings.filter { it.ownerUid !in state.blocked && it.expiresAt > System.currentTimeMillis() }
        if (listings.isEmpty()) item { Text("No posts match yet. Try another city or create your own post.") }
        items(listings, key = { it.id }) { listing -> ListingCard(listing) { vm.selectListing(listing) } }
        if (state.moreListings) item { OutlinedButton(onClick = vm::loadMoreListings, enabled = !state.busy) { Text("Load more posts") } }
    }
}

@Composable
private fun ListingCard(listing: Listing, open: () -> Unit) {
    Card(onClick = open, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (listing.kind == "ROOM") "ROOM AVAILABLE" else "SEEKING A ROOMMATE", style = MaterialTheme.typography.labelMedium)
            Text(listing.title, style = MaterialTheme.typography.titleLarge)
            Text(listing.city + " · $" + listing.monthlyRentUsd + "/month")
            Text(listing.ownerName + if (listing.status == "active") "" else " · " + listing.status)
        }
    }
}

@Composable
private fun ListingDetails(state: RoomieState, vm: RoomieViewModel, onEdit: (Listing) -> Unit,
    onReport: (String) -> Unit, onReportUser: (String) -> Unit) {
    val listing = state.selectedListing ?: return
    Page {
        TextButton(onClick = { vm.selectListing(null) }) { Text("Back to posts") }
        Text(listing.title, style = MaterialTheme.typography.headlineMedium)
        Text(listing.city + " · $" + listing.monthlyRentUsd + "/month")
        Text(if (listing.kind == "ROOM") "Room available" else "Seeking a roommate")
        Text("Posted by " + listing.ownerName)
        Text(listing.description)
        if (listing.ownerUid == state.session?.uid) {
            OutlinedButton(onClick = { onEdit(listing) }, enabled = !state.busy && listing.status != "removed") { Text("Edit or renew post") }
            if (listing.status == "active") OutlinedButton(onClick = { vm.closeListing(listing.id) }, enabled = !state.busy) { Text("Close post") }
        } else {
            Button(onClick = { vm.contact(listing) }, enabled = !state.busy && listing.status == "active") { Text("I'm interested · send a message") }
            Text("Meet safely and check the room before sending a deposit. Roomie does not verify listings or handle rent payments.")
            TextButton(onClick = { onReport(listing.id) }, enabled = !state.busy) { Text("Report post") }
            TextButton(onClick = { onReportUser(listing.ownerUid) }, enabled = !state.busy) { Text("Report user") }
            TextButton(onClick = { vm.block(listing.ownerUid) }, enabled = !state.busy) { Text("Block user") }
        }
    }
}

@Composable
private fun ChatScreen(state: RoomieState, vm: RoomieViewModel, report: (String) -> Unit) {
    val chat = state.chat ?: return
    var text by rememberSaveable(chat.id) { mutableStateOf("") }
    var messageId by rememberSaveable(chat.id) { mutableStateOf(UUID.randomUUID().toString()) }
    val other = chat.memberUids.firstOrNull { it != state.session?.uid }.orEmpty()
    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        TextButton(onClick = vm::closeChat) { Text("Back to inbox") }
        Text(chat.listingTitle, style = MaterialTheme.typography.titleMedium)
        Row {
            TextButton(onClick = { report(other) }, enabled = !state.busy) { Text("Report user") }
            TextButton(onClick = { vm.block(other) }, enabled = !state.busy) { Text("Block user") }
        }
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(state.messages.lastOrNull()?.id) {
            if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex + if (state.moreMessages) 1 else 0)
        }
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.moreMessages) item { TextButton(onClick = vm::loadOlderMessages, enabled = !state.busy) { Text("Load older messages") } }
            items(state.messages, key = { it.id }) { message ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (message.senderUid == state.session?.uid) "You" else chat.memberNames[message.senderUid].orEmpty(),
                            style = MaterialTheme.typography.labelSmall)
                        Text(message.text)
                    }
                }
            }
        }
        OutlinedTextField(text, { text = it.take(2000) }, label = { Text("Message") }, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.sendMessage(text, messageId) { text = ""; messageId = UUID.randomUUID().toString() } },
            enabled = !state.busy && text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Send") }
    }
}

@Composable
private fun ListingEditor(listing: Listing?, busy: Boolean, onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Long) -> Unit) {
    val id = rememberSaveable(listing?.id) { listing?.id ?: UUID.randomUUID().toString() }
    var kind by rememberSaveable(id) { mutableStateOf(listing?.kind ?: "ROOM") }
    var title by rememberSaveable(id) { mutableStateOf(listing?.title.orEmpty()) }
    var city by rememberSaveable(id) { mutableStateOf(listing?.city.orEmpty()) }
    var description by rememberSaveable(id) { mutableStateOf(listing?.description.orEmpty()) }
    var rent by rememberSaveable(id) { mutableStateOf(listing?.monthlyRentUsd?.toString().orEmpty()) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Your housing post") },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
        confirmButton = { Button(onClick = { onSave(id, kind, title, city, description, rent.toLongOrNull() ?: 0) },
            enabled = !busy && title.trim().length >= 5 && city.trim().length >= 2 && description.trim().length >= 20 && (rent.toLongOrNull() ?: 0) in 1L..100000L) { Text("Publish") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row { FilterChip(kind == "ROOM", { kind = "ROOM" }, label = { Text("Have a room") })
                    Spacer(Modifier.width(8.dp)); FilterChip(kind == "ROOMMATE", { kind = "ROOMMATE" }, label = { Text("Need a roommate") }) }
                OutlinedTextField(title, { title = it.take(100) }, label = { Text("Title") })
                OutlinedTextField(city, { city = it.take(80) }, label = { Text("City") })
                OutlinedTextField(rent, { rent = it.filter(Char::isDigit).take(6) }, label = { Text("Monthly rent or budget · USD") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(description, { description = it.take(2000) }, label = { Text("Describe the room or what you're seeking") }, minLines = 4)
                Text("Posts expire after 30 days. Keep exact addresses, phone numbers, and personal documents out of public posts.")
            }
        })
}

@Composable
private fun ReportDialog(busy: Boolean, onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("SCAM") }
    var detail by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Report for review") },
        confirmButton = { Button(onClick = { onSubmit(reason, detail) }, enabled = !busy) { Text("Send report") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            listOf("SCAM", "HARASSMENT", "DISCRIMINATION", "OTHER").forEach { value ->
                Row { RadioButton(reason == value, { reason = value }); Text(value.lowercase().replaceFirstChar(Char::uppercase)) }
            }
            OutlinedTextField(detail, { detail = it.take(500) }, label = { Text("Details · optional") })
        } })
}

@Composable
private fun DeleteDialog(busy: Boolean, onDismiss: () -> Unit, onDelete: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete your account?") },
        confirmButton = { Button(onClick = { onDelete(password) }, enabled = !busy && password.isNotEmpty()) { Text("Delete account") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { Column {
            Text("Your profile, posts, devices, blocks, and shared conversation histories will be deleted. A short-lived deletion record prevents old sessions from recreating your account. Enter your password to confirm.")
            OutlinedTextField(password, { password = it.take(128) }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation())
        } })
}

@Composable
private fun InfoText() {
    val uri = LocalUriHandler.current
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Roomie is for adults offering rooms or seeking roommates. Treat others respectfully. Fraud, harassment, discriminatory housing posts, and sharing someone else's personal information are prohibited. You are responsible for the accuracy and lawfulness of your post.")
        Text("Your display name, bio, city, and housing posts are visible to signed-in members. Your email stays in Firebase Authentication. Conversations are accessible to their participants and the service operator; they are not end-to-end encrypted.")
        Text("Firebase processes authentication, profiles, posts, messages, device tokens, entitlements, and abuse reports. App Check protects access. Message notifications show a generic alert without message text. No ads or rent payments are included.")
        Text("In-app purchases are not enabled in this build. Future Roomie Plus and listing boosts will use Google Play Billing with backend verification. Verification, blocking, reporting, and account deletion will remain free.")
        Text("Block stops new contact. Reports enter a moderation queue. Account deletion removes your profile, posts, tokens, blocks, and shared conversations. Completed deletion records expire after 7 days and reports after 30 days once their retention policies are enabled.")
        Text("Roomie does not verify listings, guarantee compatibility, handle rent payments, or make housing decisions.")
        if (BuildConfig.PRIVACY_POLICY_URL.isNotBlank()) TextButton(onClick = { uri.openUri(BuildConfig.PRIVACY_POLICY_URL) }) { Text("Public privacy policy") }
        if (BuildConfig.ACCOUNT_DELETION_URL.isNotBlank()) TextButton(onClick = { uri.openUri(BuildConfig.ACCOUNT_DELETION_URL) }) { Text("Account deletion help") }
        if (BuildConfig.SUPPORT_EMAIL.isNotBlank()) Text("Support: " + BuildConfig.SUPPORT_EMAIL)
    }
}
