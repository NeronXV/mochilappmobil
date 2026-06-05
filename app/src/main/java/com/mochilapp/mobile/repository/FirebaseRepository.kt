package com.mochilapp.mobile.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableResult
import com.google.firebase.storage.FirebaseStorage
import com.mochilapp.mobile.data.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val functions = FirebaseFunctions.getInstance()

    companion object {
        private const val TAG = "FirebaseRepository"
    }

    // --- Authentication ---
    fun getCurrentUserUid(): String? = auth.currentUser?.uid
    fun getCurrentUserEmail(): String? = auth.currentUser?.email

    suspend fun getUserProfile(uid: String): UserFirestore? {
        return try {
            firestore.collection("users").document(uid).get().await().toObject(UserFirestore::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user profile", e)
            null
        }
    }

    suspend fun saveUserProfile(user: UserFirestore) {
        try {
            firestore.collection("users").document(user.uid).set(user).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user profile", e)
            throw e
        }
    }

    suspend fun uploadProfileImage(uri: Uri, uid: String): String {
        return uploadImage(uri, "profiles/$uid/avatar.jpg")
    }

    // --- Services ---
    fun getAllServices(): Flow<List<ServiceFirestore>> = callbackFlow {
        val subscription = firestore.collection("services")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to services", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.toObjects(ServiceFirestore::class.java))
                }
            }
        awaitClose { subscription.remove() }
    }

    fun getServicesByType(type: String): Flow<List<ServiceFirestore>> = callbackFlow {
        val subscription = firestore.collection("services")
            .whereEqualTo("type", type)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    trySend(snapshot.toObjects(ServiceFirestore::class.java))
                }
            }
        awaitClose { subscription.remove() }
    }

    fun getServicesByOwner(email: String): Flow<List<ServiceFirestore>> = callbackFlow {
        val subscription = firestore.collection("services")
            .whereEqualTo("ownerEmail", email)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    trySend(snapshot.toObjects(ServiceFirestore::class.java))
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun getServiceById(id: String): ServiceFirestore? {
        return try {
            firestore.collection("services").document(id).get().await().toObject(ServiceFirestore::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting service by ID", e)
            null
        }
    }

    suspend fun addService(service: ServiceFirestore, imageUri: Uri? = null) {
        try {
            var imageUrl = service.imageUrl
            if (imageUri != null) {
                imageUrl = uploadImage(imageUri, "services/${auth.currentUser?.uid}/${UUID.randomUUID()}")
            }
            firestore.collection("services").add(service.copy(imageUrl = imageUrl)).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding service", e)
            throw e
        }
    }

    suspend fun deleteService(id: String) {
        try {
            // Firestore physical delete - now deprecated in favor of soft delete
            firestore.collection("services").document(id).delete().await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting service", e)
        }
    }

    suspend fun updateServiceVisibility(id: String, isVisible: Boolean) {
        try {
            firestore.collection("services").document(id).update("isVisible", isVisible).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating service visibility", e)
            throw e
        }
    }

    // --- Bookings ---
    suspend fun addBooking(booking: BookingFirestore): String {
        return try {
            val doc = firestore.collection("bookings").add(booking).await()
            doc.id
        } catch (e: Exception) {
            Log.e(TAG, "Error adding booking", e)
            throw e
        }
    }

    fun getBookingsForUser(email: String): Flow<List<BookingFirestore>> = callbackFlow {
        val subscription = firestore.collection("bookings")
            .whereEqualTo("travelerEmail", email)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) trySend(snapshot.toObjects(BookingFirestore::class.java))
            }
        awaitClose { subscription.remove() }
    }

    fun getBookingsForOwner(email: String): Flow<List<BookingFirestore>> = callbackFlow {
        val subscription = firestore.collection("bookings")
            .whereEqualTo("ownerEmail", email)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) trySend(snapshot.toObjects(BookingFirestore::class.java))
            }
        awaitClose { subscription.remove() }
    }

    suspend fun updateBookingStatus(id: String, status: String) {
        try {
            firestore.collection("bookings").document(id).update("status", status).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating booking status", e)
        }
    }

    suspend fun updateBookingFields(id: String, fields: Map<String, Any>) {
        try {
            firestore.collection("bookings").document(id).update(fields).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating booking fields", e)
        }
    }

    fun getBookingsByServiceAndDate(serviceId: String, date: String): Flow<List<BookingFirestore>> = callbackFlow {
        val subscription = firestore.collection("bookings")
            .whereEqualTo("serviceId", serviceId)
            .whereEqualTo("date", date)
            .whereNotEqualTo("status", "CANCELLED")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) trySend(snapshot.toObjects(BookingFirestore::class.java))
            }
        awaitClose { subscription.remove() }
    }

    // --- Posts & Community ---
    fun getAllPosts(): Flow<List<PostFirestore>> = callbackFlow {
        val subscription = firestore.collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to posts", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.toObjects(PostFirestore::class.java))
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addPost(post: PostFirestore, imageUri: Uri? = null) {
        try {
            var imageUrl = post.imageUrl
            if (imageUri != null) {
                imageUrl = uploadImage(imageUri, "posts/${auth.currentUser?.uid}/${UUID.randomUUID()}")
            }
            firestore.collection("posts").add(post.copy(imageUrl = imageUrl)).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding post", e)
            throw e
        }
    }

    suspend fun toggleLike(postId: String, userUid: String) {
        val docRef = firestore.collection("posts").document(postId)
        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val post = snapshot.toObject(PostFirestore::class.java) ?: return@runTransaction
                val likedBy = post.likedBy.toMutableList()
                var likes = post.likes

                if (userUid in likedBy) {
                    likedBy.remove(userUid)
                    likes -= 1
                } else {
                    likedBy.add(userUid)
                    likes += 1
                }

                transaction.update(docRef, "likedBy", likedBy)
                transaction.update(docRef, "likes", likes)
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling like", e)
        }
    }

    fun getCommentsForPost(postId: String): Flow<List<CommentFirestore>> = callbackFlow {
        val subscription = firestore.collection("comments")
            .whereEqualTo("postId", postId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) trySend(snapshot.toObjects(CommentFirestore::class.java))
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addComment(comment: CommentFirestore) {
        try {
            firestore.collection("comments").add(comment).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding comment", e)
        }
    }

    // --- Reviews ---
    suspend fun addReview(review: ReviewFirestore) {
        // ... existente
    }

    // --- Flash Promos ---
    fun getActivePromos(): Flow<List<PromoFirestore>> = callbackFlow {
        val subscription = firestore.collection("promos")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) trySend(snapshot.toObjects(PromoFirestore::class.java))
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addPromo(promo: PromoFirestore) {
        try {
            firestore.collection("promos").add(promo).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding promo", e)
        }
    }

    // --- Stripe Payments ---
    suspend fun createPaymentIntent(data: Map<String, Any?>): Map<String, Any>? {
        return try {
            val result = functions
                .getHttpsCallable("createPaymentIntent")
                .call(data)
                .await()
            
            @Suppress("UNCHECKED_CAST")
            result.getData() as Map<String, Any>?
        } catch (e: Exception) {
            Log.e(TAG, "Error calling createPaymentIntent", e)
            null
        }
    }

    // --- Storage ---
    private suspend fun uploadImage(uri: Uri, path: String): String {
        return try {
            val ref = storage.reference.child(path)
            ref.putFile(uri).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image to storage", e)
            throw e
        }
    }
}
