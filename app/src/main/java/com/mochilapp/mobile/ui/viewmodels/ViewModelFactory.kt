package com.mochilapp.mobile.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mochilapp.mobile.repository.FirebaseRepository

class ViewModelFactory(
    private val repository: FirebaseRepository,
    private val userEmail: String? = null,
    private val userName: String? = null,
    private val userUid: String? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> 
                AuthViewModel(repository) as T
            modelClass.isAssignableFrom(MarketplaceViewModel::class.java) -> 
                MarketplaceViewModel(repository) as T
            modelClass.isAssignableFrom(CompanyViewModel::class.java) -> 
                CompanyViewModel(repository, userEmail!!, userUid!!) as T
            modelClass.isAssignableFrom(BookingViewModel::class.java) -> 
                BookingViewModel(repository, userEmail!!) as T
            modelClass.isAssignableFrom(SocialViewModel::class.java) ->
                SocialViewModel(repository, userEmail!!, userName!!, userUid!!) as T
            modelClass.isAssignableFrom(AiViewModel::class.java) ->
                AiViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
