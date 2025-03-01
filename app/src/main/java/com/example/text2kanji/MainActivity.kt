package com.example.text2kanji

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsMessage
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.text2kanji.ui.theme.Text2KanjiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslateLanguage
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope

class MainActivity : ComponentActivity() {

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var smsReceiver: SmsReceiver
    private lateinit var appUpdateService: AppUpdateService
    private lateinit var connectivityReceiver: NetworkUtils.ConnectivityReceiver // Correct type
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appUpdateService = AppUpdateService(this)
        connectivityReceiver = NetworkUtils.ConnectivityReceiver { // Initialize with lambda
            checkForUpdates()
        }
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(connectivityReceiver, filter)

        // Register for permission results
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val readSmsGranted = permissions[Manifest.permission.READ_SMS] ?: false
            val receiveSmsGranted = permissions[Manifest.permission.RECEIVE_SMS] ?: false

            if (readSmsGranted && receiveSmsGranted) {
                loadContent()
            } else {
                showPermissionDeniedMessage()
            }
        }

        // Check and request permissions if needed
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            loadContent() // Permissions already granted
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS
                )
            )
        }
    }

    private fun loadContent() {
        setContent {
            Text2KanjiTheme {
                var conversations by remember { mutableStateOf<List<Pair<String, List<Pair<String, Long>>>>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    // Load SMS conversations in the background
                    lifecycleScope.launch {
                        conversations = loadSmsConversations()
                        isLoading = false
                    }
                }

                // Listen for new SMS via BroadcastReceiver
                if (!::smsReceiver.isInitialized) {
                    smsReceiver = SmsReceiver { newMessage ->
                        // Handle new SMS received
                        lifecycleScope.launch {
                            conversations = loadSmsConversations() // Reload the list of SMS in a coroutine
                        }
                    }
                    // Use Intent.ACTION_RECEIVE to receive SMS
                    val filter = IntentFilter("android.provider.Telephony.SMS_RECEIVED")
                    registerReceiver(smsReceiver, filter)
                }

                // Show loading indicator in the app UI
                if (isLoading) {
                    LoadingScreen() // This is the progress indicator within the app
                } else {
                    MainApp(conversations)
                }
            }
        }
    }

    private suspend fun loadSmsConversations(): List<Pair<String, List<Pair<String, Long>>>> {
        return withContext(Dispatchers.IO) {
            val contentResolver = contentResolver
            val cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                null,
                null,
                null
            )

            val smsMap = mutableMapOf<String, MutableList<Pair<String, Long>>>()
            cursor?.use {
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    val address = it.getString(addressIndex)
                    val body = it.getString(bodyIndex)
                    val date = it.getLong(dateIndex)
                    if (!address.isNullOrEmpty()) {
                        smsMap.getOrPut(address) { mutableListOf() }.add(body to date)
                    }
                }
            }
            smsMap.map { it.key to it.value }
        }
    }

    private fun showPermissionDeniedMessage() {
        setContent {
            Text2KanjiTheme {
                Text(text = "Permission to read and receive SMS is required to continue.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the SMS receiver when the activity is destroyed
        if (::smsReceiver.isInitialized) {
            unregisterReceiver(smsReceiver)
        }
        unregisterReceiver(connectivityReceiver)
    }

    private fun checkForUpdates() {
        coroutineScope.launch {
            if (NetworkUtils.isNetworkAvailable(this@MainActivity)) { // Use the utility function
                appUpdateService.checkForAppUpdate()
            }
        }
    }
}

// SmsReceiver class that listens for new SMS
class SmsReceiver(val onSmsReceived: (String) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val pdus = intent?.extras?.get("pdus") as? Array<*>
        pdus?.forEach { pdu ->
            val smsMessage = SmsMessage.createFromPdu(pdu as ByteArray)
            val sender = smsMessage.originatingAddress
            sender?.let {
                // Handle the incoming message
                onSmsReceived(it)
            }
        }
    }
}

@Composable
fun MainApp(conversations: List<Pair<String, List<Pair<String, Long>>>>) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "conversationList") {
        composable("conversationList") {
            ConversationListScreen(
                conversations = conversations,
                onConversationSelected = { conversation ->
                    navController.navigate("messageList/${conversation.first}")
                },
                onVersionClick = { navController.navigate("versionScreen") } // Add this
            )
        }
        composable("messageList/{address}") { backStackEntry ->
            val address = backStackEntry.arguments?.getString("address") ?: ""
            val messages = conversations.find { it.first == address }?.second.orEmpty()
            MessageListScreen(
                messages = messages, // Pass the Pair<String, Long> list directly
                onBack = { navController.popBackStack() },
                phoneNumber = address // Pass the phone number to the screen
            )
        }
        composable("versionScreen") {
            VersionScreen(navController)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    conversations: List<Pair<String, List<Pair<String, Long>>>>,
    onConversationSelected: (Pair<String, List<Pair<String, Long>>>) -> Unit,
    onVersionClick: () -> Unit
) {
    val context = LocalContext.current
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Messages",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp)
                        )
                        Spacer(modifier = Modifier.width(10.dp)) // Add some space between texts
                        Text(
                            text = "v$versionName",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                textDecoration = TextDecoration.Underline
                            ),
                            modifier = Modifier.clickable {
                                onVersionClick()
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        // If conversations list is empty, show the image and text
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Image - Replace with your actual drawable resource
                    Image(
                        painter = painterResource(id = R.drawable.questionman), // Replace with actual image resource
                        contentDescription = "No SMS Available",
                        modifier = Modifier.size(160.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Text below the image
                    Text(
                        text = "No SMS Available",
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // If conversations are not empty, show the list as usual
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(conversations.size) { index ->
                    val conversation = conversations[index]
                    val lastMessage = conversation.second.firstOrNull()
                    val timestamp = lastMessage?.second?.let { formatTimestamp(it) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .clickable { onConversationSelected(conversation) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile Image
                        Image(
                            painter = painterResource(id = R.drawable.profile_placeholder), // Replace with actual image resource
                            contentDescription = "Profile Image",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            // Phone number
                            Text(
                                text = conversation.first,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // Last message preview
                            Text(
                                text = lastMessage?.first ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        // Timestamp on the right side
                        if (timestamp != null) {
                            Text(
                                text = timestamp,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Divider()
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionScreen(navController: NavController) {
    var selectedLanguage by remember { mutableStateOf("en") } // Default to English
    val context = LocalContext.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    // Load the correct version history based on selected language
    val versionHistory = if (selectedLanguage == "en") {
        context.resources.getStringArray(R.array.version_history_en)
    } else {
        context.resources.getStringArray(R.array.version_history_jp)
    }

    val appIcon = painterResource(id = R.drawable.texttokanjiicon)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Version History", fontSize = 20.sp, fontWeight = FontWeight.Bold) },

                navigationIcon = {
                    IconButton(onClick = { backDispatcher?.onBackPressed() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
                ,
                actions = {
                    // USA Flag Button
                    IconButton(onClick = { selectedLanguage = "en" }) {
                        Image(
                            painter = painterResource(id = R.drawable.usaflag),
                            contentDescription = "English",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    // Japan Flag Button
                    IconButton(onClick = { selectedLanguage = "jp" }) {
                        Image(
                            painter = painterResource(id = R.drawable.japanflag),
                            contentDescription = "Japanese",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Image with low opacity
            Image(
                painter = appIcon,
                contentDescription = "App Icon Background",
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                contentScale = ContentScale.Fit,
                alpha = 0.7f
            )

            // Foreground content
            LazyColumn(
                contentPadding = padding,
                modifier = Modifier.padding(16.dp)
            ) {
                items(versionHistory) { version ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = version,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
fun formatTimestamp(timestamp: Long): String {
    val currentTime = Calendar.getInstance()
    val messageTime = Calendar.getInstance()
    messageTime.timeInMillis = timestamp

    return when {
        isToday(messageTime, currentTime) -> {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(messageTime.time)
        }
        isSameYear(messageTime, currentTime) -> {
            SimpleDateFormat("MMM d", Locale.getDefault()).format(messageTime.time)
        }
        else -> {
            SimpleDateFormat("MMM d yyyy", Locale.getDefault()).format(messageTime.time)
        }
    }
}

fun isToday(messageTime: Calendar, currentTime: Calendar): Boolean {
    return messageTime.get(Calendar.YEAR) == currentTime.get(Calendar.YEAR) &&
            messageTime.get(Calendar.DAY_OF_YEAR) == currentTime.get(Calendar.DAY_OF_YEAR)
}

fun isSameYear(messageTime: Calendar, currentTime: Calendar): Boolean {
    return messageTime.get(Calendar.YEAR) == currentTime.get(Calendar.YEAR)
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MessageListScreen(
    messages: List<Pair<String, Long>>, // Pair of message body and timestamp
    onBack: () -> Unit,
    phoneNumber: String
) {
    var translatedMessages by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showCopyMenu by remember { mutableStateOf(false) }
    var messageToCopy by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(phoneNumber) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Box(modifier = Modifier.fillMaxSize()) {
            val groupedMessages = messages.groupBy { timestamp ->
                // Convert timestamp to a human-readable date with day of the week
                SimpleDateFormat("EEEE, MMM dd, yyyy", Locale.getDefault())
                    .format(Date(timestamp.second))
            }

            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface),
                reverseLayout = true
            ) {
                groupedMessages.forEach { (date, messagesForDate) ->
                    items(messagesForDate.size) { index ->
                        val (message, timestamp) = messagesForDate[index]
                        val translatedMessage = translatedMessages[message]

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.profile_placeholder),
                                contentDescription = "Profile Image",
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .align(Alignment.Top)
                            )

                            Spacer(modifier = Modifier.width(15.dp))

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        shape = MaterialTheme.shapes.medium
                                    )
                                    .clip(MaterialTheme.shapes.medium)
                                    .padding(12.dp)
                                    .combinedClickable(
                                        onClick = { /* Do nothing on simple click */ },
                                        onLongClick = {
                                            messageToCopy = if (translatedMessage != null) {
                                                "$message\nTranslated: $translatedMessage"
                                            } else {
                                                message
                                            }
                                            showCopyMenu = true
                                        }
                                    )
                            ) {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                if (translatedMessage != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = translatedMessage,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 16.sp,
                                            color = Color.Gray
                                        ),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                        .format(Date(timestamp)),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.Gray
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    // Start translation
                                    coroutineScope.launch {
                                        translateTextToJapanese(message, context) { translatedText ->
                                            translatedMessages = translatedMessages + (message to translatedText)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = "Translate",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = date,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }

            if (showCopyMenu) {
                AlertDialog(
                    onDismissRequest = { showCopyMenu = false },
                    title = { Text("Copy Message") },
                    text = { Text("Would you like to copy this message?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(messageToCopy))
                                showCopyMenu = false
                            }
                        ) {
                            Text("Copy")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCopyMenu = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

fun translateTextToJapanese(text: String, context: Context, onTranslationComplete: (String) -> Unit) {
    val tagalogToEnglishOptions = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.TAGALOG)
        .setTargetLanguage(TranslateLanguage.ENGLISH)
        .build()
    val englishToJapaneseOptions = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.JAPANESE)
        .build()

    val tagalogToEnglishTranslator = Translation.getClient(tagalogToEnglishOptions)
    val englishToJapaneseTranslator = Translation.getClient(englishToJapaneseOptions)

    val conditions = DownloadConditions.Builder()
        .requireWifi()
        .build()

    // Check if the device has internet connection before attempting download
    if (!isInternetAvailable(context)) {
        Toast.makeText(context, "Connect to the internet to download translation files", Toast.LENGTH_SHORT).show()
        return
    }

    // Download both models if needed
    tagalogToEnglishTranslator.downloadModelIfNeeded(conditions)
        .addOnSuccessListener {
            englishToJapaneseTranslator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    tagalogToEnglishTranslator.translate(text)
                        .addOnSuccessListener { englishText ->
                            englishToJapaneseTranslator.translate(englishText)
                                .addOnSuccessListener { japaneseText ->
                                    onTranslationComplete(japaneseText)
                                }
                                .addOnFailureListener {
                                    onTranslationComplete("Failed to translate to Japanese")
                                }
                        }
                        .addOnFailureListener {
                            onTranslationComplete("Failed to translate to English")
                        }
                }
                .addOnFailureListener {
                    onTranslationComplete("Failed to download English-to-Japanese model")
                }
        }
        .addOnFailureListener {
            onTranslationComplete("Failed to download Tagalog-to-English model")
        }
}

// Helper function to check internet availability
fun isInternetAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val activeNetwork = connectivityManager.activeNetworkInfo
    return activeNetwork?.isConnected == true
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF4A4A4A)))), // Adding a gradient background
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(64.dp)  // Increase the size for a more noticeable spinner
                    .graphicsLayer(
                        // Add some scaling animation to make it feel more dynamic
                        scaleX = 1.2f,
                        scaleY = 1.2f
                    ),
                color = Color.White, // Custom color for the progress indicator
                strokeWidth = 6.dp // Thicker stroke for a more modern look
            )
            Spacer(modifier = Modifier.height(16.dp)) // Add some space between the spinner and text
            Text(
                text = "Loading...",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
@Preview(showBackground = true)
@Composable
fun PreviewConversationListScreen() {
    Text2KanjiTheme {
        ConversationListScreen(
            conversations = listOf(
                "1234567890" to listOf("Hi" to 1690185600000L, "Hello" to 1690272000000L),
                "9876543210" to listOf("How are you?" to 1690275600000L)
            ),
            onConversationSelected = {},
            onVersionClick = {}
        )
    }
}




