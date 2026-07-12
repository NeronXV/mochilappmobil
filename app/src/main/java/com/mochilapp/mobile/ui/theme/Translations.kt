package com.mochilapp.mobile.ui.theme

import androidx.compose.runtime.*

enum class AppLanguage(val code: String) {
    ESPAÑOL("es"),
    ENGLISH("en"),
    FRANÇAIS("fr")
}

object Translations {
    private val data = mapOf(
        "app_name" to mapOf("es" to "Mochilapp", "en" to "Mochilapp", "fr" to "Mochilapp"),
        "welcome" to mapOf("es" to "Bienvenido", "en" to "Welcome", "fr" to "Bienvenue"),
        "login" to mapOf("es" to "Iniciar Sesión", "en" to "Log In", "fr" to "Se Connecter"),
        "register" to mapOf("es" to "Registrarse", "en" to "Register", "fr" to "S'inscrire"),
        "email" to mapOf("es" to "Correo electrónico", "en" to "Email", "fr" to "E-mail"),
        "password" to mapOf("es" to "Contraseña", "en" to "Password", "fr" to "Mot de passe"),
        "continue" to mapOf("es" to "Continuar", "en" to "Continue", "fr" to "Continuer"),
        "name" to mapOf("es" to "Nombre completo", "en" to "Full name", "fr" to "Nom complet"),
        "role_traveler" to mapOf("es" to "Viajero", "en" to "Traveler", "fr" to "Voyageur"),
        "role_company" to mapOf("es" to "Empresa", "en" to "Company", "fr" to "Entreprise"),
        "search_placeholder" to mapOf("es" to "Buscar destinos, tours...", "en" to "Search destinations, tours...", "fr" to "Rechercher des destinations..."),
        "explore" to mapOf("es" to "Explora", "en" to "Explore", "fr" to "Explorer"),
        "home" to mapOf("es" to "Inicio", "en" to "Home", "fr" to "Accueil"),
        "bookings" to mapOf("es" to "Reservas", "en" to "Bookings", "fr" to "Réservations"),
        "profile" to mapOf("es" to "Perfil", "en" to "Profile", "fr" to "Profil"),
        "map" to mapOf("es" to "Mapa", "en" to "Map", "fr" to "Carte"),
        "recommended" to mapOf("es" to "Recomendado para ti", "en" to "Recommended for you", "fr" to "Recommandé pour vous"),
        "discover" to mapOf("es" to "Descubre Experiencias", "en" to "Discover Experiences", "fr" to "Découvrir des expériences"),
        "date" to mapOf("es" to "Fecha", "en" to "Date", "fr" to "Date"),
        "people" to mapOf("es" to "Personas", "en" to "People", "fr" to "Personnes"),
        "price" to mapOf("es" to "Precio", "en" to "Price", "fr" to "Prix"),
        "all" to mapOf("es" to "Todos", "en" to "All", "fr" to "Tous"),
        "logout" to mapOf("es" to "Cerrar Sesión", "en" to "Log Out", "fr" to "Déconnexion"),
        "no_services" to mapOf("es" to "No hay servicios disponibles", "en" to "No services available", "fr" to "Aucun service disponible"),
        "editorial_tag" to mapOf("es" to "Mochilapp te recomienda", "en" to "Mochilapp recommends", "fr" to "Mochilapp recommande"),
        "create_account" to mapOf("es" to "Crea tu cuenta", "en" to "Create your account", "fr" to "Créez votre compte"),
        "profile_type" to mapOf("es" to "Tipo de perfil:", "en" to "Profile type:", "fr" to "Type de profil:"),
        "already_have_account" to mapOf("es" to "¿Ya tienes cuenta? Inicia sesión", "en" to "Already have an account? Log in", "fr" to "Vous avez déjà un compte? Connectez-vous"),
        "company_type" to mapOf("es" to "Tipo de empresa", "en" to "Company type", "fr" to "Type d'entreprise"),
        "cancel" to mapOf("es" to "Cancelar", "en" to "Cancel", "fr" to "Annuler"),
        "search_title" to mapOf("es" to "Busca tu próxima aventura", "en" to "Find your next adventure", "fr" to "Trouvez votre prochaine aventure"),
        "no_results" to mapOf("es" to "No se encontraron resultados", "en" to "No results found", "fr" to "Aucun résultat trouvé"),
        "results" to mapOf("es" to "Resultados", "en" to "Results", "fr" to "Résultats"),
        "book_now" to mapOf("es" to "Reservar Ahora", "en" to "Book Now", "fr" to "Réserver"),
        "about" to mapOf("es" to "Acerca de", "en" to "About", "fr" to "À propos"),
        "location" to mapOf("es" to "Ubicación", "en" to "Location", "fr" to "Emplacement"),
        "rating" to mapOf("es" to "Rating", "en" to "Rating", "fr" to "Évaluation"),
        "duration" to mapOf("es" to "Duración", "en" to "Duration", "fr" to "Durée"),
        "people_count" to mapOf("es" to "Personas", "en" to "People", "fr" to "Personnes"),
        "total_price" to mapOf("es" to "Precio total", "en" to "Total price", "fr" to "Prix total"),
        "confirm_details" to mapOf("es" to "Confirma tus detalles", "en" to "Confirm your details", "fr" to "Confirmez vos détails"),
        "payment_continue" to mapOf("es" to "Continuar al Pago", "en" to "Continue to Payment", "fr" to "Continuer au paiement"),
        "community" to mapOf("es" to "Comunidad", "en" to "Community", "fr" to "Communauté"),
        "share_adventure" to mapOf("es" to "Compartir", "en" to "Share", "fr" to "Partager"),
        "ai_assistant" to mapOf("es" to "Asistente AI", "en" to "AI Assistant", "fr" to "Assistant IA"),
        "ask_mochi" to mapOf("es" to "Pregúntame por recomendaciones...", "en" to "Ask me for recommendations...", "fr" to "Demandez-moi des recommandations..."),
        "mochi_thinking" to mapOf("es" to "Mochi está pensando...", "en" to "Mochi is thinking...", "fr" to "Mochi réfléchit..."),
        "create_post" to mapOf("es" to "Crear Post", "en" to "Create Post", "fr" to "Créer un post"),
        "add_service" to mapOf("es" to "Agregar Servicio", "en" to "Add Service", "fr" to "Ajouter un service"),
        "ai_maintenance_msg" to mapOf("es" to "Estamos ajustando los últimos detalles de MochiBot. ¡Vuelve pronto!", "en" to "We're fine-tuning MochiBot's final details. Come back soon!", "fr" to "Nous peaufinons les derniers détails de MochiBot. Revenez bientôt !"),
        
        // Dashboards & Navigation
        "nav_panel" to mapOf("es" to "Panel", "en" to "Dashboard", "fr" to "Tableau"),
        "nav_search" to mapOf("es" to "Explorar", "en" to "Explore", "fr" to "Explorer"),
        "nav_bookings" to mapOf("es" to "Reservas", "en" to "Bookings", "fr" to "Réservations"),
        "nav_profile" to mapOf("es" to "Perfil", "en" to "Profile", "fr" to "Profil"),
        "nav_community" to mapOf("es" to "Comunidad", "en" to "Community", "fr" to "Communauté"),
        "nav_services" to mapOf("es" to "Servicios", "en" to "Services", "fr" to "Services"),
        
        "empty_community_company" to mapOf("es" to "Pronto verás aquí publicaciones y reseñas relacionadas con tu negocio.", "en" to "Soon you'll see posts and reviews related to your business here.", "fr" to "Bientôt, vous verrez ici les publications et les avis liés à votre entreprise."),
        "empty_services_company" to mapOf("es" to "Aún no tienes servicios publicados.", "en" to "You don't have any published services yet.", "fr" to "Vous n'avez pas encore de services publiés."),
        
        "filter_dates" to mapOf("es" to "Fechas", "en" to "Dates", "fr" to "Dates"),
        "filter_guests" to mapOf("es" to "Personas", "en" to "Guests", "fr" to "Personnes"),
        "filter_price" to mapOf("es" to "Precio", "en" to "Price", "fr" to "Prix"),
        "filter_all" to mapOf("es" to "Todos", "en" to "All", "fr" to "Tous"),
        "clear_filters" to mapOf("es" to "Limpiar filtros", "en" to "Clear filters", "fr" to "Effacer"),
        "filters_button" to mapOf("es" to "Filtros", "en" to "Filters", "fr" to "Filtres"),
        
        "company_dashboard_title" to mapOf("es" to "Dashboard de gestión", "en" to "Management Dashboard", "fr" to "Tableau de gestion"),
        "company_new_service" to mapOf("es" to "Nuevo Servicio", "en" to "New Service", "fr" to "Nouveau Service"),
        "company_flash_promo" to mapOf("es" to "Oferta Flash", "en" to "Flash Deal", "fr" to "Offre Flash"),
        "company_bookings_today" to mapOf("es" to "Reservas hoy", "en" to "Today's bookings", "fr" to "Réservations aujourd'hui"),
        "company_pending_requests" to mapOf("es" to "Solicitudes pendientes", "en" to "Pending requests", "fr" to "Demandes en attente"),
        "company_free_slots" to mapOf("es" to "Cupos libres (hoy)", "en" to "Free slots (today)", "fr" to "Places libres (aujourd'hui)"),
        "company_free_departures" to mapOf("es" to "Salidas disponibles (hoy)", "en" to "Available departures (today)", "fr" to "Départs disponibles (aujourd'hui)"),
        "company_upcoming_bookings" to mapOf("es" to "Próximas reservas", "en" to "Upcoming bookings", "fr" to "Prochaines réservations"),
        "company_weekly_performance" to mapOf("es" to "Rendimiento Semanal", "en" to "Weekly Performance", "fr" to "Performance Hebdomadaire"),
        "company_estimated_revenue" to mapOf("es" to "INGRESOS ESTIMADOS", "en" to "ESTIMATED REVENUE", "fr" to "REVENU ESTIMÉ"),
        "company_web_panel" to mapOf("es" to "Mi Panel Empresarial (Web)", "en" to "My Web Panel", "fr" to "Mon Panneau Web"),
        "company_no_bookings" to mapOf("es" to "Aún no tienes reservas.", "en" to "No bookings yet.", "fr" to "Pas encore de réservations."),
        "view_all" to mapOf("es" to "VER TODAS", "en" to "VIEW ALL", "fr" to "VOIR TOUT"),
        "flash_promos_title" to mapOf("es" to "¡Ofertas Relámpago! ⚡", "en" to "Flash Deals! ⚡", "fr" to "Offres Flash ! ⚡"),
        
        "flash_promo_dialog_title" to mapOf("es" to "Lanzar Oferta Relámpago ⚡", "en" to "Launch Flash Deal ⚡", "fr" to "Lancer une offre flash ⚡"),
        "flash_promo_dialog_desc" to mapOf("es" to "Esta oferta aparecerá instantáneamente en el feed de todos los viajeros.", "en" to "This deal will instantly appear in all travelers' feeds.", "fr" to "Cette offre apparaîtra instantanément dans le flux de tous les voyageurs."),
        "flash_promo_msg_label" to mapOf("es" to "Mensaje (Ej: 2 lugares para hoy)", "en" to "Message (e.g. 2 spots left today)", "fr" to "Message (Ex: 2 places pour aujourd'hui)"),
        "flash_promo_discount_label" to mapOf("es" to "Descuento (Ej: 50% u Oferta)", "en" to "Discount (e.g. 50% or Deal)", "fr" to "Remise (Ex: 50% ou Offre)"),
        "send" to mapOf("es" to "Enviar", "en" to "Send", "fr" to "Envoyer"),
        "no_adventures" to mapOf("es" to "Aún no tienes aventuras guardadas.", "en" to "You don't have any saved adventures yet.", "fr" to "Vous n'avez pas encore d'aventures enregistrées."),
        "coming_soon" to mapOf("es" to "Próximamente", "en" to "Coming Soon", "fr" to "Prochainement"),
        
        "booking_details" to mapOf("es" to "Detalle de reserva", "en" to "Booking details", "fr" to "Détails de réservation"),
        "qr_code" to mapOf("es" to "Código QR", "en" to "QR Code", "fr" to "Code QR"),
        "how_to_get_there" to mapOf("es" to "Cómo llegar", "en" to "How to get there", "fr" to "Comment s'y rendre"),
        "paid_status" to mapOf("es" to "Pagada", "en" to "Paid", "fr" to "Payée"),
        "pending_status" to mapOf("es" to "Pendiente de pago", "en" to "Pending payment", "fr" to "En attente"),
        "cancelled_status" to mapOf("es" to "Cancelada", "en" to "Cancelled", "fr" to "Annulée"),
        "checked_in_status" to mapOf("es" to "Validada", "en" to "Checked-in", "fr" to "Validée"),
        "completed_status" to mapOf("es" to "Ejecutada", "en" to "Completed", "fr" to "Terminée"),
        "no_location_msg" to mapOf("es" to "No tenemos ubicación disponible para este servicio.", "en" to "No location available for this service.", "fr" to "Aucun emplacement disponible pour ce service."),
        
        "confirm_arrival" to mapOf("es" to "Confirmar llegada", "en" to "Confirm arrival", "fr" to "Confirmer l'arrivée"),
        "manual_code_entry" to mapOf("es" to "Ingresar código manual", "en" to "Manual code entry", "fr" to "Saisie manuelle"),
        "mark_completed" to mapOf("es" to "Marcar como ejecutado", "en" to "Mark as completed", "fr" to "Marquer comme terminé"),
        "invalid_booking_msg" to mapOf("es" to "Esta reserva no pertenece a tu negocio o no está pagada.", "en" to "This booking doesn't belong to your business or isn't paid.", "fr" to "Cette réservation n'appartient pas à votre entreprise ou n'est pas payée."),
        "booking_validated_success" to mapOf("es" to "Reserva validada correctamente", "en" to "Booking validated successfully", "fr" to "Réservation validée avec succès"),
        "service_completed_success" to mapOf("es" to "Servicio marcado como ejecutado", "en" to "Service marked as completed", "fr" to "Service marqué comme terminé"),

        // Tipos de servicio (CompanyType) para mostrar al viajero
        "type_hotel" to mapOf("es" to "Hotel", "en" to "Hotel", "fr" to "Hôtel"),
        "type_hostel" to mapOf("es" to "Hostal", "en" to "Hostel", "fr" to "Auberge"),
        "type_property_rental" to mapOf("es" to "Renta vacacional", "en" to "Vacation rental", "fr" to "Location de vacances"),
        "type_restaurant" to mapOf("es" to "Restaurante", "en" to "Restaurant", "fr" to "Restaurant"),
        "type_food_stand" to mapOf("es" to "Puesto de comida", "en" to "Food stand", "fr" to "Stand de nourriture"),
        "type_boat_tour" to mapOf("es" to "Tour en lancha", "en" to "Boat tour", "fr" to "Tour en bateau"),
        "type_tour_agency" to mapOf("es" to "Agencia de tours", "en" to "Tour agency", "fr" to "Agence de tours"),
        "type_transport" to mapOf("es" to "Transporte", "en" to "Transport", "fr" to "Transport"),
        "type_other" to mapOf("es" to "Experiencia", "en" to "Experience", "fr" to "Expérience")
    )

    fun getString(key: String, lang: String): String {
        val result = data[key]?.get(lang)
        if (result == null) {
            android.util.Log.w("TRANSLATIONS", "Missing key: $key for language: $lang")
        }
        return result ?: data[key]?.get("es") ?: key
    }
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.ESPAÑOL }

@Composable
fun t(key: String): String {
    val currentLang = LocalAppLanguage.current
    return Translations.getString(key, currentLang.code)
}

// Nombre legible de un CompanyType almacenado como string ("BOAT_TOUR" -> "Tour en lancha");
// si el tipo no está catalogado, regresa el valor tal cual
@Composable
fun serviceTypeLabel(type: String): String {
    val key = "type_${type.lowercase()}"
    val label = t(key)
    return if (label == key) type else label
}
