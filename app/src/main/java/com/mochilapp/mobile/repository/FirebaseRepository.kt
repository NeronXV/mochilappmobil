package com.mochilapp.mobile.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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

    // Busca la empresa dueña de un servicio por su email, para exponer su
    // WhatsApp/teléfono al viajero (el contacto vive en el doc del usuario).
    suspend fun getUserByEmail(email: String): UserFirestore? {
        return try {
            // Filtramos por role==COMPANY: las reglas solo permiten listar
            // empresas (no viajeros), y siempre buscamos al dueño del servicio.
            firestore.collection("users")
                .whereEqualTo("email", email)
                .whereEqualTo("role", "COMPANY")
                .limit(1)
                .get().await()
                .toObjects(UserFirestore::class.java)
                .firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user by email", e)
            null
        }
    }

    // Empresas para la fila de círculos del viajero ("abierto ahora").
    fun getCompanies(): Flow<List<UserFirestore>> = callbackFlow {
        val subscription = firestore.collection("users")
            .whereEqualTo("role", "COMPANY")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to companies", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) trySend(snapshot.toObjects(UserFirestore::class.java))
            }
        awaitClose { subscription.remove() }
    }

    suspend fun saveUserProfile(user: UserFirestore) {
        try {
            firestore.collection("users").document(user.uid).set(user).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user profile", e)
            throw e
        }
    }

    // Guarda o quita un servicio de "Mis Aventuras" del viajero. Usa
    // arrayUnion/arrayRemove para no pisar la lista ante escrituras paralelas.
    suspend fun setServiceSaved(uid: String, serviceId: String, saved: Boolean) {
        try {
            val op = if (saved) FieldValue.arrayUnion(serviceId)
            else FieldValue.arrayRemove(serviceId)
            firestore.collection("users").document(uid)
                .update("savedServices", op).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating saved services", e)
            throw e
        }
    }

    // Perfil en tiempo real: refleja al instante los MochiPuntos, nivel e
    // insignias que otorgan las Cloud Functions del Pasaporte.
    fun observeUserProfile(uid: String): Flow<UserFirestore?> = callbackFlow {
        val subscription = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing user profile", error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(UserFirestore::class.java))
            }
        awaitClose { subscription.remove() }
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
                if (error != null) {
                    // No tragar el error: un fallo de permisos o de red dejaba el
                    // panel vacío sin pista alguna. Ahora queda en el Logcat.
                    Log.e(TAG, "Error escuchando servicios de '$email'", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    // Deserializar documento por documento en vez de snapshot.toObjects():
                    // un solo doc con un campo de tipo incompatible (p. ej. creado desde
                    // el panel web) ya no hace desaparecer TODOS los servicios.
                    val services = snapshot.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(ServiceFirestore::class.java)
                        } catch (e: Exception) {
                            Log.e(TAG, "Servicio '${doc.id}' ignorado: no se pudo leer", e)
                            null
                        }
                    }
                    trySend(services)
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
            val currentUser = auth.currentUser ?: throw IllegalStateException("Debes iniciar sesión para publicar.")
            val currentEmail = currentUser.email ?: throw IllegalStateException("Tu cuenta no tiene email de autenticación.")
            var imageUrl = service.imageUrl
            if (imageUri != null) {
                imageUrl = uploadImage(imageUri, "services/${currentUser.uid}/${UUID.randomUUID()}")
            }
            val fields = mapOf(
                "ownerEmail" to currentEmail,
                "ownerUid" to currentUser.uid,
                "name" to service.name,
                "description" to service.description,
                "price" to service.price,
                "type" to service.type,
                "location" to service.location,
                "imageUrl" to imageUrl,
                "rating" to 0.0,
                "reviewCount" to 0,
                "capacity" to service.capacity,
                "departureTimes" to service.departureTimes,
                "businessHours" to service.businessHours,
                "isOpen" to service.isOpen,
                "amenities" to service.amenities,
                "rules" to service.rules,
                "routeName" to service.routeName,
                "origin" to service.origin,
                "destination" to service.destination,
                "vehicleName" to service.vehicleName,
                "driverName" to service.driverName,
                "guideName" to service.guideName,
                "meetingPoint" to service.meetingPoint,
                "checkIn" to service.checkIn,
                "checkOut" to service.checkOut,
                "menu" to service.menu,
                "rooms" to service.rooms,
                "isVisible" to service.isVisible,
                "isRecommended" to false,
                "latitude" to service.latitude,
                "longitude" to service.longitude,
                "address" to service.address,
                "offersPickup" to service.offersPickup,
                "offersDelivery" to service.offersDelivery,
                "deliveryFee" to service.deliveryFee
            )
            firestore.collection("services").add(fields).await()
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

    suspend fun updateService(id: String, service: ServiceFirestore, imageUri: Uri? = null) {
        try {
            var imageUrl = service.imageUrl
            if (imageUri != null) {
                imageUrl = uploadImage(imageUri, "services/${auth.currentUser?.uid}/${UUID.randomUUID()}")
            }
            // Solo campos editables: rating, reviewCount, ownerEmail e isRecommended
            // están protegidos por las reglas de Firestore
            val fields = mapOf(
                "name" to service.name,
                "description" to service.description,
                "price" to service.price,
                "type" to service.type,
                "location" to service.location,
                "imageUrl" to imageUrl,
                "capacity" to service.capacity,
                "departureTimes" to service.departureTimes,
                "meetingPoint" to service.meetingPoint,
                "checkIn" to service.checkIn,
                "checkOut" to service.checkOut,
                "amenities" to service.amenities,
                "rules" to service.rules,
                "routeName" to service.routeName,
                "origin" to service.origin,
                "destination" to service.destination,
                "vehicleName" to service.vehicleName,
                "driverName" to service.driverName,
                "guideName" to service.guideName,
                "businessHours" to service.businessHours,
                "isOpen" to service.isOpen,
                "address" to service.address,
                "latitude" to service.latitude,
                "longitude" to service.longitude,
                "offersPickup" to service.offersPickup,
                "offersDelivery" to service.offersDelivery,
                "deliveryFee" to service.deliveryFee
            )
            firestore.collection("services").document(id).update(fields).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating service", e)
            throw e
        }
    }

    // Actualiza solo el menú del servicio (módulo gastronómico)
    suspend fun updateServiceMenu(id: String, menu: List<MenuItemFirestore>) {
        try {
            firestore.collection("services").document(id).update("menu", menu).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating service menu", e)
            throw e
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

    // Solo filtro de igualdad por fecha (sin índice compuesto); las canceladas
    // se descartan en el cliente
    fun getBookingsByDate(date: String): Flow<List<BookingFirestore>> = callbackFlow {
        val subscription = firestore.collection("bookings")
            .whereEqualTo("date", date)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    trySend(snapshot.toObjects(BookingFirestore::class.java)
                        .filter { it.status != "CANCELLED" })
                }
            }
        awaitClose { subscription.remove() }
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

    // --- Stories (historias efímeras de empresas, 24h) ---
    suspend fun addStory(story: StoryFirestore, imageUri: Uri) {
        try {
            val imageUrl = uploadImage(imageUri, "stories/${auth.currentUser?.uid}/${UUID.randomUUID()}")
            firestore.collection("stories").add(story.copy(imageUrl = imageUrl)).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding story", e)
            throw e
        }
    }

    fun getActiveStories(): Flow<List<StoryFirestore>> = callbackFlow {
        val subscription = firestore.collection("stories")
            .whereGreaterThan("expiresAt", System.currentTimeMillis())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to stories", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) trySend(snapshot.toObjects(StoryFirestore::class.java))
            }
        awaitClose { subscription.remove() }
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
        try {
            // El promedio (rating/reviewCount) lo recalcula la Cloud Function
            // onReviewCreated: las reglas de Firestore impiden que un viajero
            // modifique la reputación del servicio directamente.
            firestore.collection("reviews").add(review).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding review", e)
            throw e
        }
    }

    fun getReviewsForService(serviceId: String): Flow<List<ReviewFirestore>> = callbackFlow {
        val subscription = firestore.collection("reviews")
            .whereEqualTo("serviceId", serviceId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    trySend(snapshot.toObjects(ReviewFirestore::class.java).sortedByDescending { it.timestamp })
                }
            }
        awaitClose { subscription.remove() }
    }

    // --- Flash Promos ---
    fun getActivePromos(): Flow<List<PromoFirestore>> = callbackFlow {
        val subscription = firestore.collection("promos")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val now = System.currentTimeMillis()
                    val active = snapshot.toObjects(PromoFirestore::class.java)
                        .filter { promo ->
                            // Promos sin expiresAt (legado) caducan 24h después de creadas
                            val expiry = if (promo.expiresAt > 0L) promo.expiresAt
                                         else promo.timestamp + 24 * 60 * 60 * 1000L
                            promo.isActive && expiry > now
                        }
                        .take(10)
                    trySend(active)
                }
            }
        awaitClose { subscription.remove() }
    }

    fun getPromosByOwner(email: String): Flow<List<PromoFirestore>> = callbackFlow {
        val subscription = firestore.collection("promos")
            .whereEqualTo("ownerEmail", email)
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

    // --- Avisos (notices) ---
    suspend fun addNotice(notice: NoticeFirestore) {
        try {
            firestore.collection("notices").add(notice).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error adding notice", e)
            throw e
        }
    }

    fun getNoticesByOwner(email: String): Flow<List<NoticeFirestore>> = callbackFlow {
        val subscription = firestore.collection("notices")
            .whereEqualTo("ownerEmail", email)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    trySend(snapshot.toObjects(NoticeFirestore::class.java)
                        .sortedByDescending { it.timestamp })
                }
            }
        awaitClose { subscription.remove() }
    }

    // Avisos vigentes de toda la plataforma; el volumen es bajo y el viajero
    // los cruza en el cliente contra sus reservas/servicio en pantalla
    fun getActiveNotices(): Flow<List<NoticeFirestore>> = callbackFlow {
        val subscription = firestore.collection("notices")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val now = System.currentTimeMillis()
                    trySend(snapshot.toObjects(NoticeFirestore::class.java)
                        .filter { it.isActive && (it.expiresAt <= 0L || it.expiresAt > now) })
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun deactivateNotice(id: String) {
        try {
            firestore.collection("notices").document(id).update("isActive", false).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error deactivating notice", e)
        }
    }

    // --- FCM ---
    suspend fun registerFcmToken() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
            firestore.collection("users").document(uid).update("fcmToken", token).await()
        } catch (e: Exception) {
            Log.e(TAG, "Error registering FCM token", e)
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
