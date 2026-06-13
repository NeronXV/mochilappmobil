package com.mochilapp.mobile.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.mochilapp.mobile.data.UserFirestore
import com.mochilapp.mobile.repository.FirebaseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Recompensa del Pasaporte detectada en vivo, para celebrarla en pantalla.
data class RewardEvent(val pointsGained: Long, val newBadge: String?)

class AuthViewModel(private val repository: FirebaseRepository) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    
    private val _userProfile = MutableStateFlow<UserFirestore?>(null)
    val userProfile: StateFlow<UserFirestore?> = _userProfile

    private val _isCheckingAuth = MutableStateFlow(true)
    val isCheckingAuth: StateFlow<Boolean> = _isCheckingAuth

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Escucha en vivo del documento del usuario; mantiene MochiPuntos, nivel
    // e insignias sincronizados con lo que escriben las Cloud Functions.
    private var profileJob: Job? = null

    // Eventos de recompensa (de una sola vez) para el overlay celebratorio.
    private val _rewardEvents = MutableSharedFlow<RewardEvent>(extraBufferCapacity = 8)
    val rewardEvents: SharedFlow<RewardEvent> = _rewardEvents.asSharedFlow()

    // Línea base para calcular el delta; null hasta la primera emisión, así
    // la carga inicial del perfil nunca dispara una celebración.
    private var lastPoints: Long? = null
    private var lastBadges: Set<String> = emptySet()

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        viewModelScope.launch {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                _userProfile.value = repository.getUserProfile(currentUser.uid)
                startObservingProfile(currentUser.uid)
            }
            _isCheckingAuth.value = false
        }
    }

    // Sustituye una lectura puntual por una suscripción reactiva. Ignora los
    // nulos transitorios para no borrar el perfil durante reconexiones.
    private fun startObservingProfile(uid: String) {
        profileJob?.cancel()
        lastPoints = null
        lastBadges = emptySet()
        profileJob = viewModelScope.launch {
            repository.observeUserProfile(uid).collect { profile ->
                if (profile == null) return@collect
                val prevPoints = lastPoints
                val prevBadges = lastBadges
                _userProfile.value = profile

                val currentBadges = profile.badges.toSet()
                if (prevPoints != null) {
                    val gained = profile.mochiPoints - prevPoints
                    val newBadge = (currentBadges - prevBadges).firstOrNull()
                    if (gained > 0 || newBadge != null) {
                        _rewardEvents.tryEmit(RewardEvent(gained.coerceAtLeast(0L), newBadge))
                    }
                }
                lastPoints = profile.mochiPoints
                lastBadges = currentBadges
            }
        }
    }

    fun signInWithGoogle(idToken: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                result.user?.let { user ->
                    // Check if profile exists, if not create it
                    val profile = repository.getUserProfile(user.uid)
                    if (profile == null) {
                        val newProfile = UserFirestore(
                            uid = user.uid,
                            email = user.email ?: "",
                            name = user.displayName ?: "User",
                            role = "TRAVELER" // Default role for social login
                        )
                        repository.saveUserProfile(newProfile)
                        _userProfile.value = newProfile
                        startObservingProfile(user.uid)
                        onSuccess("TRAVELER")
                    } else {
                        _userProfile.value = profile
                        startObservingProfile(user.uid)
                        onSuccess(profile.role)
                    }
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Google Sign-In failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(
        name: String,
        email: String,
        pass: String,
        role: String,
        companyType: String,
        businessName: String = "",
        businessDescription: String = "",
        phone: String = "",
        whatsapp: String = "",
        businessLocation: String = "",
        rfc: String = "",
        rnt: String = "",
        checkIn: String = "",
        checkOut: String = "",
        meetingPoint: String = "",
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = auth.createUserWithEmailAndPassword(email, pass).await()
                result.user?.uid?.let { uid ->
                    val profile = UserFirestore(
                        uid = uid,
                        email = email,
                        name = name,
                        role = role,
                        companyType = companyType,
                        businessName = businessName,
                        businessDescription = businessDescription,
                        phone = phone,
                        whatsapp = whatsapp,
                        businessLocation = businessLocation,
                        rfc = rfc,
                        rnt = rnt,
                        checkIn = checkIn,
                        checkOut = checkOut,
                        meetingPoint = meetingPoint,
                        status = if (role == "COMPANY") "PENDING" else "ACTIVE"
                    )
                    repository.saveUserProfile(profile)
                    _userProfile.value = profile
                    startObservingProfile(uid)
                    onSuccess(role)
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Registration failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun login(email: String, pass: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = auth.signInWithEmailAndPassword(email, pass).await()
                result.user?.uid?.let { uid ->
                    val profile = repository.getUserProfile(uid)
                    _userProfile.value = profile
                    if (profile != null) {
                        startObservingProfile(uid)
                        onSuccess(profile.role)
                    } else {
                        onError("Profile not found")
                    }
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Login failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        profileJob?.cancel()
        profileJob = null
        auth.signOut()
        _userProfile.value = null
        onComplete()
    }

    fun resetPassword(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                auth.sendPasswordResetEmail(email).await()
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error sending reset email")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateProfile(name: String, bio: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentProfile = _userProfile.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updatedProfile = currentProfile.copy(name = name, bio = bio)
                repository.saveUserProfile(updatedProfile)
                _userProfile.value = updatedProfile
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error updating profile")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Datos comerciales del perfil de empresa; role y businessVerified
    // están protegidos por las reglas de Firestore
    fun updateBusinessProfile(
        name: String,
        businessDescription: String,
        phone: String,
        whatsapp: String,
        businessLocation: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentProfile = _userProfile.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updatedProfile = currentProfile.copy(
                    name = name,
                    businessDescription = businessDescription,
                    phone = phone,
                    whatsapp = whatsapp,
                    businessLocation = businessLocation
                )
                repository.saveUserProfile(updatedProfile)
                _userProfile.value = updatedProfile
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error updating business profile")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Guarda/quita un servicio de "Mis Aventuras". El listener en vivo del
    // perfil actualiza la UI, así que no tocamos _userProfile a mano aquí.
    fun toggleSavedService(serviceId: String) {
        val profile = _userProfile.value ?: return
        val currentlySaved = serviceId in profile.savedServices
        viewModelScope.launch {
            try {
                repository.setServiceSaved(profile.uid, serviceId, !currentlySaved)
            } catch (_: Exception) {
                // Silencioso: un fallo de red no debe romper la navegación
            }
        }
    }

    fun updateProfileImage(uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val currentProfile = _userProfile.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val imageUrl = repository.uploadProfileImage(uri, currentProfile.uid)
                val updatedProfile = currentProfile.copy(profileImageUrl = imageUrl)
                repository.saveUserProfile(updatedProfile)
                _userProfile.value = updatedProfile
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Error uploading image")
            } finally {
                _isLoading.value = false
            }
        }
    }
}
