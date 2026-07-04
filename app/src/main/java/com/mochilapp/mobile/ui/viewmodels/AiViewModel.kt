package com.mochilapp.mobile.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    // Servicios reales que Mochi recomendó, para pintarlos como tarjetas
    // reservables bajo su mensaje.
    val recommendedServices: List<ServiceFirestore> = emptyList()
)

// La llamada a Gemini vive en la Cloud Function askMochi (la API key está en
// Secret Manager, no en el APK). Aquí solo se arma el contexto y el historial.
class AiViewModel(
    private val repository: FirebaseRepository
) : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage("¡Hola! Soy tu asistente inteligente de Mochilapp. Puedo ayudarte a encontrar los mejores tours, hoteles y planificar tu viaje basándome en los servicios reales disponibles. ¿A dónde quieres ir?", false)
    ))
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun sendMessage(userText: String, lang: String = "es") {
        if (userText.isBlank()) return

        // Historial previo (sin el mensaje nuevo) para que Mochi mantenga hilo;
        // el saludo inicial se omite, no aporta contexto.
        val history = _messages.value.drop(1).map { msg ->
            mapOf("role" to if (msg.isUser) "user" else "model", "text" to msg.text)
        }

        _messages.value = _messages.value + ChatMessage(userText, true)
        _isLoading.value = true

        viewModelScope.launch {
            try {
                // Fetch real services context
                val availableServices = repository.getAllServices().first().take(15)
                val contextPrompt = buildContextPrompt(availableServices, lang)

                val fullPrompt = "$contextPrompt\n\nUsuario dice: $userText"

                val raw = repository.askMochi(fullPrompt, history)
                if (raw.isNullOrBlank()) {
                    _messages.value = _messages.value + ChatMessage(
                        if (lang == "en") "MochiBot is currently unavailable. Try again later."
                        else "MochiBot no está disponible por el momento. Intenta más tarde.",
                        false
                    )
                    return@launch
                }
                val (cleanText, ids) = extractRecommendedIds(raw)
                // Solo IDs que existen de verdad; descarta alucinaciones del modelo.
                val recommended = ids.mapNotNull { id -> availableServices.find { it.id == id } }
                _messages.value = _messages.value + ChatMessage(cleanText, false, recommended)
            } catch (e: Exception) {
                android.util.Log.e("MochiBot_Debug", "Error consultando askMochi", e)
                _messages.value = _messages.value + ChatMessage(
                    if (lang == "en") "MochiBot is currently unavailable. Try again later."
                    else "MochiBot no está disponible por el momento. Intenta más tarde.",
                    false
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun buildContextPrompt(services: List<ServiceFirestore>, lang: String): String {
        val servicesInfo = services.joinToString("\n") {
            "- [${it.id}] ${it.name} en ${it.location} por $${it.price} (${it.type}). Calificación: ${it.rating} estrellas."
        }
        
        val instructions = when(lang) {
            "en" -> """
                You are the Expert Assistant of Mochilapp. Your goal is to help the traveler plan their trip using EXCLUSIVELY the services available on our platform.
                
                INSTRUCTIONS:
                1. Be friendly, adventurous, and professional.
                2. Respond ALWAYS in ENGLISH.
                3. Prioritize services listed below based on rating and price.
                4. If a service is not listed, politely say it's not available in our network yet and suggest an alternative.
            """.trimIndent()
            "fr" -> """
                Vous êtes l'Assistant Expert de Mochilapp. Votre objectif est d'aider le voyageur à planifier son voyage en utilisant EXCLUSIVEMENT les services disponibles sur notre plateforme.
                
                INSTRUCTIONS:
                1. Soyez amical, aventureux et professionnel.
                2. Répondez TOUJOURS en FRANÇAIS.
                3. Priorisez les services listés ci-dessous en fonction de la note et du prix.
                4. Si un service n'est pas répertorié, dites poliment qu'il n'est pas encore disponible et suggérez une alternative.
            """.trimIndent()
            else -> """
                Eres el Asistente Experto de Mochilapp. Tu objetivo es ayudar al viajero a planificar su viaje usando EXCLUSIVAMENTE los servicios disponibles en nuestra plataforma.
                
                INSTRUCTIONS:
                1. Sé amable, aventurero y profesional.
                2. Responde SIEMPRE en ESPAÑOL.
                3. Prioriza los servicios listados arriba basándote en su calificación y precio.
                4. Si un servicio no está en la lista, dile amablemente que por ahora no está disponible en nuestra red pero sugiere una alternativa similar.
            """.trimIndent()
        }
        
        return """
            $instructions
            
            CURRENT AVAILABLE SERVICES:
            $servicesInfo

            5. Keep answers concise and easy to read on mobile.
            6. IMPORTANTE: al final, en una línea aparte, escribe los IDs de los servicios que recomendaste, exactamente con este formato: [IDS: id1, id2]. Usa únicamente IDs de la lista de arriba (máximo 3). Si no recomiendas ninguno, omite esa línea por completo.
        """.trimIndent()
    }

    // Extrae el marcador [IDS: ...] de la respuesta de Mochi y devuelve el
    // texto limpio (sin el marcador) junto con la lista de IDs recomendados.
    private fun extractRecommendedIds(text: String): Pair<String, List<String>> {
        val regex = Regex("\\[IDS:\\s*([^\\]]*)\\]", RegexOption.IGNORE_CASE)
        val ids = regex.find(text)
            ?.groupValues?.get(1)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        val clean = regex.replace(text, "").trim()
        return clean to ids
    }
}
