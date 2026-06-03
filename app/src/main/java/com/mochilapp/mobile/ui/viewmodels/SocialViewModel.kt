package com.mochilapp.mobile.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochilapp.mobile.data.CommentFirestore
import com.mochilapp.mobile.data.PostFirestore
import com.mochilapp.mobile.repository.FirebaseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SocialViewModel(
    private val repository: FirebaseRepository,
    private val userEmail: String,
    private val userName: String,
    val userUid: String
) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    val posts: StateFlow<List<PostFirestore>> = repository.getAllPosts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPost(content: String, imageUrl: String, imageUri: Uri? = null, linkedServiceId: String? = null, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val post = PostFirestore(
                    authorEmail = userEmail,
                    authorName = userName,
                    content = content,
                    imageUrl = imageUrl,
                    linkedServiceId = linkedServiceId
                )
                repository.addPost(post, imageUri)
                onComplete()
            } catch (e: Exception) {
                // Log error
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleLike(post: PostFirestore) {
        viewModelScope.launch {
            repository.toggleLike(post.id, userUid)
        }
    }

    fun getComments(postId: String): Flow<List<CommentFirestore>> {
        return repository.getCommentsForPost(postId)
    }

    fun addComment(postId: String, content: String) {
        viewModelScope.launch {
            val comment = CommentFirestore(
                postId = postId,
                authorName = userName,
                content = content
            )
            repository.addComment(comment)
        }
    }
}
