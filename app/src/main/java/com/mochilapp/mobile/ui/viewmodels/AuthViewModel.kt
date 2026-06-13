package com.mochilapp.mobile.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.mochilapp.mobile.data.UserFirestore
import com.mochilapp.mobile.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(private val repository: FirebaseRepository) : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    
    private val _userProfile = MutableStateFlow<UserFirestore?>(null)
    val userProfile: StateFlow<UserFirestore?> = _userProfile

    private val _isCheckingAuth = MutableStateFlow(true)
    val isCheckingAuth: StateFlow<Boolean> = _isCheckingAuth

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        viewModelScope.launch {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                _userProfile.value = repository.getUserProfile(currentUser.uid)
            }
            _isCheckingAuth.value = false
        }
    }

    private fun loadUserProfile(uid: String) {
        viewModelScope.launch {
            _userProfile.value = repository.getUserProfile(uid)
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
                        onSuccess("TRAVELER")
                    } else {
                        _userProfile.value = profile
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
