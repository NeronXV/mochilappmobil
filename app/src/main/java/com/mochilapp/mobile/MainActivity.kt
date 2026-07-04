package com.mochilapp.mobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.google.firebase.messaging.FirebaseMessaging
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.mochilapp.mobile.navigation.Destination
import com.mochilapp.mobile.repository.FirebaseRepository
import com.mochilapp.mobile.ui.screens.*
import com.mochilapp.mobile.ui.theme.AppLanguage
import com.mochilapp.mobile.ui.theme.LocalAppLanguage
import com.mochilapp.mobile.ui.theme.MochilappTheme
import com.mochilapp.mobile.ui.viewmodels.*

// Preferencias locales de la app (por ahora solo el idioma elegido)
private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val LANGUAGE_KEY = stringPreferencesKey("app_language")

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        askNotificationPermission()

        // Suscribir a temas globales
        val messaging = FirebaseMessaging.getInstance()
        messaging.subscribeToTopic("promos")
        messaging.subscribeToTopic("feed_updates")

        // Idioma persistido: antes se perdía al cerrar la app y volvía a
        // español. Lectura síncrona única al arrancar (es un dato diminuto)
        // para no parpadear en otro idioma.
        val savedCode = runBlocking { settingsDataStore.data.first()[LANGUAGE_KEY] }
        val initialLanguage = AppLanguage.entries.firstOrNull { it.code == savedCode }
            ?: AppLanguage.ESPAÑOL

        setContent {
            // rememberSaveable: que el idioma sobreviva rotaciones y recreaciones
            var currentLanguage by androidx.compose.runtime.saveable.rememberSaveable {
                mutableStateOf(initialLanguage)
            }
            CompositionLocalProvider(LocalAppLanguage provides currentLanguage) {
                MochilappTheme {
                    MochilappApp(
                        currentLanguage = currentLanguage,
                        onLanguageChange = { newLang ->
                            currentLanguage = newLang
                            lifecycleScope.launch {
                                settingsDataStore.edit { it[LANGUAGE_KEY] = newLang.code }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun MochilappApp(currentLanguage: AppLanguage, onLanguageChange: (AppLanguage) -> Unit) {
    val repository = remember { FirebaseRepository() }

    // Debug Log
    LaunchedEffect(currentLanguage) {
        android.util.Log.d("LANG_DEBUG", "App recomposing with language: $currentLanguage")
    }

    val authViewModel: AuthViewModel = viewModel(
        factory = ViewModelFactory(repository = repository)
    )
    
    val userProfile by authViewModel.userProfile.collectAsState()
    val userEmail = userProfile?.email ?: ""
    val userName = userProfile?.name ?: ""
    val userUid = userProfile?.uid ?: ""

    val marketplaceViewModel: MarketplaceViewModel = viewModel(
        factory = ViewModelFactory(repository = repository)
    )

    // Registrar el token FCM en el perfil al iniciar sesión
    LaunchedEffect(userUid) {
        if (userUid.isNotEmpty()) {
            repository.registerFcmToken()
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    val backStack = rememberNavBackStack(Destination.Splash) as NavBackStack<Destination>

    Box(modifier = Modifier.fillMaxSize()) {
    NavDisplay(
        backStack = backStack,
        onBack = { 
            if (backStack.size > 1) {
                backStack.removeAt(backStack.size - 1)
            }
        },
        entryProvider = entryProvider {
            entry<Destination.Splash> {
                val isCheckingAuth by authViewModel.isCheckingAuth.collectAsState()
                SplashScreen(
                    onTimeout = {
                        if (!isCheckingAuth) {
                            val profile = authViewModel.userProfile.value
                            val nextDest = when {
                                profile == null -> Destination.LanguageSelection
                                profile.role == "TRAVELER" -> Destination.TravelerDashboard
                                else -> Destination.CompanyDashboard
                            }
                            backStack.clear()
                            backStack.add(nextDest)
                        }
                    }
                )
            }
            entry<Destination.LanguageSelection> {
                LanguageSelectionScreen(
                    onLanguageSelected = { code ->
                        val newLang = when(code) {
                            "en" -> AppLanguage.ENGLISH
                            "fr" -> AppLanguage.FRANÇAIS
                            else -> AppLanguage.ESPAÑOL
                        }
                        onLanguageChange(newLang)
                        backStack.add(Destination.Login)
                    }
                )
            }
            entry<Destination.Login> {
                LoginScreen(
                    authViewModel = authViewModel,
                    onLoginSuccess = { role ->
                        val nextDest = if (role == "TRAVELER") Destination.TravelerDashboard else Destination.CompanyDashboard
                        // Reemplazar el stack: Login/Idioma no deben quedar debajo
                        // del dashboard, o "atrás" regresaría al login con sesión activa
                        backStack.clear()
                        backStack.add(nextDest)
                    },
                    onNavigateToRegistration = { backStack.add(Destination.Registration) }
                )
            }
            entry<Destination.Registration> {
                RegistrationScreen(
                    authViewModel = authViewModel,
                    onRegistrationSuccess = { role ->
                        val nextDest = if (role == "TRAVELER") Destination.TravelerDashboard else Destination.CompanyDashboard
                        backStack.clear()
                        backStack.add(nextDest)
                    },
                    onNavigateToLogin = {
                        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                    }
                )
            }
            entry<Destination.TravelerDashboard> {
                TravelerDashboard(
                    viewModel = marketplaceViewModel,
                    onServiceClick = { id -> backStack.add(Destination.ServiceDetail(id)) },
                    onSocialFeedClick = { backStack.add(Destination.SocialFeed) },
                    onMapClick = { backStack.add(Destination.TourismMap) },
                    onAiClick = { backStack.add(Destination.AiAssistant) },
                    onSearchClick = { backStack.add(Destination.Search) },
                    onBookingsClick = { backStack.add(Destination.BookingHistory) },
                    onProfileClick = { backStack.add(Destination.UserProfile) },
                    onLogout = {
                        authViewModel.logout {
                            backStack.clear()
                            backStack.add(Destination.Login)
                        }
                    }
                )
            }
            entry<Destination.CompanyDashboard> {
                val companyViewModel: CompanyViewModel = viewModel(
                    key = "company_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail, userUid = userUid)
                )
                CompanyDashboard(
                    viewModel = companyViewModel,
                    authViewModel = authViewModel,
                    onAddService = { backStack.add(Destination.AddService) },
                    onAiClick = { backStack.add(Destination.AiAssistant) },
                    onLogout = {
                        authViewModel.logout {
                            backStack.clear()
                            backStack.add(Destination.Login)
                        }
                    },
                    onProfileClick = { backStack.add(Destination.UserProfile) },
                    onBookingClick = { id -> backStack.add(Destination.BookingDetailCompany(id)) },
                    onBoatModuleClick = { backStack.add(Destination.BoatTourModule) },
                    onLodgingModuleClick = { backStack.add(Destination.LodgingModule) },
                    onRentalModuleClick = { backStack.add(Destination.PropertyRentalModule) },
                    onRestaurantModuleClick = { backStack.add(Destination.RestaurantModule) },
                    onTourAgencyModuleClick = { backStack.add(Destination.TourAgencyModule) },
                    onTransportModuleClick = { backStack.add(Destination.TransportModule) },
                    onCommunityClick = { backStack.add(Destination.SocialFeed) }
                )
            }
            entry<Destination.BoatTourModule> {
                val companyViewModel: CompanyViewModel = viewModel(
                    key = "company_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail, userUid = userUid)
                )
                BoatTourModuleScreen(
                    viewModel = companyViewModel,
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.LodgingModule> {
                val companyViewModel: CompanyViewModel = viewModel(
                    key = "company_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail, userUid = userUid)
                )
                LodgingModuleScreen(
                    viewModel = companyViewModel,
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.PropertyRentalModule> {
                val companyViewModel: CompanyViewModel = viewModel(
                    key = "company_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail, userUid = userUid)
                )
                PropertyRentalModuleScreen(
                    viewModel = companyViewModel,
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.RestaurantModule> {
                val companyViewModel: CompanyViewModel = viewModel(
                    key = "company_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail, userUid = userUid)
                )
                RestaurantModuleScreen(
                    viewModel = companyViewModel,
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.TourAgencyModule> {
                val companyViewModel: CompanyViewModel = viewModel(
                    key = "company_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail, userUid = userUid)
                )
                TourAgencyModuleScreen(
                    viewModel = companyViewModel,
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.TransportModule> {
                val companyViewModel: CompanyViewModel = viewModel(
                    key = "company_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail, userUid = userUid)
                )
                TransportModuleScreen(
                    viewModel = companyViewModel,
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.BookingDetailCompany> { key ->
                val bookingViewModel: BookingViewModel = viewModel(
                    key = "booking_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail)
                )
                BookingDetailCompanyScreen(
                    bookingId = key.bookingId,
                    bookingViewModel = bookingViewModel,
                    userUid = userUid,
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.AddService> {
                val companyViewModel: CompanyViewModel = viewModel(
                    key = "company_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail, userUid = userUid)
                )
                AddServiceScreen(
                    viewModel = companyViewModel,
                    onMapClick = { backStack.add(Destination.MapPicker) },
                    onBack = { backStack.removeAt(backStack.size - 1) },
                    companyLocation = userProfile?.businessLocation ?: "",
                    companyLat = userProfile?.businessLat ?: 0.0,
                    companyLng = userProfile?.businessLng ?: 0.0,
                    companyHours = userProfile?.businessHours ?: ""
                )
            }
            entry<Destination.MapPicker> {
                val companyViewModel: CompanyViewModel = viewModel(
                    key = "company_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail, userUid = userUid)
                )
                MapPickerScreen(
                    viewModel = companyViewModel,
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.ServiceDetail> { key ->
                ServiceDetailScreen(
                    serviceId = key.serviceId,
                    viewModel = marketplaceViewModel,
                    onBookClick = { id -> backStack.add(Destination.BookingFlow(id)) },
                    onBack = { backStack.removeAt(backStack.size - 1) },
                    userName = userName,
                    isSaved = userProfile?.savedServices?.contains(key.serviceId) == true,
                    onToggleSave = { authViewModel.toggleSavedService(key.serviceId) },
                    onOrderClick = { id -> backStack.add(Destination.FoodOrder(id)) }
                )
            }
            entry<Destination.FoodOrder> { key ->
                val bookingViewModel: BookingViewModel = viewModel(
                    key = "booking_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail)
                )
                FoodOrderScreen(
                    serviceId = key.serviceId,
                    travelerName = userName,
                    marketplaceViewModel = marketplaceViewModel,
                    bookingViewModel = bookingViewModel,
                    onPaymentNavigate = { id -> backStack.add(Destination.Payment(id)) },
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.BookingFlow> { key ->
                val bookingViewModel: BookingViewModel = viewModel(
                    key = "booking_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail)
                )
                BookingFlowScreen(
                    serviceId = key.serviceId,
                    travelerName = userName,
                    marketplaceViewModel = marketplaceViewModel,
                    bookingViewModel = bookingViewModel,
                    onPaymentNavigate = { id -> backStack.add(Destination.Payment(id)) },
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.Payment> { key ->
                val bookingViewModel: BookingViewModel = viewModel(
                    key = "booking_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail)
                )
                PaymentScreen(
                    bookingId = key.bookingId,
                    viewModel = bookingViewModel,
                    onPaymentSuccess = {
                        // size > 1 evita vaciar el stack si el dashboard no está debajo
                        while (backStack.size > 1 &&
                            backStack.last() !is Destination.TravelerDashboard &&
                            backStack.last() !is Destination.CompanyDashboard
                        ) {
                            backStack.removeAt(backStack.size - 1)
                        }
                    },
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.SocialFeed> {
                val socialViewModel: SocialViewModel = viewModel(
                    key = "social_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail, userName = userName, userUid = userUid)
                )
                SocialFeedScreen(
                    viewModel = socialViewModel,
                    onCreatePost = { backStack.add(Destination.CreatePost) },
                    onBack = { backStack.removeAt(backStack.size - 1) },
                    onServiceClick = { id -> backStack.add(Destination.ServiceDetail(id)) }
                )
            }
            entry<Destination.CreatePost> {
                val socialViewModel: SocialViewModel = viewModel(
                    key = "social_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail, userName = userName, userUid = userUid)
                )
                CreatePostScreen(
                    viewModel = socialViewModel,
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.TourismMap> {
                TourismMapScreen(
                    marketplaceViewModel = marketplaceViewModel,
                    onServiceClick = { id -> backStack.add(Destination.ServiceDetail(id)) },
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.AiAssistant> {
                val aiViewModel: AiViewModel = viewModel(
                    factory = ViewModelFactory(repository = repository)
                )
                AiAssistantScreen(
                    viewModel = aiViewModel,
                    onBack = { backStack.removeAt(backStack.size - 1) },
                    onServiceClick = { id -> backStack.add(Destination.ServiceDetail(id)) }
                )
            }
            entry<Destination.Search> {
                SearchScreen(
                    viewModel = marketplaceViewModel,
                    onServiceClick = { id -> backStack.add(Destination.ServiceDetail(id)) },
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.BookingHistory> {
                val bookingViewModel: BookingViewModel = viewModel(
                    key = "booking_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail)
                )
                BookingHistoryScreen(
                    viewModel = bookingViewModel,
                    onBookingClick = { id -> backStack.add(Destination.BookingDetail(id)) },
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.BookingDetail> { key ->
                val bookingViewModel: BookingViewModel = viewModel(
                    key = "booking_$userUid",
                    factory = ViewModelFactory(repository = repository, userEmail = userEmail)
                )
                BookingDetailScreen(
                    bookingId = key.bookingId,
                    bookingViewModel = bookingViewModel,
                    marketplaceViewModel = marketplaceViewModel,
                    onPayNow = { id -> backStack.add(Destination.Payment(id)) },
                    onBack = { backStack.removeAt(backStack.size - 1) }
                )
            }
            entry<Destination.UserProfile> {
                val allServices by marketplaceViewModel.allServices.collectAsState()
                val savedIds = userProfile?.savedServices ?: emptyList()
                val savedAdventures = allServices.filter { it.id in savedIds }
                UserProfileScreen(
                    authViewModel = authViewModel,
                    savedAdventures = savedAdventures,
                    onAdventureClick = { id -> backStack.add(Destination.ServiceDetail(id)) },
                    onBack = { backStack.removeAt(backStack.size - 1) },
                    onLogout = {
                        authViewModel.logout {
                            backStack.clear()
                            backStack.add(Destination.Login)
                        }
                    }
                )
            }
        }
    )

        // Overlay global del Pasaporte: celebra puntos e insignias encima
        // de cualquier pantalla cuando el servidor los acredita.
        RewardOverlay(authViewModel = authViewModel)
    }
}
