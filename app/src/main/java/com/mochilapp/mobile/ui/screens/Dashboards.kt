package com.mochilapp.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.mochilapp.mobile.R
import com.mochilapp.mobile.data.CompanyType
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.data.UserFirestore
import com.mochilapp.mobile.data.esPrivado
import com.mochilapp.mobile.data.holdsSeats
import com.mochilapp.mobile.ui.theme.t
import com.mochilapp.mobile.ui.viewmodels.AuthViewModel
import com.mochilapp.mobile.ui.viewmodels.CompanyViewModel
import com.mochilapp.mobile.ui.viewmodels.MarketplaceViewModel
import com.mochilapp.mobile.data.StoryFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelerDashboard(
    viewModel: MarketplaceViewModel,
    onServiceClick: (String) -> Unit,
    onSocialFeedClick: () -> Unit,
    onMapClick: () -> Unit,
    onAiClick: () -> Unit,
    onSearchClick: () -> Unit,
    onBookingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onLogout: () -> Unit
) {
    val services by viewModel.services.collectAsState()
    val recommended by viewModel.recommendedServices.collectAsState()
    val promos by viewModel.activePromos.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentPriceRange by viewModel.priceRange.collectAsState()
    val currentGuests by viewModel.guestsCount.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val isSeeding by viewModel.isSeeding.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Fila de círculos de empresas ("abierto ahora" = algún servicio con isOpen)
    val companies by viewModel.companies.collectAsState()
    val allServices by viewModel.allServices.collectAsState()
    val stories by viewModel.activeStories.collectAsState()
    var selectedCircle by remember { mutableStateOf<CompanyCircle?>(null) }
    var storyCircle by remember { mutableStateOf<CompanyCircle?>(null) }
    val circles = remember(companies, allServices, stories) {
        companies.mapNotNull { company ->
            val owned = allServices.filter { it.ownerEmail == company.email }
            if (owned.isEmpty()) null
            else {
                val story = stories.filter { it.ownerEmail == company.email }
                    .maxByOrNull { it.timestamp }
                CompanyCircle(company, owned, owned.any { it.isOpen }, story)
            }
        }.sortedWith(
            compareByDescending<CompanyCircle> { it.story != null }
                .thenByDescending { it.openNow }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Mochilapp",
                    modifier = Modifier.padding(horizontal = 28.dp),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black)
                )
                Spacer(Modifier.height(24.dp))
                
                /* 
                // Botón de desarrollo oculto para MVP y Release
                if (com.mochilapp.mobile.BuildConfig.DEBUG) {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                        label = { Text("Dev: Subir Datos de Prueba") },
                        selected = false,
                        onClick = { 
                            scope.launch { 
                                viewModel.seedSampleData()
                                drawerState.close()
                            }
                        }
                    )
                }
                */

                NavigationDrawerItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    label = { Text(t("logout")) },
                    selected = false,
                    onClick = onLogout
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.logocolor),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Mochilapp",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = onAiClick) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assistant", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            },
            bottomBar = {
                PremiumBottomNavigation(
                    onHomeClick = { /* Already here */ },
                    onSearchClick = onSearchClick,
                    onMapClick = onMapClick,
                    onBookingsClick = onBookingsClick,
                    onProfileClick = onProfileClick
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // Círculos de empresas con "abierto ahora" (estilo stories)
                if (circles.isNotEmpty()) {
                    item {
                        BusinessCirclesRow(
                            circles = circles,
                            onCircleClick = { circle ->
                                if (circle.story != null) storyCircle = circle else selectedCircle = circle
                            }
                        )
                    }
                }

                // Flash Promos Section (Mochi-Alertas)
                if (promos.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFFFFD43B))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    t("flash_promos_title"),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(promos) { promo ->
                                    FlashPromoCard(
                                        promo = promo,
                                        onClick = { 
                                            if (it.serviceId.isNotEmpty()) {
                                                viewModel.applyPromo(it)
                                                onServiceClick(it.serviceId)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Editorial Section: Recommended
                if (recommended.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                t("recommended"),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            EditorialCarousel(recommended, onServiceClick)
                        }
                    }
                }

                // Search Filters (New design)
                item {
                    PremiumFilterSection(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        selectedType = selectedType,
                        onTypeSelect = { viewModel.selectType(it) },
                        priceRange = currentPriceRange,
                        onPriceRangeSelect = { viewModel.updatePriceRange(it) },
                        guests = currentGuests,
                        onGuestsChange = { viewModel.updateGuests(it) },
                        selectedDate = selectedDate,
                        onDateSelect = { viewModel.updateDate(it) }
                    )
                }

                // Feed Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            t("discover"),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        if (searchQuery.isNotEmpty() || selectedType != null || currentPriceRange != com.mochilapp.mobile.ui.viewmodels.PriceRange.ALL || currentGuests > 1 || selectedDate != null) {
                            TextButton(onClick = { viewModel.clearFilters() }) {
                                Text(t("clear_filters"), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                if (services.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (searchQuery.isNotEmpty() || selectedType != null || selectedDate != null ||
                                    currentGuests > 1 || currentPriceRange != com.mochilapp.mobile.ui.viewmodels.PriceRange.ALL
                                ) "No encontramos experiencias con esos filtros."
                                else "Aún no hay experiencias disponibles. Vuelve pronto.",
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(32.dp)
                            )
                        }
                    }
                } else {
                    items(services) { service ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            PremiumServiceCard(service = service, onClick = { onServiceClick(service.id) })
                        }
                    }
                }
            }
        }

        if (isSeeding) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("Generando experiencias...", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        selectedCircle?.let { circle ->
            CompanyCircleSheet(
                circle = circle,
                onServiceClick = { id -> selectedCircle = null; onServiceClick(id) },
                onDismiss = { selectedCircle = null }
            )
        }

        storyCircle?.let { circle ->
            StoryViewer(
                circle = circle,
                onOpenProfile = { storyCircle = null; selectedCircle = circle },
                onDismiss = { storyCircle = null }
            )
        }
    }
}

// Fila de círculos de empresas estilo "stories": anillo verde = abierto ahora.
data class CompanyCircle(
    val company: UserFirestore,
    val services: List<ServiceFirestore>,
    val openNow: Boolean,
    val story: StoryFirestore? = null
)

@Composable
fun BusinessCirclesRow(circles: List<CompanyCircle>, onCircleClick: (CompanyCircle) -> Unit) {
    LazyRow(
        modifier = Modifier.padding(top = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(circles) { circle ->
            CompanyCircleItem(circle = circle, onClick = { onCircleClick(circle) })
        }
    }
}

@Composable
fun CompanyCircleItem(circle: CompanyCircle, onClick: () -> Unit) {
    val name = circle.company.businessName.ifBlank { circle.company.name }
    // Anillo degradado de marca cuando hay historia nueva (estilo stories);
    // si no, verde si está abierto y gris si está cerrado.
    val ringBrush = when {
        circle.story != null -> Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
        )
        circle.openNow -> SolidColor(Color(0xFF2ECC71))
        else -> SolidColor(Color(0xFFCED4DA))
    }
    val image = circle.company.profileImageUrl.ifBlank {
        circle.services.firstOrNull { it.imageUrl.isNotBlank() }?.imageUrl ?: ""
    }
    Column(
        modifier = Modifier
            .width(68.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .border(if (circle.story != null) 2.5.dp else 2.dp, ringBrush, CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE9ECEF)),
                contentAlignment = Alignment.Center
            ) {
                if (image.isNotBlank()) {
                    AsyncImage(
                        model = image,
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        name.take(1).uppercase(),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 22.sp
                    )
                }
            }
            if (circle.openNow) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2ECC71))
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            name,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyCircleSheet(
    circle: CompanyCircle,
    onServiceClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val name = circle.company.businessName.ifBlank { circle.company.name }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.weight(1f))
                Surface(
                    color = if (circle.openNow) Color(0xFFD4EFDF) else Color(0xFFF1F3F5),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (circle.openNow) "Abierto ahora" else "Cerrado",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (circle.openNow) Color(0xFF1D8348) else Color.Gray
                    )
                }
            }

            if (circle.company.businessDescription.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(circle.company.businessDescription, fontSize = 13.sp, color = Color.Gray)
            }

            Spacer(Modifier.height(16.dp))
            ContactCompanyButton(
                whatsapp = circle.company.whatsapp,
                phone = circle.company.phone,
                message = "¡Hola $name! Te encontré en Mochilapp 🎒 y tengo una pregunta."
            )

            Spacer(Modifier.height(20.dp))
            Text("Sus experiencias", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                circle.services.take(8).forEach { service ->
                    SavedAdventureCard(
                        service = service,
                        brandColor = MaterialTheme.colorScheme.primary,
                        onClick = { onServiceClick(service.id) }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun StoryViewer(
    circle: CompanyCircle,
    onOpenProfile: () -> Unit,
    onDismiss: () -> Unit
) {
    val story = circle.story ?: return
    val name = circle.company.businessName.ifBlank { circle.company.name }
    var started by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 8000, easing = LinearEasing),
        label = "storyProgress"
    )
    // Auto-cierre a los 8s, sensación efímera de historia.
    LaunchedEffect(story.id) {
        started = true
        delay(8000)
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss)
        ) {
            AsyncImage(
                model = story.imageUrl,
                contentDescription = story.caption,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (circle.company.profileImageUrl.isNotBlank()) {
                            AsyncImage(
                                model = circle.company.profileImageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                    .navigationBarsPadding()
                    .padding(20.dp)
            ) {
                if (story.caption.isNotBlank()) {
                    Text(story.caption, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(16.dp))
                }
                Button(
                    onClick = onOpenProfile,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.Storefront, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Ver $name", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StoryUploadButton(viewModel: CompanyViewModel, companyName: String) {
    val context = LocalContext.current
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pickedUri = uri
            caption = ""
            showDialog = true
        }
    }

    OutlinedButton(
        onClick = { launcher.launch("image/*") },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
    ) {
        Icon(Icons.Default.AddAPhoto, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Subir Historia (24h)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }

    if (showDialog) {
        val uri = pickedUri
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Nueva historia") },
            text = {
                Column {
                    Text("Tu historia será visible 24 horas para los viajeros.", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    if (uri != null) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text("Texto (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    uri?.let { viewModel.addStory(it, caption, companyName) }
                    showDialog = false
                    Toast.makeText(context, "Historia publicada 🎒", Toast.LENGTH_SHORT).show()
                }) { Text("Publicar") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun FlashPromoCard(
    promo: com.mochilapp.mobile.data.PromoFirestore,
    onClick: (com.mochilapp.mobile.data.PromoFirestore) -> Unit
) {
    Card(
        onClick = { onClick(promo) },
        modifier = Modifier
            .width(280.dp)
            .height(130.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F3F5))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFFFFD43B).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (promo.discountPercent > 0) "AHORRA ${promo.discountPercent}%" else "OFERTA",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color(0xFFD68910),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFFFFD43B), modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = promo.content,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 2,
                lineHeight = 18.sp
            )
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = promo.companyName,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Ver oferta",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EditorialCarousel(
    recommended: List<ServiceFirestore>,
    onServiceClick: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(recommended) { service ->
            Card(
                onClick = { onServiceClick(service.id) },
                modifier = Modifier
                    .width(300.dp)
                    .height(180.dp),
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box {
                    if (service.imageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = service.imageUrl,
                            contentDescription = service.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Modern Gradient Background Fallback
                        Box(modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            )
                        ))
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp)
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                t("editorial_tag"),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = service.name,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumFilterSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedType: String?,
    onTypeSelect: (String?) -> Unit,
    priceRange: com.mochilapp.mobile.ui.viewmodels.PriceRange,
    onPriceRangeSelect: (com.mochilapp.mobile.ui.viewmodels.PriceRange) -> Unit,
    guests: Int,
    onGuestsChange: (Int) -> Unit,
    selectedDate: String?,
    onDateSelect: (String?) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        // Top Search Bar
        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(t("search_placeholder"), color = Color.Gray, fontSize = 14.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF1F3F5),
                unfocusedContainerColor = Color(0xFFF1F3F5),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Secondary Filters (Dates, Guests, Price)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            var showPriceMenu by remember { mutableStateOf(false) }
            var showGuestsMenu by remember { mutableStateOf(false) }
            var showDateMenu by remember { mutableStateOf(false) }

            Box {
                DropdownFilterBadge(
                    icon = Icons.Default.CalendarToday, 
                    text = selectedDate ?: t("filter_dates"),
                    onClick = { showDateMenu = true }
                )
                DropdownMenu(expanded = showDateMenu, onDismissRequest = { showDateMenu = false }) {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    val calendar = java.util.Calendar.getInstance()
                    
                    DropdownMenuItem(
                        text = { Text("Hoy") },
                        onClick = { 
                            onDateSelect(sdf.format(calendar.time))
                            showDateMenu = false 
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Mañana") },
                        onClick = { 
                            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
                            onDateSelect(sdf.format(calendar.time))
                            showDateMenu = false 
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Cualquier fecha") },
                        onClick = { 
                            onDateSelect(null)
                            showDateMenu = false 
                        }
                    )
                }
            }
            
            Box {
                DropdownFilterBadge(
                    icon = Icons.Default.People, 
                    text = if (guests > 1) "$guests ${t("filter_guests")}" else t("filter_guests"),
                    onClick = { showGuestsMenu = true }
                )
                DropdownMenu(expanded = showGuestsMenu, onDismissRequest = { showGuestsMenu = false }) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$guests+ ${t("people")}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (guests > 1) onGuestsChange(guests - 1) },
                                enabled = guests > 1
                            ) {
                                Icon(
                                    Icons.Default.RemoveCircle,
                                    contentDescription = "Menos personas",
                                    tint = if (guests > 1) MaterialTheme.colorScheme.primary else Color.LightGray
                                )
                            }
                            Text(
                                "$guests",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            IconButton(onClick = { onGuestsChange(guests + 1) }) {
                                Icon(
                                    Icons.Default.AddCircle,
                                    contentDescription = "Más personas",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Box {
                DropdownFilterBadge(
                    icon = Icons.Default.AttachMoney, 
                    text = when(priceRange) {
                        com.mochilapp.mobile.ui.viewmodels.PriceRange.ALL -> t("filter_price")
                        com.mochilapp.mobile.ui.viewmodels.PriceRange.ECONOMY -> "< $500"
                        com.mochilapp.mobile.ui.viewmodels.PriceRange.MEDIUM -> "$500-$1500"
                        com.mochilapp.mobile.ui.viewmodels.PriceRange.PREMIUM -> "> $1500"
                    },
                    onClick = { showPriceMenu = true }
                )
                DropdownMenu(expanded = showPriceMenu, onDismissRequest = { showPriceMenu = false }) {
                    com.mochilapp.mobile.ui.viewmodels.PriceRange.entries.forEach { range ->
                        DropdownMenuItem(
                            text = { 
                                Text(when(range) {
                                    com.mochilapp.mobile.ui.viewmodels.PriceRange.ALL -> t("all")
                                    com.mochilapp.mobile.ui.viewmodels.PriceRange.ECONOMY -> "Económico (< 500)"
                                    com.mochilapp.mobile.ui.viewmodels.PriceRange.MEDIUM -> "Medio (500-1500)"
                                    com.mochilapp.mobile.ui.viewmodels.PriceRange.PREMIUM -> "Premium (> 1500)"
                                })
                            },
                            onClick = { 
                                onPriceRangeSelect(range)
                                showPriceMenu = false 
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))

        // Category Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PremiumFilterChip(
                    selected = selectedType == null,
                    onClick = { onTypeSelect(null) },
                    label = t("all"),
                    icon = null
                )
            }
            items(CompanyType.entries.filter { it != CompanyType.NONE }) { type ->
                val icon = when(type) {
                    CompanyType.HOTEL -> Icons.Default.Bed
                    CompanyType.BOAT_TOUR -> Icons.Default.DirectionsBoat
                    CompanyType.RESTAURANT -> Icons.Default.Restaurant
                    else -> Icons.Default.Explore
                }
                PremiumFilterChip(
                    selected = selectedType == type.name,
                    onClick = { onTypeSelect(type.name) },
                    label = com.mochilapp.mobile.ui.theme.serviceTypeLabel(type.name),
                    icon = icon
                )
            }
        }
    }
}

@Composable
fun DropdownFilterBadge(icon: ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE9ECEF)),
        modifier = Modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
            Spacer(Modifier.width(8.dp))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
        }
    }
}

@Composable
fun PremiumBottomNavigation(
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onMapClick: () -> Unit,
    onBookingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    selectedItem: Int = 0
) {
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(96.dp)
    ) {
        // Dock flotante
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockNavItem(Icons.Default.Home, t("nav_home"), selectedItem == 0, accent, onHomeClick)
                DockNavItem(Icons.Default.Search, t("nav_search"), selectedItem == 1, accent, onSearchClick)

                Spacer(modifier = Modifier.width(56.dp))

                DockNavItem(Icons.Default.ConfirmationNumber, t("nav_bookings"), selectedItem == 2, accent, onBookingsClick)
                DockNavItem(Icons.Default.Person, t("nav_profile"), selectedItem == 3, accent, onProfileClick)
            }
        }

        // FAB del mapa con degradado azul → verde (marca Mochilapp)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(60.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                    )
                )
                .clickable(onClick = onMapClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Map, contentDescription = "Map", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

// Ítem del dock: solo icono; el activo se resalta con una píldora de color
@Composable
fun DockNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) accent else Color(0xFFADB5BD),
        label = "dockTint"
    )
    val background by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.12f) else Color.Transparent,
        label = "dockBg"
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = background
    ) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun PremiumFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector?
) {
    val containerColor = if (selected) Color.Black else Color(0xFFF1F3F5)
    val contentColor = if (selected) Color.White else Color.Black

    Surface(
        onClick = onClick,
        modifier = Modifier.height(40.dp),
        shape = RoundedCornerShape(20.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = contentColor)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = contentColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumServiceCard(
    service: ServiceFirestore, 
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Image with AsyncImage (Coil)
            if (service.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = service.imageUrl,
                    contentDescription = service.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.LightGray, Color.DarkGray)))
                )
            }
            
            // Info Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            service.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                // Privado: la tarifa base es "desde" (badge en la tarjeta)
                                if (service.esPrivado) "Desde ${formatMxn(service.price)}"
                                else formatMxn(service.price),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (service.esPrivado) {
                            Surface(color = Color(0xFF1D8348), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    "PRIVADO",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        if (service.reviewCount > 0) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD43B), modifier = Modifier.size(18.dp))
                            Text(
                                " ${String.format(java.util.Locale.US, "%.1f", service.rating)} ",
                                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black
                            )
                            Text("(${service.reviewCount} reseñas)", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        } else {
                            Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    "NUEVO",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(displayLocation(service.location), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyDashboard(
    viewModel: CompanyViewModel,
    authViewModel: AuthViewModel,
    onAddService: () -> Unit,
    onAiClick: () -> Unit,
    onLogout: () -> Unit,
    onProfileClick: () -> Unit,
    onBookingClick: (String) -> Unit,
    onBoatModuleClick: () -> Unit = {},
    onLodgingModuleClick: () -> Unit = {},
    onRentalModuleClick: () -> Unit = {},
    onRestaurantModuleClick: () -> Unit = {},
    onTourAgencyModuleClick: () -> Unit = {},
    onTransportModuleClick: () -> Unit = {},
    onCommunityClick: () -> Unit = {}
) {
    val services by viewModel.myServices.collectAsState()
    val bookings by viewModel.myBookings.collectAsState()
    val revenue by viewModel.totalRevenue.collectAsState()
    val monthlyRevenue by viewModel.monthlyRevenue.collectAsState()
    val pendingCount by viewModel.pendingBookingsCount.collectAsState()
    val paidCount by viewModel.paidBookingsCount.collectAsState()
    val cancelledCount by viewModel.cancelledBookingsCount.collectAsState()
    val todayCount by viewModel.todayBookingsCount.collectAsState()
    val activePromosCount by viewModel.activePromosCount.collectAsState()
    val recentBookings by viewModel.recentBookings.collectAsState()
    val filteredBookings by viewModel.filteredBookings.collectAsState()
    val bookingStatusFilter by viewModel.bookingStatusFilter.collectAsState()
    val bookingSearchQuery by viewModel.bookingSearchQuery.collectAsState()
    val userProfile by authViewModel.userProfile.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    
    val companyTeal = MaterialTheme.colorScheme.tertiary
    val companyLightTeal = MaterialTheme.colorScheme.tertiaryContainer
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.logocolor),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Mochilapp", 
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                color = companyTeal
                            )
                        ) 
                    }
                },
            )
        },
        bottomBar = {
            CompanyBottomNavigation(
                selectedItem = selectedTab,
                onTabSelected = { tab ->
                    // Comunidad abre el feed social real en lugar del placeholder
                    if (tab == 3) onCommunityClick() else viewModel.selectTab(tab)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> { // Panel (Home)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Notification Banner (solo si hay reservas pendientes reales)
                        if (pendingCount > 0) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().clickable { viewModel.selectTab(2) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(t("company_pending_requests"), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                            Text(
                                                if (pendingCount == 1) "1 reserva esperando confirmación" else "$pendingCount reservas esperando confirmación",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Company Title Section
                        item {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        userProfile?.name ?: "Tu Empresa",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    val isVerified = userProfile?.businessVerified == true
                                    Surface(
                                        color = if (isVerified) Color(0xFF2ECC71).copy(alpha = 0.2f) else Color(0xFFFDEBD0),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                if (isVerified) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = if (isVerified) Color(0xFF27AE60) else Color(0xFFD68910)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                if (isVerified) "VERIFICADO" else "EN REVISIÓN",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isVerified) Color(0xFF27AE60) else Color(0xFFD68910)
                                            )
                                        }
                                    }
                                }
                                Text(t("company_dashboard_title"), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 14.sp)
                            }
                        }

                        // Action Button
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { viewModel.startNewService(); onAddService() },
                                    colors = ButtonDefaults.buttonColors(containerColor = companyTeal),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.height(48.dp).weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(t("company_new_service"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                var showPromoDialog by remember { mutableStateOf(false) }
                                Button(
                                    onClick = { showPromoDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD43B), contentColor = Color.Black),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.height(48.dp).weight(1f)
                                ) {
                                    Icon(Icons.Default.FlashOn, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(t("company_flash_promo"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                if (showPromoDialog) {
                                    var promoText by remember { mutableStateOf("") }
                                    var discountText by remember { mutableStateOf("50%") }
                                    var promoServiceId by remember { mutableStateOf("") }
                                    var serviceMenuExpanded by remember { mutableStateOf(false) }
                                    val visibleServices = services.filter { it.isVisible }
                                    val selectedPromoService = visibleServices.find { it.id == promoServiceId }

                                    AlertDialog(
                                        onDismissRequest = { showPromoDialog = false },
                                        title = { Text(t("flash_promo_dialog_title")) },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Text(t("flash_promo_dialog_desc"), fontSize = 12.sp, color = Color.Gray)

                                                // Servicio al que se liga la oferta (obligatorio para
                                                // que el viajero pueda reservar al tocarla)
                                                Box {
                                                    OutlinedTextField(
                                                        value = selectedPromoService?.name ?: "",
                                                        onValueChange = {},
                                                        readOnly = true,
                                                        label = { Text("Servicio en oferta") },
                                                        placeholder = { Text("Selecciona un servicio") },
                                                        trailingIcon = {
                                                            IconButton(onClick = { serviceMenuExpanded = true }) {
                                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                                            }
                                                        },
                                                        modifier = Modifier.fillMaxWidth().clickable { serviceMenuExpanded = true }
                                                    )
                                                    DropdownMenu(
                                                        expanded = serviceMenuExpanded,
                                                        onDismissRequest = { serviceMenuExpanded = false }
                                                    ) {
                                                        visibleServices.forEach { service ->
                                                            DropdownMenuItem(
                                                                text = { Text(service.name) },
                                                                onClick = {
                                                                    promoServiceId = service.id
                                                                    serviceMenuExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                OutlinedTextField(
                                                    value = promoText,
                                                    onValueChange = { promoText = it },
                                                    label = { Text(t("flash_promo_msg_label")) }
                                                )
                                                OutlinedTextField(
                                                    value = discountText,
                                                    onValueChange = { discountText = it },
                                                    label = { Text(t("flash_promo_discount_label")) }
                                                )

                                                Text(
                                                    "La oferta estará vigente 24 horas y llegará a todos los viajeros.",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    viewModel.sendFlashPromo(promoText, discountText, userProfile?.name ?: "Empresa", promoServiceId)
                                                    showPromoDialog = false
                                                },
                                                enabled = promoText.isNotBlank() && promoServiceId.isNotEmpty()
                                            ) { Text(t("send")) }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showPromoDialog = false }) { Text(t("cancel")) }
                                        }
                                    )
                                }
                            }
                        }

                        // Avisos operativos a viajeros (retrasos, cambios, cierres)
                        item {
                            var showNoticesDialog by remember { mutableStateOf(false) }
                            val myNotices by viewModel.myNotices.collectAsState()
                            val activeNoticesCount = myNotices.count {
                                it.isActive && (it.expiresAt <= 0L || it.expiresAt > System.currentTimeMillis())
                            }
                            OutlinedButton(
                                onClick = { showNoticesDialog = true },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = companyTeal)
                            ) {
                                Icon(Icons.Default.Campaign, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (activeNoticesCount > 0) "Avisos a Viajeros ($activeNoticesCount activos)"
                                    else "Avisos a Viajeros",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            if (showNoticesDialog) {
                                CompanyNoticesDialog(
                                    services = services,
                                    notices = myNotices,
                                    companyName = userProfile?.name ?: "Empresa",
                                    onSend = { serviceId, serviceName, date, message, severity ->
                                        viewModel.sendNotice(
                                            serviceId = serviceId,
                                            serviceName = serviceName,
                                            companyName = userProfile?.name ?: "Empresa",
                                            date = date,
                                            message = message,
                                            severity = severity
                                        )
                                        showNoticesDialog = false
                                    },
                                    onDeactivate = { viewModel.deactivateNotice(it) },
                                    onDismiss = { showNoticesDialog = false }
                                )
                            }
                        }

                        // Subir historia efímera (24h) que verán los viajeros
                        item {
                            StoryUploadButton(
                                viewModel = viewModel,
                                companyName = userProfile?.name ?: "Empresa"
                            )
                        }

                        // Módulos por vertical (equivalente al moduleRegistry del panel web):
                        // una tarjeta por cada módulo cuyo tipo de servicio tenga la empresa
                        val moduleEntries = listOf(
                            CompanyModuleEntry(
                                title = "Control de Embarcaciones",
                                subtitle = "Mapa de asientos, ocupación y cupos en vivo",
                                icon = Icons.Default.DirectionsBoat,
                                accent = Color(0xFF22D3EE),
                                serviceTypes = setOf("BOAT_TOUR"),
                                onClick = onBoatModuleClick
                            ),
                            CompanyModuleEntry(
                                title = "Gestión de Hospedaje",
                                subtitle = "Habitaciones ocupadas y libres por día",
                                icon = Icons.Default.Hotel,
                                accent = Color(0xFF34D399),
                                serviceTypes = setOf("HOTEL", "HOSTEL"),
                                onClick = onLodgingModuleClick
                            ),
                            CompanyModuleEntry(
                                title = "Calendario de Rentas",
                                subtitle = "Disponibilidad mensual y rentas de propiedades",
                                icon = Icons.Default.CalendarMonth,
                                accent = Color(0xFFA78BFA),
                                serviceTypes = setOf("PROPERTY_RENTAL"),
                                onClick = onRentalModuleClick
                            ),
                            CompanyModuleEntry(
                                title = "Gestión Gastronómica",
                                subtitle = "Menú digital, estado del local y reservas de mesa",
                                icon = Icons.Default.Restaurant,
                                accent = Color(0xFFFBBF24),
                                serviceTypes = setOf("RESTAURANT", "FOOD_STAND"),
                                onClick = onRestaurantModuleClick
                            ),
                            CompanyModuleEntry(
                                title = "Control de Salidas",
                                subtitle = "Tablero de salidas y ocupación por horario",
                                icon = Icons.Default.Explore,
                                accent = Color(0xFFC4B5FD),
                                serviceTypes = setOf("TOUR_AGENCY"),
                                onClick = onTourAgencyModuleClick
                            ),
                            CompanyModuleEntry(
                                title = "Control de Rutas",
                                subtitle = "Corridas, asientos y pasajeros por horario",
                                icon = Icons.Default.DirectionsBus,
                                accent = Color(0xFF60A5FA),
                                serviceTypes = setOf("TRANSPORT"),
                                onClick = onTransportModuleClick
                            )
                        ).filter { entry ->
                            // Mostrar el módulo si hay servicios del giro O si la
                            // empresa se registró con ese giro (aunque aún no publique)
                            services.any { it.type in entry.serviceTypes } ||
                                (userProfile?.companyType ?: "") in entry.serviceTypes
                        }

                        items(moduleEntries) { entry ->
                            Surface(
                                onClick = entry.onClick,
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = entry.accent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(
                                            entry.icon,
                                            contentDescription = null,
                                            tint = entry.accent,
                                            modifier = Modifier.padding(12.dp).size(28.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                        Text(
                                            entry.subtitle,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = entry.accent)
                                }
                            }
                        }

                        // Stats Grid (KPIs equivalentes al panel web)
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                CompanyStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.CalendarMonth,
                                    label = t("company_bookings_today"),
                                    value = todayCount.toString(),
                                    onClick = {
                                        viewModel.setBookingStatusFilter("ALL")
                                        viewModel.selectTab(2)
                                    }
                                )
                                CompanyStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.AssignmentLate,
                                    label = t("company_pending_requests"),
                                    value = pendingCount.toString(),
                                    actionLabel = if (pendingCount > 0) "Revisar" else null,
                                    onActionClick = {
                                        viewModel.setBookingStatusFilter("PENDING")
                                        viewModel.selectTab(2)
                                    },
                                    onClick = {
                                        viewModel.setBookingStatusFilter("PENDING")
                                        viewModel.selectTab(2)
                                    }
                                )
                            }
                        }
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                CompanyStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.CreditCard,
                                    label = "Ingresos del Mes",
                                    value = formatMxn(monthlyRevenue),
                                    onClick = {
                                        viewModel.setBookingStatusFilter("PAID")
                                        viewModel.selectTab(2)
                                    }
                                )
                                CompanyStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.CheckCircle,
                                    label = "Reservas Pagadas",
                                    value = paidCount.toString(),
                                    onClick = {
                                        viewModel.setBookingStatusFilter("PAID")
                                        viewModel.selectTab(2)
                                    }
                                )
                            }
                        }
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                CompanyStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Storefront,
                                    label = "Servicios Activos",
                                    value = services.count { it.isVisible }.toString(),
                                    onClick = { viewModel.selectTab(1) }
                                )
                                CompanyStatCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.FlashOn,
                                    label = "Promos Activas",
                                    value = activePromosCount.toString()
                                )
                            }
                        }

                        // Progress Card
                        item {
                            // Disponibilidad separada por modalidad (spec §9): asientos
                            // de colectivos y salidas de privados NUNCA se suman.
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    // Solo cuentan reservas de hoy que retienen cupo (los
                                    // PENDING abandonados liberan al expirar su hold)
                                    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                                    val activasHoy = bookings.filter { it.date == today && it.holdsSeats() }
                                    val visibles = services.filter { it.isVisible }
                                    val colectivos = visibles.filter { !it.esPrivado }
                                    val privados = visibles.filter { it.esPrivado }

                                    if (colectivos.isNotEmpty() || privados.isEmpty()) {
                                        val totalCapacity = colectivos.sumOf { it.capacity }
                                        val usedSlots = activasHoy
                                            .filter { b -> colectivos.any { it.id == b.serviceId } }
                                            .sumOf { it.slots }
                                        val freeSlots = (totalCapacity - usedSlots).coerceAtLeast(0)
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.AirlineSeatReclineExtra, contentDescription = null, tint = companyTeal, modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(t("company_free_slots"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                            }
                                            Spacer(Modifier.height(12.dp))
                                            Row(verticalAlignment = Alignment.Bottom) {
                                                Text(
                                                    text = if (totalCapacity > 0) freeSlots.toString() else "--",
                                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black)
                                                )
                                                Spacer(Modifier.weight(1f))
                                                LinearProgressIndicator(
                                                    progress = { if (totalCapacity > 0) usedSlots.toFloat() / totalCapacity.toFloat() else 0f },
                                                    modifier = Modifier.width(150.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                                    color = companyTeal,
                                                    trackColor = companyLightTeal
                                                )
                                            }
                                        }
                                    }

                                    if (privados.isNotEmpty()) {
                                        // Privados: la unidad es la SALIDA (horario), no el asiento
                                        val salidasTotales = privados.sumOf { maxOf(1, it.departureTimes.size) }
                                        val salidasTomadas = privados.sumOf { svc ->
                                            val activas = activasHoy.filter { it.serviceId == svc.id }
                                            if (svc.departureTimes.isEmpty()) {
                                                if (activas.isNotEmpty()) 1 else 0
                                            } else {
                                                svc.departureTimes.count { t -> activas.any { it.departureTime == t } }
                                            }
                                        }
                                        val salidasLibres = (salidasTotales - salidasTomadas).coerceAtLeast(0)
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.DirectionsBoat, contentDescription = null, tint = companyTeal, modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(t("company_free_departures"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                            }
                                            Spacer(Modifier.height(12.dp))
                                            Row(verticalAlignment = Alignment.Bottom) {
                                                Text(
                                                    text = "$salidasLibres de $salidasTotales",
                                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black)
                                                )
                                                Spacer(Modifier.weight(1f))
                                                LinearProgressIndicator(
                                                    progress = { if (salidasTotales > 0) salidasTomadas.toFloat() / salidasTotales.toFloat() else 0f },
                                                    modifier = Modifier.width(150.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
                                                    color = companyTeal,
                                                    trackColor = companyLightTeal
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Últimas Reservas
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Últimas Reservas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (bookings.isNotEmpty()) {
                                        TextButton(onClick = { viewModel.selectTab(2) }) {
                                            Text("Ver todas", color = companyTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                if (recentBookings.isEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                                            Spacer(Modifier.height(8.dp))
                                            Text("Sin reservas registradas aún.", fontSize = 12.sp, color = Color.Gray)
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        recentBookings.forEach { booking ->
                                            RealBookingListItem(booking, onClick = { onBookingClick(booking.id) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> { // Servicios
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(t("nav_services"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Button(onClick = { viewModel.startNewService(); onAddService() }, shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Text(t("company_new_service"), fontSize = 12.sp)
                                }
                            }
                        }
                        if (services.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillParentMaxHeight(0.7f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(t("empty_services_company"), color = Color.Gray)
                                }
                            }
                        } else {
                            items(services) { service ->
                                CompanyServiceCard(
                                    service = service,
                                    onEdit = {
                                        viewModel.startEditingService(service)
                                        onAddService()
                                    },
                                    onToggleVisibility = {
                                        viewModel.setServiceVisibility(service.id, !service.isVisible)
                                    }
                                )
                            }
                        }
                    }
                }
                2 -> { // Reservas
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            var showVerifier by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${t("nav_bookings")} (${bookings.size})",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Button(
                                    onClick = { showVerifier = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = companyTeal)
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Verificar Ticket", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (showVerifier) {
                                TicketVerifierDialog(
                                    bookings = bookings,
                                    onCheckIn = { viewModel.checkInBooking(it) },
                                    onDismiss = { showVerifier = false }
                                )
                            }
                        }
                        if (bookings.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillParentMaxHeight(0.7f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(t("company_no_bookings"), color = Color.Gray)
                                }
                            }
                        } else {
                            // Resumen por estado
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BookingStatusSummaryCard(
                                        modifier = Modifier.weight(1f),
                                        label = "Pendientes",
                                        count = pendingCount,
                                        icon = Icons.Default.Schedule,
                                        tint = Color(0xFFD68910),
                                        background = Color(0xFFFDEBD0)
                                    )
                                    BookingStatusSummaryCard(
                                        modifier = Modifier.weight(1f),
                                        label = "Pagadas",
                                        count = paidCount,
                                        icon = Icons.Default.CheckCircle,
                                        tint = Color(0xFF1D8348),
                                        background = Color(0xFFD4EFDF)
                                    )
                                    BookingStatusSummaryCard(
                                        modifier = Modifier.weight(1f),
                                        label = "Canceladas",
                                        count = cancelledCount,
                                        icon = Icons.Default.Cancel,
                                        tint = Color(0xFFC0392B),
                                        background = Color(0xFFFADBD8)
                                    )
                                }
                            }

                            // Búsqueda
                            item {
                                TextField(
                                    value = bookingSearchQuery,
                                    onValueChange = { viewModel.setBookingSearchQuery(it) },
                                    placeholder = { Text("Buscar viajero o servicio...", color = Color.Gray, fontSize = 13.sp) },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    singleLine = true
                                )
                            }

                            // Filtros por estado
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(
                                        listOf(
                                            "ALL" to "Todas",
                                            "PENDING" to "Pendientes",
                                            "PAID" to "Pagadas",
                                            "CANCELLED" to "Canceladas"
                                        )
                                    ) { (status, label) ->
                                        FilterChip(
                                            selected = bookingStatusFilter == status,
                                            onClick = { viewModel.setBookingStatusFilter(status) },
                                            label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }

                            if (filteredBookings.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                        Text("Ninguna reserva coincide con la búsqueda.", color = Color.Gray, fontSize = 13.sp)
                                    }
                                }
                            } else {
                                items(filteredBookings) { booking ->
                                    RealBookingListItem(booking, onClick = { onBookingClick(booking.id) })
                                }
                            }
                        }
                    }
                }
                3 -> { // Comunidad
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                            Spacer(Modifier.height(16.dp))
                            Text(t("empty_community_company"), color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    }
                }
                4 -> { // Perfil
                    CompanyProfileTab(
                        authViewModel = authViewModel,
                        onLogout = onLogout
                    )
                }
            }
        }
    }
}

// Equivalente nativo del moduleRegistry del panel web: cada módulo declara
// qué tipos de servicio lo habilitan
data class CompanyModuleEntry(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val serviceTypes: Set<String>,
    val onClick: () -> Unit
)

@Composable
fun BookingStatusSummaryCard(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    icon: ImageVector,
    tint: Color,
    background: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = background
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(8.dp))
            Text(count.toString(), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = tint)
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tint.copy(alpha = 0.8f), maxLines = 1)
        }
    }
}

// Verificador de tickets: la empresa teclea el código de 6 dígitos del viajero,
// se valida contra sus reservas y permite confirmar la llegada (check-in).
// Acepta también códigos legado con prefijo MOCHI-.
@Composable
fun TicketVerifierDialog(
    bookings: List<com.mochilapp.mobile.data.BookingFirestore>,
    onCheckIn: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }
    var checkedIn by remember { mutableStateOf(false) }

    val normalizedCode = codeInput.trim().uppercase().removePrefix("MOCHI-")
    val found = if (searched) bookings.find {
        it.confirmationCode.isNotBlank() &&
            it.confirmationCode.uppercase().removePrefix("MOCHI-") == normalizedCode
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text("Verificar Ticket", fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = {
                        codeInput = it.uppercase()
                        searched = false
                        checkedIn = false
                    },
                    label = { Text("Código del viajero (6 dígitos)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                if (searched && !checkedIn) {
                    if (found == null) {
                        Surface(color = Color(0xFFFADBD8), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFC0392B), modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Código no encontrado en tus reservas.", fontSize = 13.sp, color = Color(0xFFC0392B), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        val (statusColor, statusBg, statusLabel) = when (found.status) {
                            "PAID" -> Triple(Color(0xFF1D8348), Color(0xFFD4EFDF), "PAGADA • Lista para check-in")
                            "PENDING" -> Triple(Color(0xFFD68910), Color(0xFFFDEBD0), "PENDIENTE DE PAGO")
                            "CHECKED_IN" -> Triple(Color(0xFF2471A3), Color(0xFFD4E6F1), "YA HIZO CHECK-IN")
                            "COMPLETED" -> Triple(Color(0xFF1D8348), Color(0xFFD4EFDF), "SERVICIO COMPLETADO")
                            "CANCELLED" -> Triple(Color(0xFFC0392B), Color(0xFFFADBD8), "CANCELADA")
                            else -> Triple(Color.Gray, Color(0xFFF1F3F5), found.status)
                        }
                        Surface(color = statusBg, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(statusLabel, fontSize = 11.sp, fontWeight = FontWeight.Black, color = statusColor)
                                Spacer(Modifier.height(6.dp))
                                Text(found.travelerName.ifEmpty { found.travelerEmail }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "${found.serviceName} • ${found.date}" +
                                        (if (found.departureTime.isNotEmpty()) " • ${found.departureTime}" else ""),
                                    fontSize = 12.sp, color = Color.DarkGray
                                )
                                Text("${found.slots} persona(s) • ${formatMxn(found.totalPrice)}", fontSize = 12.sp, color = Color.DarkGray)
                            }
                        }
                    }
                }

                if (checkedIn) {
                    Surface(color = Color(0xFFD4EFDF), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1D8348), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("¡Check-in realizado! Bienvenido a bordo. 🎒", fontSize = 13.sp, color = Color(0xFF1D8348), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!checkedIn && searched && found != null && found.status == "PAID") {
                Button(
                    onClick = {
                        onCheckIn(found.id)
                        checkedIn = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2ECC71))
                ) {
                    Icon(Icons.Default.HowToReg, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Confirmar llegada", fontWeight = FontWeight.Bold)
                }
            } else if (!checkedIn) {
                Button(
                    onClick = { searched = true },
                    enabled = normalizedCode.isNotBlank()
                ) { Text("Verificar", fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (checkedIn) "Cerrar" else "Cancelar") }
        }
    )
}

// Tarjeta de servicio para el panel de empresa, con acciones de gestión
@Composable
fun CompanyServiceCard(
    service: ServiceFirestore,
    onEdit: () -> Unit,
    onToggleVisibility: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box {
                PremiumServiceCard(service = service, onClick = onEdit)
                if (!service.isVisible) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("OCULTO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                TextButton(onClick = onToggleVisibility) {
                    Icon(
                        if (service.isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (service.isVisible) "Ocultar" else "Mostrar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CompanyStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    trend: String? = null,
    actionLabel: String? = null,
    onActionClick: () -> Unit = {},
    // Toda la tarjeta navega a su pantalla (estilo KPI clicable de panel web)
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            disabledContentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black))
                Spacer(Modifier.weight(1f))
                if (trend != null) {
                    Text(trend, color = Color(0xFF27AE60), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (actionLabel != null) {
                    Text(actionLabel, color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onActionClick))
                }
            }
        }
    }
}

@Composable
fun RealBookingListItem(booking: com.mochilapp.mobile.data.BookingFirestore, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(48.dp), 
                shape = RoundedCornerShape(12.dp), 
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(booking.travelerName.take(1).uppercase(), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(booking.travelerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${booking.slots} personas • ${booking.date}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                if (booking.departureTime.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Gray)
                        Spacer(Modifier.width(4.dp))
                        Text(booking.departureTime, fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatMxn(booking.totalPrice), fontWeight = FontWeight.Black, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                val (bg, fg, statusIcon) = when (booking.status) {
                    "PENDING" -> Triple(Color(0xFFFDEBD0), Color(0xFFD68910), Icons.Default.Schedule)
                    "CANCELLED" -> Triple(Color(0xFFFADBD8), Color(0xFFC0392B), Icons.Default.Close)
                    else -> Triple(Color(0xFFD4EFDF), Color(0xFF1D8348), Icons.Default.Check)
                }
                Surface(
                    color = bg,
                    shape = CircleShape,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = fg
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompanyBottomNavigation(
    selectedItem: Int,
    onTabSelected: (Int) -> Unit
) {
    val accent = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(68.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockNavItem(Icons.Default.GridView, t("nav_panel"), selectedItem == 0, accent) { onTabSelected(0) }
                DockNavItem(Icons.Default.Storefront, t("nav_services"), selectedItem == 1, accent) { onTabSelected(1) }
                DockNavItem(Icons.Default.CalendarMonth, t("nav_bookings"), selectedItem == 2, accent) { onTabSelected(2) }
                DockNavItem(Icons.Default.Groups, t("nav_community"), selectedItem == 3, accent) { onTabSelected(3) }
                DockNavItem(Icons.Default.PersonOutline, t("nav_profile"), selectedItem == 4, accent) { onTabSelected(4) }
            }
        }
    }
}
