package com.mochilapp.mobile.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.repository.FirebaseRepository
import com.mochilapp.mobile.ui.theme.Translations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

// Usamos gemini-1.5-flash-latest como modelo de alta compatibilidad
private const val MOCHIBOT_MODEL = "gemini-1.5-flash-latest"

class AiViewModel(
    private val repository: FirebaseRepository,
    private val apiKey: String
) : ViewModel() {
    // Usamos el modelo centralizado
    private val generativeModel = GenerativeModel(
        modelName = MOCHIBOT_MODEL,
        apiKey = apiKey
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage("¡Hola! Soy tu asistente inteligente de Mochilapp. Puedo ayudarte a encontrar los mejores tours, hoteles y planificar tu viaje basándome en los servicios reales disponibles. ¿A dónde quieres ir?", false)
    ))
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val chat = generativeModel.startChat()

    fun sendMessage(userText: String, lang: String = "es") {
        if (userText.isBlank()) return

        _messages.value = _messages.value + ChatMessage(userText, true)
        _isLoading.value = true

        // Log de diagnóstico seguro
        android.util.Log.d("MochiBot_Debug", "Enviando mensaje. API Key presente: ${apiKey.isNotBlank()}. Modelo: $MOCHIBOT_MODEL. Idioma: $lang")

        viewModelScope.launch {
            try {
                if (apiKey.isBlank() || apiKey == "MOCK_API_KEY") {
                    android.util.Log.e("MochiBot_Debug", "Error: API Key faltante o inválida.")
                    _messages.value = _messages.value + ChatMessage(Translations.getString("ai_maintenance_msg", lang), false)
                    return@launch
                }

                // Fetch real services context
                val availableServices = repository.getAllServices().first().take(15)
                val contextPrompt = buildContextPrompt(availableServices, lang)
                
                val fullPrompt = "$contextPrompt\n\nUsuario dice: $userText"
                
                val response = chat.sendMessage(fullPrompt)
                _messages.value = _messages.value + ChatMessage(response.text ?: "No recibí una respuesta clara de la IA.", false)
            } catch (e: Exception) {
                // Registro detallado en Logcat para depuración
                val errorType = e.javaClass.simpleName
                val errorMsg = e.message ?: "Sin mensaje"
                android.util.Log.e("MochiBot_Debug", "Error real de Gemini ($errorType): $errorMsg")
                
                // Análisis de causas comunes
                when {
                    errorMsg.contains("403") -> android.util.Log.e("MochiBot_Debug", "Causa: API Key restringida o sin permisos para Generative AI.")
                    errorMsg.contains("404") -> android.util.Log.e("MochiBot_Debug", "Causa: Modelo '$MOCHIBOT_MODEL' no encontrado en esta región.")
                    errorMsg.contains("DEVELOPER_ERROR") -> android.util.Log.e("MochiBot_Debug", "Causa: Problema de configuración de Google Play Services o SHA-1.")
                }
                
                // Mostrar mensaje amigable al usuario
                _messages.value = _messages.value + ChatMessage(if(lang == "en") "MochiBot is currently unavailable. Try again later." else "MochiBot no está disponible por el momento. Intenta más tarde.", false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun buildContextPrompt(services: List<ServiceFirestore>, lang: String): String {
        val servicesInfo = services.joinToString("\n") { 
            "- ${it.name} en ${it.location} por $${it.price} (${it.type}). Calificación: ${it.rating} estrellas."
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
        """.trimIndent()
    }
}
