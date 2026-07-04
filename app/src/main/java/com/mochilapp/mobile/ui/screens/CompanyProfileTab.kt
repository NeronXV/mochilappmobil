package com.mochilapp.mobile.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mochilapp.mobile.ui.theme.serviceTypeLabel
import com.mochilapp.mobile.ui.theme.t
import com.mochilapp.mobile.ui.viewmodels.AuthViewModel

// Pestaña de perfil del panel de empresa: identidad comercial, verificación
// y datos de contacto editables sin salir de la app
@Composable
fun CompanyProfileTab(
    authViewModel: AuthViewModel,
    onLogout: () -> Unit
) {
    val userProfile by authViewModel.userProfile.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val companyTeal = MaterialTheme.colorScheme.tertiary

    var showEditDialog by remember { mutableStateOf(false) }

    fun toast(msg: String) =
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            authViewModel.updateProfileImage(
                uri = it,
                onSuccess = { toast("Logo actualizado") },
                onError = { error -> toast(error) }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo del negocio (toca para cambiar)
        Box {
            Surface(
                modifier = Modifier.size(100.dp).clickable { imageLauncher.launch("image/*") },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                if (userProfile?.profileImageUrl?.isNotEmpty() == true) {
                    AsyncImage(
                        model = userProfile?.profileImageUrl,
                        contentDescription = "Logo del negocio",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.clip(CircleShape)
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            userProfile?.name?.take(1)?.uppercase() ?: "C",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            Surface(
                color = companyTeal,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomEnd).size(30.dp)
            ) {
                Icon(
                    Icons.Default.PhotoCamera,
                    contentDescription = "Cambiar logo",
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            userProfile?.name ?: "Tu Empresa",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            serviceTypeLabel(userProfile?.companyType?.takeIf { it != "NONE" } ?: "OTHER"),
            color = Color.Gray,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(8.dp))
        val isVerified = userProfile?.businessVerified == true
        Surface(
            color = if (isVerified) Color(0xFFD4EFDF) else Color(0xFFFDEBD0),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isVerified) Icons.Default.CheckCircle else Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isVerified) Color(0xFF27AE60) else Color(0xFFD68910)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isVerified) "NEGOCIO VERIFICADO" else "VERIFICACIÓN EN REVISIÓN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isVerified) Color(0xFF27AE60) else Color(0xFFD68910)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Datos fiscales / verificación
        ProfileSectionCard(title = "Verificación del Negocio", icon = Icons.Default.VerifiedUser) {
            ProfileInfoRow(Icons.Default.Badge, "RFC", userProfile?.rfc?.ifEmpty { "No registrado" } ?: "No registrado")
            ProfileInfoRow(Icons.Default.CardTravel, "RNT", userProfile?.rnt?.ifEmpty { "No registrado" } ?: "No registrado")
            if (!isVerified) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "El equipo de Mochilapp revisa tus datos fiscales; al aprobarse, tu negocio mostrará la insignia de verificado a los viajeros.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Información comercial editable
        ProfileSectionCard(
            title = "Información del Negocio",
            icon = Icons.Default.Storefront,
            action = {
                TextButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Editar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        ) {
            val description = userProfile?.businessDescription.orEmpty()
            if (description.isNotEmpty()) {
                Text(description, fontSize = 12.sp, color = Color.DarkGray, lineHeight = 17.sp)
                Spacer(Modifier.height(8.dp))
            }
            ProfileInfoRow(Icons.Default.Email, "Correo", userProfile?.email.orEmpty())
            ProfileInfoRow(Icons.Default.Phone, "Teléfono", userProfile?.phone?.ifEmpty { "Sin registrar" } ?: "Sin registrar")
            ProfileInfoRow(Icons.Default.Chat, "WhatsApp", userProfile?.whatsapp?.ifEmpty { "Sin registrar" } ?: "Sin registrar")
            ProfileInfoRow(
                Icons.Default.Place,
                "Ubicación",
                userProfile?.businessLocation?.takeIf { it.isNotEmpty() }?.let { displayLocation(it) } ?: "Sin registrar"
            )
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(t("logout"), fontWeight = FontWeight.Bold)
        }

        // Acceso legado al panel web mientras termina la migración
        TextButton(
            onClick = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://mochilapp-2c777.web.app?app=business")
                )
                context.startActivity(intent)
            }
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(t("company_web_panel"), fontSize = 12.sp, color = Color.Gray)
        }

        Spacer(Modifier.height(80.dp))
    }

    if (showEditDialog) {
        BusinessInfoDialog(
            initialName = userProfile?.name.orEmpty(),
            initialDescription = userProfile?.businessDescription.orEmpty(),
            initialPhone = userProfile?.phone.orEmpty(),
            initialWhatsapp = userProfile?.whatsapp.orEmpty(),
            initialLocation = userProfile?.businessLocation.orEmpty(),
            initialLat = userProfile?.businessLat ?: 0.0,
            initialLng = userProfile?.businessLng ?: 0.0,
            isLoading = isLoading,
            onSave = { name, description, phone, whatsapp, location, lat, lng ->
                authViewModel.updateBusinessProfile(
                    name = name,
                    businessDescription = description,
                    phone = phone,
                    whatsapp = whatsapp,
                    businessLocation = location,
                    businessLat = lat,
                    businessLng = lng,
                    onSuccess = {
                        toast("Información actualizada")
                        showEditDialog = false
                    },
                    onError = { error -> toast(error) }
                )
            },
            onDismiss = { showEditDialog = false }
        )
    }
}

@Composable
private fun ProfileSectionCard(
    title: String,
    icon: ImageVector,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun ProfileInfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Gray, letterSpacing = 1.sp)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BusinessInfoDialog(
    initialName: String,
    initialDescription: String,
    initialPhone: String,
    initialWhatsapp: String,
    initialLocation: String,
    initialLat: Double,
    initialLng: Double,
    isLoading: Boolean,
    onSave: (name: String, description: String, phone: String, whatsapp: String, location: String, lat: Double, lng: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var phone by remember { mutableStateOf(initialPhone) }
    var whatsapp by remember { mutableStateOf(initialWhatsapp) }
    var location by remember { mutableStateOf(initialLocation) }
    // Pin del negocio: antes solo se podía fijar en el registro; un pin mal
    // puesto quedaba congelado para siempre y precargaba mal cada servicio
    var lat by remember { mutableStateOf(initialLat) }
    var lng by remember { mutableStateOf(initialLng) }
    var showMapPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Información del negocio", fontWeight = FontWeight.Black) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre comercial") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción del negocio") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = whatsapp,
                    onValueChange = { whatsapp = it },
                    label = { Text("WhatsApp") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Ubicación del negocio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val hasPin = lat != 0.0 || lng != 0.0
                OutlinedButton(
                    onClick = { showMapPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (hasPin) Icons.Default.CheckCircle else Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (hasPin) "Ajustar pin en el mapa" else "Ubicar mi negocio en el mapa")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), description.trim(), phone.trim(), whatsapp.trim(), location.trim(), lat, lng) },
                enabled = name.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Guardar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )

    if (showMapPicker) {
        LocationPickerDialog(
            initialLat = lat,
            initialLng = lng,
            title = "Ubica tu negocio",
            hint = "Toca el mapa donde está tu negocio",
            onPick = { newLat, newLng ->
                lat = newLat
                lng = newLng
            },
            onDismiss = { showMapPicker = false }
        )
    }
}
