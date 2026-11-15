package com.example.nyumbani // <-- Keep your project's actual package name here

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream

// --- NYUMBANI COLOR PALETTE ---
val BgCream = Color(0xFFFBF7EF)
val CardCream = Color(0xFFEFE3CC)
val PrimaryBrown = Color(0xFF6B4A2F)
val TextDark = Color(0xFF3B2A1C)
val TextMuted = Color(0xFF8A7256)
val BorderTan = Color(0xFFD8C6A8)
val DangerRed = Color(0xFFB33A3A)

// --- DATA MODELS ---
data class UserProfile(
    val id: String = "",
    val fullName: String = "",
    val phone: String = "",
    val unit: String = "",
    val estate: String = "Greenwood Estate",
    val role: String = "tenant" // "tenant" or "manager"
)

data class BulletinPost(
    val id: String = "",
    val authorName: String = "",
    val authorPhone: String = "",
    val role: String = "tenant",
    val content: String = "",
    val timeAgo: String = "Just now",
    val pinned: Boolean = false,
    val imageBase64: String = "",
    val createdAt: Long = 0L
)

data class GalleryPhoto(
    val id: String = "",
    val caption: String = "",
    val authorName: String = "",
    val authorPhone: String = "",
    val timeAgo: String = "Just now",
    val imageBase64: String = "",
    val createdAt: Long = 0L
)

data class ServiceRequest(
    val id: String = "",
    val category: String = "",
    val description: String = "",
    val urgency: String = "Urgent",
    val status: String = "Pending",
    val provider: String = "Unassigned",
    val tenantName: String = "",
    val tenantPhone: String = "",
    val unit: String = ""
)

// --- IMAGE HELPERS (Compress Uri -> Base64 for Firestore & Decode Back) ---
fun uriToCompressedBase64(context: android.content.Context, uri: Uri): String {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val original = BitmapFactory.decodeStream(inputStream) ?: return ""
        val maxDim = 800
        val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
        val scaled = Bitmap.createScaledBitmap(
            original,
            (original.width * ratio).toInt(),
            (original.height * ratio).toInt(),
            true
        )
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
        Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
    } catch (e: Exception) {
        ""
    }
}

fun base64ToBitmap(base64Str: String): Bitmap? {
    if (base64Str.isBlank()) return null
    return try {
        val bytes = Base64.decode(base64Str, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        val db = FirebaseFirestore.getInstance()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = PrimaryBrown,
                    background = BgCream,
                    surface = Color.White
                )
            ) {
                NyumbaniApp(db)
            }
        }
    }
}

@Composable
fun NyumbaniApp(db: FirebaseFirestore) {
    val context = LocalContext.current

    var currentScreen by remember { mutableStateOf("login") }
    var currentUser by remember { mutableStateOf<UserProfile?>(null) }
    var selectedCategory by remember { mutableStateOf("Plumbing") }
    var pendingDescription by remember { mutableStateOf("") }
    var pendingUrgency by remember { mutableStateOf("Urgent") }
    var selectedGalleryPhoto by remember { mutableStateOf<GalleryPhoto?>(null) }

    val posts = remember { mutableStateListOf<BulletinPost>() }
    val galleryPhotos = remember { mutableStateListOf<GalleryPhoto>() }
    val requests = remember { mutableStateListOf<ServiceRequest>() }

    // Real-time Firestore listeners
    LaunchedEffect(Unit) {
        db.collection("posts")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val fetched = snapshot.documents.map { doc ->
                        BulletinPost(
                            id = doc.id,
                            authorName = doc.getString("authorName") ?: "",
                            authorPhone = doc.getString("authorPhone") ?: "",
                            role = doc.getString("role") ?: "tenant",
                            content = doc.getString("content") ?: "",
                            timeAgo = doc.getString("timeAgo") ?: "Just now",
                            pinned = doc.getBoolean("pinned") ?: false,
                            imageBase64 = doc.getString("imageBase64") ?: "",
                            createdAt = doc.getLong("createdAt") ?: 0L
                        )
                    }.sortedWith(compareByDescending<BulletinPost> { it.pinned }.thenByDescending { it.createdAt })

                    posts.clear()
                    posts.addAll(fetched)
                }
            }

        db.collection("photos")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val fetchedPhotos = snapshot.documents.map { doc ->
                        GalleryPhoto(
                            id = doc.id,
                            caption = doc.getString("caption") ?: "Estate photo",
                            authorName = doc.getString("authorName") ?: "Resident",
                            authorPhone = doc.getString("authorPhone") ?: "",
                            timeAgo = doc.getString("timeAgo") ?: "Just now",
                            imageBase64 = doc.getString("imageBase64") ?: "",
                            createdAt = doc.getLong("createdAt") ?: 0L
                        )
                    }.sortedByDescending { it.createdAt }

                    galleryPhotos.clear()
                    galleryPhotos.addAll(fetchedPhotos)
                }
            }

        db.collection("service_requests").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                requests.clear()
                for (doc in snapshot.documents) {
                    requests.add(
                        ServiceRequest(
                            id = doc.id,
                            category = doc.getString("category") ?: "",
                            description = doc.getString("description") ?: "",
                            urgency = doc.getString("urgency") ?: "Normal",
                            status = doc.getString("status") ?: "Pending",
                            provider = doc.getString("provider") ?: "Joseph — plumber",
                            tenantName = doc.getString("tenantName") ?: "",
                            tenantPhone = doc.getString("tenantPhone") ?: "",
                            unit = doc.getString("unit") ?: ""
                        )
                    )
                }
            }
        }
    }

    val isAdmin = currentUser?.role == "manager"
    val showBottomBar = currentScreen in listOf("home", "board", "gallery", "services", "profile")

    Scaffold(
        containerColor = BgCream,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
                    val navItems = listOf(
                        Triple("home", "Home", Icons.Default.Home),
                        Triple("board", "Board", Icons.Default.Forum),
                        Triple("gallery", "Photos", Icons.Default.PhotoLibrary),
                        Triple("services", "Services", Icons.Default.Build),
                        Triple("profile", "Profile", Icons.Default.Person)
                    )
                    navItems.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = currentScreen == route,
                            onClick = { currentScreen = route },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PrimaryBrown,
                                selectedTextColor = PrimaryBrown,
                                indicatorColor = CardCream
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (currentScreen) {
                // 01. LOGIN SCREEN (Clean: no demo credentials or registration hints displayed)
                "login" -> {
                    var phone by remember { mutableStateOf("") }
                    var password by remember { mutableStateOf("") }
                    var isLoggingIn by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(PrimaryBrown),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Apartment, contentDescription = null, tint = BgCream, modifier = Modifier.size(34.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Nyumbani", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        Text("Your estate, in one place", fontSize = 13.sp, color = TextMuted)
                        Spacer(Modifier.height(24.dp))

                        OutlinedTextField(
                            value = phone, onValueChange = { phone = it },
                            label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(18.dp))

                        Button(
                            enabled = !isLoggingIn,
                            onClick = {
                                val cleanPhone = phone.trim()
                                val cleanPass = password.trim()
                                if (cleanPhone.isEmpty() || cleanPass.isEmpty()) {
                                    Toast.makeText(context, "Enter phone number and password", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                // Hardcoded Super-User Admin Check
                                if (cleanPhone == "0722000222" && cleanPass == "1234") {
                                    currentUser = UserProfile(
                                        id = "admin_master",
                                        fullName = "David Mwangi",
                                        phone = cleanPhone,
                                        unit = "Admin Office",
                                        role = "manager"
                                    )
                                    currentScreen = "home"
                                    return@Button
                                }

                                // Registered Tenant Check in Firestore
                                isLoggingIn = true
                                db.collection("users")
                                    .whereEqualTo("phone", cleanPhone)
                                    .get()
                                    .addOnSuccessListener { docs ->
                                        isLoggingIn = false
                                        val matchedDoc = docs.documents.firstOrNull { it.getString("password") == cleanPass }
                                        if (matchedDoc != null) {
                                            currentUser = UserProfile(
                                                id = matchedDoc.id,
                                                fullName = matchedDoc.getString("fullName") ?: "Resident",
                                                phone = cleanPhone,
                                                unit = matchedDoc.getString("unit") ?: "",
                                                estate = matchedDoc.getString("estate") ?: "Greenwood Estate",
                                                role = "tenant"
                                            )
                                            currentScreen = "home"
                                        } else {
                                            Toast.makeText(context, "Invalid phone number or password", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        isLoggingIn = false
                                        Toast.makeText(context, "Login error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrown),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (isLoggingIn) "Logging in..." else "Log in", fontWeight = FontWeight.Bold)
                        }

                        TextButton(onClick = { currentScreen = "signup" }) {
                            Text("New here? Create an account", color = PrimaryBrown, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // 01b. SIGN UP SCREEN (Strictly registers new users as "tenant" only)
                "signup" -> {
                    var name by remember { mutableStateOf("") }
                    var phone by remember { mutableStateOf("") }
                    var unit by remember { mutableStateOf("") }
                    var pass by remember { mutableStateOf("") }
                    var isRegistering by remember { mutableStateOf(false) }

                    Column(Modifier.fillMaxSize()) {
                        TopHeader("Create account", "Join Greenwood Estate", onBack = { currentScreen = "login" })
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone number") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("House / unit number") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

                            Spacer(Modifier.height(6.dp))

                            Button(
                                enabled = !isRegistering,
                                onClick = {
                                    val cleanName = name.trim()
                                    val cleanPhone = phone.trim()
                                    val cleanUnit = unit.trim()
                                    val cleanPass = pass.trim()

                                    if (cleanName.isBlank() || cleanPhone.isBlank() || cleanUnit.isBlank() || cleanPass.isBlank()) {
                                        Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (cleanPhone == "0722 000 222") {
                                        Toast.makeText(context, "This phone number is reserved", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    isRegistering = true
                                    // Prevent duplicate phone registration
                                    db.collection("users").whereEqualTo("phone", cleanPhone).get()
                                        .addOnSuccessListener { existing ->
                                            if (!existing.isEmpty) {
                                                isRegistering = false
                                                Toast.makeText(context, "An account with this phone already exists", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val userMap = hashMapOf(
                                                    "fullName" to cleanName,
                                                    "phone" to cleanPhone,
                                                    "unit" to cleanUnit,
                                                    "password" to cleanPass,
                                                    "estate" to "Greenwood Estate",
                                                    "role" to "tenant" // Enforced tenant role
                                                )
                                                db.collection("users").add(userMap)
                                                    .addOnSuccessListener { docRef ->
                                                        isRegistering = false
                                                        currentUser = UserProfile(docRef.id, cleanName, cleanPhone, cleanUnit, "Greenwood Estate", "tenant")
                                                        Toast.makeText(context, "Account created!", Toast.LENGTH_SHORT).show()
                                                        currentScreen = "home"
                                                    }
                                                    .addOnFailureListener { e ->
                                                        isRegistering = false
                                                        Toast.makeText(context, "Registration failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                        }
                                        .addOnFailureListener { e ->
                                            isRegistering = false
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrown)
                            ) { Text(if (isRegistering) "Creating account..." else "Create account") }
                        }
                    }
                }

                // 02. HOME SCREEN (Synced with Bulletin Board & Photo Gallery)
                "home" -> {
                    val user = currentUser ?: UserProfile()
                    Column(Modifier.fillMaxSize()) {
                        TopHeader("Home", user.estate)
                        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item {
                                Surface(color = CardCream, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("Good morning,", fontSize = 12.sp, color = TextMuted)
                                        Text(user.fullName.substringBefore(" "), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                        Text("${user.estate} · Unit ${user.unit}", fontSize = 12.sp, color = TextMuted)
                                    }
                                }
                            }

                            // SYNCED BULLETIN BOARD NEWS
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Estate news (${posts.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextMuted)
                                    Text("See all", fontSize = 12.sp, color = PrimaryBrown, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { currentScreen = "board" })
                                }
                            }
                            if (posts.isEmpty()) {
                                item {
                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(11.dp),
                                        modifier = Modifier.fillMaxWidth().border(1.dp, BorderTan, RoundedCornerShape(11.dp)).clickable { currentScreen = "newpost" }
                                    ) {
                                        Text("No bulletin posts yet. Tap here to share an update!", modifier = Modifier.padding(14.dp), fontSize = 12.sp, color = TextMuted)
                                    }
                                }
                            } else {
                                items(posts.take(2)) { post ->
                                    val canDeletePost = isAdmin || (post.authorPhone.isNotEmpty() && post.authorPhone == user.phone)
                                    PostCard(
                                        post = post,
                                        canDelete = canDeletePost,
                                        onClick = { currentScreen = "board" },
                                        onDelete = {
                                            db.collection("posts").document(post.id).delete()
                                                .addOnSuccessListener { Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show() }
                                        }
                                    )
                                }
                            }

                            // SYNCED GALLERY PHOTOS STRIP
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Recent photos (${galleryPhotos.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextMuted)
                                    Text("Open gallery", fontSize = 12.sp, color = PrimaryBrown, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { currentScreen = "gallery" })
                                }
                                Spacer(Modifier.height(6.dp))
                                if (galleryPhotos.isEmpty()) {
                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(11.dp),
                                        modifier = Modifier.fillMaxWidth().border(1.dp, BorderTan, RoundedCornerShape(11.dp)).clickable { currentScreen = "upload" }
                                    ) {
                                        Text("No photos uploaded yet. Tap to add a photo to the gallery.", modifier = Modifier.padding(14.dp), fontSize = 12.sp, color = TextMuted)
                                    }
                                } else {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(galleryPhotos.take(6)) { photoItem ->
                                            val bmp = remember(photoItem.imageBase64) { base64ToBitmap(photoItem.imageBase64) }
                                            if (bmp != null) {
                                                Image(
                                                    bitmap = bmp.asImageBitmap(),
                                                    contentDescription = photoItem.caption,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(92.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .border(1.dp, BorderTan, RoundedCornerShape(10.dp))
                                                        .clickable {
                                                            selectedGalleryPhoto = photoItem
                                                            currentScreen = "photo"
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Text("Quick actions", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextMuted)
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    QuickTile("Bulletin", Icons.Default.Forum, Modifier.weight(1f)) { currentScreen = "board" }
                                    QuickTile("Services", Icons.Default.Build, Modifier.weight(1f)) { currentScreen = "services" }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    QuickTile("Photos", Icons.Default.PhotoLibrary, Modifier.weight(1f)) { currentScreen = "gallery" }
                                    QuickTile("Profile", Icons.Default.Person, Modifier.weight(1f)) { currentScreen = "profile" }
                                }
                            }
                        }
                    }
                }

                // 03. BULLETIN BOARD
                "board" -> {
                    val user = currentUser ?: UserProfile()
                    Column(Modifier.fillMaxSize()) {
                        TopHeader("Bulletin board", "Tenants and management")
                        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item {
                                Button(
                                    onClick = { currentScreen = "newpost" },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrown),
                                    shape = RoundedCornerShape(10.dp)
                                ) { Text("+ New post") }
                            }
                            if (posts.isEmpty()) {
                                item {
                                    Text(
                                        "The bulletin board is empty. Tap '+ New post' to add text and photos.",
                                        color = TextMuted,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 12.dp)
                                    )
                                }
                            } else {
                                items(posts) { post ->
                                    val canDeletePost = isAdmin || (post.authorPhone.isNotEmpty() && post.authorPhone == user.phone)
                                    PostCard(
                                        post = post,
                                        canDelete = canDeletePost,
                                        onClick = {},
                                        onDelete = {
                                            db.collection("posts").document(post.id).delete()
                                                .addOnSuccessListener { Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show() }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 03b. NEW POST (Saves authorPhone for ownership permissions)
                "newpost" -> {
                    var postText by remember { mutableStateOf("") }
                    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
                    var isPosting by remember { mutableStateOf(false) }
                    val user = currentUser ?: UserProfile()

                    val imagePicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        selectedImageUri = uri
                    }

                    val previewBitmap = remember(selectedImageUri) {
                        selectedImageUri?.let { uri ->
                            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                        }
                    }

                    Column(Modifier.fillMaxSize()) {
                        TopHeader("New post", "Share with your neighbours", onBack = { currentScreen = "board" })
                        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item {
                                OutlinedTextField(
                                    value = postText, onValueChange = { postText = it },
                                    label = { Text("Write something...") },
                                    modifier = Modifier.fillMaxWidth().height(120.dp)
                                )
                            }
                            item {
                                Text("Add a photo (optional)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextMuted)
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    color = CardCream,
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { imagePicker.launch("image/*") }
                                ) {
                                    if (previewBitmap != null) {
                                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Image(
                                                bitmap = previewBitmap.asImageBitmap(),
                                                contentDescription = "Selected photo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp))
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text("Tap to change photo", fontSize = 12.sp, color = PrimaryBrown, fontWeight = FontWeight.SemiBold)
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.padding(vertical = 26.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = PrimaryBrown, modifier = Modifier.size(28.dp))
                                            Spacer(Modifier.height(6.dp))
                                            Text("Choose image from device", fontSize = 13.sp, color = TextDark, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }
                            item {
                                Button(
                                    enabled = !isPosting,
                                    onClick = {
                                        if (postText.isNotBlank() || selectedImageUri != null) {
                                            isPosting = true
                                            val encodedImage = selectedImageUri?.let { uriToCompressedBase64(context, it) } ?: ""
                                            val newPost = hashMapOf(
                                                "authorName" to user.fullName,
                                                "authorPhone" to user.phone,
                                                "role" to user.role,
                                                "content" to postText,
                                                "timeAgo" to "Just now",
                                                "pinned" to (user.role == "manager"),
                                                "imageBase64" to encodedImage,
                                                "createdAt" to System.currentTimeMillis()
                                            )
                                            db.collection("posts").add(newPost)
                                                .addOnSuccessListener {
                                                    isPosting = false
                                                    Toast.makeText(context, "Posted to bulletin!", Toast.LENGTH_SHORT).show()
                                                    currentScreen = "board"
                                                }
                                                .addOnFailureListener { e ->
                                                    isPosting = false
                                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                                }
                                        } else {
                                            Toast.makeText(context, "Write a message or choose a photo", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrown)
                                ) {
                                    Text(if (isPosting) "Posting..." else "Post")
                                }
                            }
                        }
                    }
                }

                // 04. PHOTO GALLERY
                "gallery" -> {
                    Column(Modifier.fillMaxSize()) {
                        TopHeader("Photo gallery", "${galleryPhotos.size} photos")
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { currentScreen = "upload" },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrown),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("+ Upload photo")
                            }

                            if (galleryPhotos.isEmpty()) {
                                Surface(
                                    color = CardCream,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { currentScreen = "upload" }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = PrimaryBrown, modifier = Modifier.size(36.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Text("No photos in the gallery yet", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                                        Text("Tap '+ Upload photo' above to add your own pictures.", fontSize = 12.sp, color = TextMuted)
                                    }
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(galleryPhotos.size) { idx ->
                                        val photoItem = galleryPhotos[idx]
                                        val bmp = remember(photoItem.imageBase64) { base64ToBitmap(photoItem.imageBase64) }
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = photoItem.caption,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        selectedGalleryPhoto = photoItem
                                                        currentScreen = "photo"
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 04b. FULL PHOTO VIEW SCREEN (Delete button visible ONLY to owner or Admin)
                "photo" -> {
                    val photoItem = selectedGalleryPhoto
                    val user = currentUser ?: UserProfile()
                    var isDeleting by remember { mutableStateOf(false) }

                    Column(Modifier.fillMaxSize()) {
                        TopHeader("Photo", "Community gallery", onBack = { currentScreen = "gallery" })
                        if (photoItem != null) {
                            val bmp = remember(photoItem.imageBase64) { base64ToBitmap(photoItem.imageBase64) }
                            val canDeletePhoto = isAdmin || (photoItem.authorPhone.isNotEmpty() && photoItem.authorPhone == user.phone)

                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = photoItem.caption,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                Text(photoItem.caption.ifBlank { "Estate photo" }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                Text("by ${photoItem.authorName} · ${photoItem.timeAgo}", fontSize = 12.sp, color = TextMuted)

                                if (canDeletePhoto) {
                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        enabled = !isDeleting,
                                        onClick = {
                                            if (photoItem.id.isNotEmpty()) {
                                                isDeleting = true
                                                db.collection("photos").document(photoItem.id).delete()
                                                    .addOnSuccessListener {
                                                        isDeleting = false
                                                        selectedGalleryPhoto = null
                                                        Toast.makeText(context, "Photo deleted", Toast.LENGTH_SHORT).show()
                                                        currentScreen = "gallery"
                                                    }
                                                    .addOnFailureListener { e ->
                                                        isDeleting = false
                                                        Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(46.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete photo", modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (isDeleting) "Deleting..." else "Delete photo", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                // 04c. UPLOAD PHOTO SCREEN (Saves authorPhone for ownership permissions)
                "upload" -> {
                    var caption by remember { mutableStateOf("") }
                    var selectedUri by remember { mutableStateOf<Uri?>(null) }
                    var isUploading by remember { mutableStateOf(false) }
                    val user = currentUser ?: UserProfile()

                    val galleryPicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri: Uri? ->
                        selectedUri = uri
                    }

                    val previewBmp = remember(selectedUri) {
                        selectedUri?.let { uri ->
                            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                        }
                    }

                    Column(Modifier.fillMaxSize()) {
                        TopHeader("Upload photo", "Add to estate gallery", onBack = { currentScreen = "gallery" })
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                color = CardCream,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { galleryPicker.launch("image/*") }
                            ) {
                                if (previewBmp != null) {
                                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Image(
                                            bitmap = previewBmp.asImageBitmap(),
                                            contentDescription = "Preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text("Tap to pick a different photo", fontSize = 12.sp, color = PrimaryBrown, fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.padding(vertical = 44.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = PrimaryBrown, modifier = Modifier.size(34.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Text("Choose a photo from device", fontSize = 13.sp, color = TextDark, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = caption,
                                onValueChange = { caption = it },
                                label = { Text("Caption (e.g., Community clean-up day)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Button(
                                enabled = !isUploading,
                                onClick = {
                                    val uri = selectedUri
                                    if (uri == null) {
                                        Toast.makeText(context, "Please choose a photo first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isUploading = true
                                        val encoded = uriToCompressedBase64(context, uri)
                                        val photoData = hashMapOf(
                                            "caption" to caption.ifBlank { "Community photo" },
                                            "authorName" to user.fullName,
                                            "authorPhone" to user.phone,
                                            "timeAgo" to "Just now",
                                            "imageBase64" to encoded,
                                            "createdAt" to System.currentTimeMillis()
                                        )
                                        db.collection("photos").add(photoData)
                                            .addOnSuccessListener {
                                                isUploading = false
                                                Toast.makeText(context, "Photo uploaded to gallery!", Toast.LENGTH_SHORT).show()
                                                currentScreen = "gallery"
                                            }
                                            .addOnFailureListener { e ->
                                                isUploading = false
                                                Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrown)
                            ) {
                                Text(if (isUploading) "Uploading..." else "Upload")
                            }
                        }
                    }
                }

                // 05. SERVICES
                "services" -> {
                    val categories = listOf(
                        "Repairs" to Icons.Default.Handyman,
                        "Cleaning" to Icons.Default.CleaningServices,
                        "Electrical" to Icons.Default.Bolt,
                        "Plumbing" to Icons.Default.Plumbing,
                        "Security" to Icons.Default.Security,
                        "Garbage" to Icons.Default.DeleteOutline
                    )
                    val user = currentUser ?: UserProfile()
                    val visibleRequests = if (isAdmin) requests else requests.filter { it.tenantPhone == user.phone || it.tenantPhone.isEmpty() }

                    Column(Modifier.fillMaxSize()) {
                        TopHeader("Services", "Household help for your unit")
                        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item {
                                Text("Request a service", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextMuted)
                                Spacer(Modifier.height(8.dp))
                                categories.chunked(3).forEach { rowItems ->
                                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rowItems.forEach { (cat, icon) ->
                                            QuickTile(cat, icon, Modifier.weight(1f)) {
                                                selectedCategory = cat
                                                currentScreen = "request"
                                            }
                                        }
                                    }
                                }
                            }
                            item { Text("My requests", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextMuted) }
                            items(visibleRequests) { req ->
                                Surface(color = Color.White, shape = RoundedCornerShape(11.dp), modifier = Modifier.fillMaxWidth().border(1.dp, BorderTan, RoundedCornerShape(11.dp))) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(req.category, fontWeight = FontWeight.Bold, color = TextDark)
                                            Surface(color = CardCream, shape = RoundedCornerShape(20.dp)) {
                                                Text(req.status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, color = PrimaryBrown, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                        Text(req.description, fontSize = 13.sp, color = TextDark, modifier = Modifier.padding(vertical = 4.dp))
                                        Text("${req.urgency} · ${req.provider}", fontSize = 11.sp, color = TextMuted)
                                    }
                                }
                            }
                        }
                    }
                }

                // 05b. REQUEST SERVICE FORM
                "request" -> {
                    var desc by remember { mutableStateOf("") }
                    var urgency by remember { mutableStateOf("Urgent") }
                    val user = currentUser ?: UserProfile()

                    Column(Modifier.fillMaxSize()) {
                        TopHeader("Request: $selectedCategory", "Unit ${user.unit}", onBack = { currentScreen = "services" })
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = desc, onValueChange = { desc = it },
                                label = { Text("Describe the issue (e.g., Kitchen tap leaking)") },
                                modifier = Modifier.fillMaxWidth().height(110.dp)
                            )
                            Text("Urgency", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Normal", "Urgent", "Emergency").forEach { level ->
                                    FilterChip(selected = urgency == level, onClick = { urgency = level }, label = { Text(level) })
                                }
                            }
                            Button(
                                onClick = {
                                    pendingDescription = desc.ifBlank { "$selectedCategory assistance needed" }
                                    pendingUrgency = urgency
                                    currentScreen = "matched"
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrown)
                            ) { Text("Find a provider") }
                        }
                    }
                }

                // 05c. MATCHED PROVIDERS
                "matched" -> {
                    val providers = listOf(
                        Triple("Joseph Kamau", "Plumber / Technician · 4.8 ★ · on site", "JK"),
                        Triple("Peter Mutua", "Handyman · 4.5 ★ · 15 min away", "PM"),
                        Triple("Sarah Wanjiru", "Specialist · 4.3 ★ · 30 min away", "SW")
                    )
                    var selectedProvider by remember { mutableStateOf(providers[0].first) }
                    val user = currentUser ?: UserProfile()

                    Column(Modifier.fillMaxSize()) {
                        TopHeader("Matched providers", "Vetted for Greenwood Estate", onBack = { currentScreen = "request" })
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            providers.forEach { (name, subtitle, initials) ->
                                val isSelected = selectedProvider == name
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) PrimaryBrown else BorderTan, RoundedCornerShape(11.dp))
                                        .clickable { selectedProvider = name }
                                ) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(36.dp).clip(CircleShape).background(PrimaryBrown), contentAlignment = Alignment.Center) {
                                            Text(initials, color = BgCream, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(name, fontWeight = FontWeight.Bold, color = TextDark)
                                            Text(subtitle, fontSize = 11.sp, color = TextMuted)
                                        }
                                        if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = PrimaryBrown)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val reqData = hashMapOf(
                                        "category" to selectedCategory,
                                        "description" to pendingDescription,
                                        "urgency" to pendingUrgency,
                                        "status" to "In progress",
                                        "provider" to selectedProvider,
                                        "tenantName" to user.fullName,
                                        "tenantPhone" to user.phone,
                                        "unit" to user.unit
                                    )
                                    db.collection("service_requests").add(reqData).addOnSuccessListener {
                                        Toast.makeText(context, "Booked $selectedProvider!", Toast.LENGTH_SHORT).show()
                                        currentScreen = "services"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrown)
                            ) { Text("Book $selectedProvider") }
                        }
                    }
                }

                // 06. PROFILE (Admin panel button visible ONLY to Admin)
                "profile" -> {
                    val user = currentUser ?: UserProfile()
                    Column(Modifier.fillMaxSize()) {
                        TopHeader("Profile", user.estate)
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(72.dp).clip(CircleShape).background(PrimaryBrown), contentAlignment = Alignment.Center) {
                                Text(user.fullName.take(2).uppercase(), color = BgCream, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(user.fullName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text("Unit ${user.unit} · ${user.estate}", fontSize = 13.sp, color = TextMuted)

                            Surface(color = CardCream, shape = RoundedCornerShape(20.dp)) {
                                Text(
                                    if (isAdmin) "Estate Manager (Super User)" else "Tenant",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryBrown
                                )
                            }

                            Surface(color = Color.White, shape = RoundedCornerShape(11.dp), modifier = Modifier.fillMaxWidth().border(1.dp, BorderTan, RoundedCornerShape(11.dp))) {
                                Column(Modifier.padding(12.dp)) {
                                    Text("Phone", fontSize = 11.sp, color = TextMuted)
                                    Text(user.phone, fontSize = 14.sp, color = TextDark)
                                }
                            }

                            if (isAdmin) {
                                Button(
                                    onClick = { currentScreen = "admin" },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBrown)
                                ) { Text("Manage estate (Admin Super Panel)") }
                            }

                            OutlinedButton(
                                onClick = { currentUser = null; currentScreen = "login" },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Log out", color = PrimaryBrown) }
                        }
                    }
                }

                // 07. ESTATE MANAGER SUPER-USER ADMIN SCREEN
                "admin" -> {
                    Column(Modifier.fillMaxSize()) {
                        TopHeader("Manage estate", "Super User Controls", onBack = { currentScreen = "profile" })
                        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatBox("${requests.count { it.status != "Completed" }}", "open reqs", Modifier.weight(1f))
                                    StatBox("${posts.size}", "posts", Modifier.weight(1f))
                                    StatBox("${galleryPhotos.size}", "photos", Modifier.weight(1f))
                                }
                            }
                            item { Text("All service requests (Tap status to toggle, trash to delete)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextMuted) }
                            items(requests) { req ->
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(11.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, BorderTan, RoundedCornerShape(11.dp))
                                        .clickable {
                                            if (req.id.isNotEmpty()) {
                                                val nextStatus = if (req.status == "Completed") "In progress" else "Completed"
                                                db.collection("service_requests").document(req.id)
                                                    .update("status", nextStatus)
                                                    .addOnSuccessListener {
                                                        Toast.makeText(context, "Updated to $nextStatus", Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                        }
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("${req.category} — ${req.tenantName} (${req.unit})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(req.status, fontSize = 11.sp, color = PrimaryBrown, fontWeight = FontWeight.Bold)
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete request",
                                                    tint = DangerRed,
                                                    modifier = Modifier.size(18.dp).clickable {
                                                        db.collection("service_requests").document(req.id).delete()
                                                            .addOnSuccessListener { Toast.makeText(context, "Request removed", Toast.LENGTH_SHORT).show() }
                                                    }
                                                )
                                            }
                                        }
                                        Text(req.description, fontSize = 12.sp, color = TextDark)
                                        Text("Assigned: ${req.provider}", fontSize = 11.sp, color = TextMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- REUSABLE UI COMPONENTS ---
@Composable
fun TopHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().background(PrimaryBrown).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(title, color = BgCream, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = BgCream.copy(alpha = 0.75f), fontSize = 12.sp)
        }
        if (onBack != null) {
            Text("Back", color = BgCream, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onBack() })
        }
    }
}

@Composable
fun PostCard(post: BulletinPost, canDelete: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    val postBitmap = remember(post.imageBase64) { base64ToBitmap(post.imageBase64) }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(11.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, if (post.pinned) PrimaryBrown else BorderTan, RoundedCornerShape(11.dp)).clickable { onClick() }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(post.authorName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = if (post.role == "manager") PrimaryBrown else CardCream, shape = RoundedCornerShape(20.dp)) {
                        Text(
                            post.role,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            color = if (post.role == "manager") BgCream else TextDark
                        )
                    }
                    if (canDelete && onDelete != null && post.id.isNotEmpty()) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete post",
                            tint = DangerRed,
                            modifier = Modifier.size(18.dp).clickable { onDelete() }
                        )
                    }
                }
            }
            if (post.content.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(post.content, fontSize = 13.sp, color = TextDark)
            }
            if (postBitmap != null) {
                Spacer(Modifier.height(8.dp))
                Image(
                    bitmap = postBitmap.asImageBitmap(),
                    contentDescription = "Post image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp))
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(post.timeAgo, fontSize = 11.sp, color = TextMuted)
        }
    }
}

@Composable
fun QuickTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = CardCream,
        shape = RoundedCornerShape(11.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = PrimaryBrown, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
        }
    }
}

@Composable
fun StatBox(number: String, label: String, modifier: Modifier = Modifier) {
    Surface(color = CardCream, shape = RoundedCornerShape(11.dp), modifier = modifier) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(number, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Text(label, fontSize = 11.sp, color = TextMuted)
        }
    }
}