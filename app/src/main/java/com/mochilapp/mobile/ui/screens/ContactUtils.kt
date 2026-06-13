package com.mochilapp.mobile.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Verde oficial de WhatsApp.
private val WhatsAppGreen = Color(0xFF25D366)

/**
 * Abre WhatsApp (o su versión web como respaldo) con el número y un mensaje
 * pre-escrito. Normaliza números mexicanos de 10 dígitos anteponiendo el 52.
 */
fun openWhatsApp(context: Context, rawNumber: String, message: String) {
    val digits = rawNumber.filter { it.isDigit() }
    if (digits.isEmpty()) {
        Toast.makeText(context, "Esta empresa no tiene WhatsApp registrado", Toast.LENGTH_SHORT).show()
        return
    }
    // 10 dígitos = móvil MX sin lada de país; anteponemos 52.
    val normalized = if (digits.length == 10) "52$digits" else digits
    val url = "https://wa.me/$normalized?text=${Uri.encode(message)}"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo abrir WhatsApp", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Botón "Contactar por WhatsApp" reutilizable. Usa el WhatsApp si existe y
 * cae al teléfono como respaldo. No se muestra si no hay ningún número.
 */
@Composable
fun ContactCompanyButton(
    whatsapp: String,
    phone: String,
    message: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val number = whatsapp.ifBlank { phone }
    if (number.isBlank()) return

    Button(
        onClick = { openWhatsApp(context, number, message) },
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White)
    ) {
        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text("Contactar por WhatsApp", fontWeight = FontWeight.Bold)
    }
}
