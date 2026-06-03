package com.mochilapp.mobile.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.mochilapp.mobile.data.ServiceFirestore
import com.mochilapp.mobile.repository.FirebaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

class AiViewModel(
    private val repository: FirebaseRepository,
    private val apiKey: String
) : ViewModel() {
    // Cambiamos al modelo 'gemini-1.5-flash', más rápido y compatible
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(listOf(
        ChatMessage("¡Hola! Soy tu asistente inteligente de Mochilapp. Puedo ayudarte a encontrar los mejores tours, hoteles y planificar tu viaje basándome en los servicios reales disponibles. ¿A dónde quieres ir?", false)
    ))
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val chat = generativeModel.startChat()

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        _messages.value = _messages.value + ChatMessage(userText, true)
        _isLoading.value = true

        viewModelScope.launch {
            try {
                if (apiKey.isBlank() || apiKey == "MOCK_API_KEY") {
                    _messages.value = _messages.value + ChatMessage("¡Hola! Parece que aún no has configurado tu GEMINI_API_KEY en el archivo local.properties. Por favor, añádela y sincroniza el proyecto para que pueda ayudarte.", false)
                    return@launch
                }

                // Fetch real services context
                val availableServices = repository.getAllServices().first().take(15)
                val contextPrompt = buildContextPrompt(availableServices)
                
                val fullPrompt = "$contextPrompt\n\nUsuario dice: $userText"
                
                val response = chat.sendMessage(fullPrompt)
                _messages.value = _messages.value + ChatMessage(response.text ?: "No recibí una respuesta clara de la IA.", false)
            } catch (e: Exception) {
                // Registrar el error técnico solo en Logcat para el desarrollador
                android.util.Log.e("AiViewModel", "Gemini Error: ${e.message}", e)
                
                // Mostrar mensaje amigable al usuario sin detalles técnicos
                _messages.value = _messages.value + ChatMessage("MochiBot no está disponible por el momento. Intenta más tarde.", false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun buildContextPrompt(services: List<ServiceFirestore>): String {
        val servicesInfo = services.joinToString("\n") { 
            "- ${it.name} en ${it.location} por $${it.price} (${it.type}). Calificación: ${it.rating} estrellas."
        }
        
        return """
            Eres el Asistente Experto de Mochilapp. Tu objetivo es ayudar al viajero a planificar su viaje usando EXCLUSIVAMENTE los servicios disponibles en nuestra plataforma.
            
            SERVICIOS ACTUALES DISPONIBLES:
            $servicesInfo
            
            INSTRUCCIONES:
            1. Sé amable, aventurero y profesional.
            2. Si el usuario pregunta por recomendaciones, prioriza los servicios listados arriba basándote en su calificación y precio.
            3. Si un servicio no está en la lista, dile amablemente que por ahora no está disponible en nuestra red pero sugiere una alternativa similar de la lista.
            4. Ayuda a calcular presupuestos si el usuario te da un monto.
            5. Mantén tus respuestas concisas y fáciles de leer en un móvil.
        """.trimIndent()
    }
}
