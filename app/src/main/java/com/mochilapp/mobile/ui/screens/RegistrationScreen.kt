package com.mochilapp.mobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochilapp.mobile.R
import com.mochilapp.mobile.data.CompanyType
import com.mochilapp.mobile.ui.theme.t
import com.mochilapp.mobile.ui.viewmodels.AuthViewModel

@Composable
fun RegistrationScreen(
    authViewModel: AuthViewModel,
    onRegistrationSuccess: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    // Basic Info
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("TRAVELER") }
    
    // Business Info (COMPANY only)
    var businessName by remember { mutableStateOf("") }
    var businessDescription by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var businessLocation by remember { mutableStateOf("") }
    var rfc by remember { mutableStateOf("") }
    var rnt by remember { mutableStateOf("") }
    var companyType by remember { mutableStateOf(CompanyType.HOTEL) }
    
    // Step Specific (Optional)
    var checkIn by remember { mutableStateOf("") }
    var checkOut by remember { mutableStateOf("") }
    var meetingPoint by remember { mutableStateOf("") }

    var currentStep by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val isLoading by authViewModel.isLoading.collectAsState()
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Background Image
        Image(
            painter = painterResource(id = R.drawable.placeholder),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(200.dp),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.White),
                        startY = 100f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(160.dp))
            
            Text(
                text = t("create_account"),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = Color.Black
            )
            
            if (role == "COMPANY") {
                Text(
                    text = "Paso ${currentStep + 1} de 3",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (currentStep == 0) {
                // Step 0: Account Basics
                Text(text = t("welcome"), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start)) // Reusing welcome
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t("name")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(t("email")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(t("password")) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                Text(t("profile_type"), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = role == "TRAVELER", onClick = { role = "TRAVELER"; currentStep = 0 })
                    Text(t("role_traveler"))
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = role == "COMPANY", onClick = { role = "COMPANY" })
                    Text(t("role_company"))
                }
            } else if (currentStep == 1 && role == "COMPANY") {
                // Step 1: Business Data
                Text(text = "Datos del negocio", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Nombre comercial") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = businessLocation,
                    onValueChange = { businessLocation = it },
                    label = { Text("Ubicación del negocio") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Teléfono") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = whatsapp,
                        onValueChange = { whatsapp = it },
                        label = { Text("WhatsApp") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = businessDescription,
                    onValueChange = { businessDescription = it },
                    label = { Text("Descripción del negocio") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Datos fiscales para la verificación de la empresa
                Text(text = "Datos de verificación", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Mochilapp valida cada empresa para dar confianza a los viajeros. Tu RFC es obligatorio; el RNT (Registro Nacional de Turismo) suma aún más credibilidad.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = rfc,
                    onValueChange = { rfc = it.uppercase().take(13) },
                    label = { Text("RFC (obligatorio)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    supportingText = { Text("12 o 13 caracteres", fontSize = 11.sp) }
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = rnt,
                    onValueChange = { rnt = it.uppercase() },
                    label = { Text("RNT (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Verified, contentDescription = null) },
                    supportingText = { Text("Registro Nacional de Turismo", fontSize = 11.sp) }
                )
            } else if (currentStep == 2 && role == "COMPANY") {
                // Step 2: Specialization
                Text(text = "Especialización", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(16.dp))
                
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Tipo: ${companyType.name}")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        CompanyType.entries.filter { it != CompanyType.NONE }.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    companyType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Specific fields based on type
                if (companyType == CompanyType.HOTEL || companyType == CompanyType.HOSTEL) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = checkIn,
                            onValueChange = { checkIn = it },
                            label = { Text("Check-in") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = checkOut,
                            onValueChange = { checkOut = it },
                            label = { Text("Check-out") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                } else if (companyType == CompanyType.BOAT_TOUR || companyType == CompanyType.TOUR_AGENCY) {
                    OutlinedTextField(
                        value = meetingPoint,
                        onValueChange = { meetingPoint = it },
                        label = { Text("Punto de encuentro") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F1FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Tu cuenta de prestador será revisada por Mochilapp antes de aparecer públicamente.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF0056B3)
                    )
                }
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = error!!, color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Navigation Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (currentStep > 0) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Atrás")
                    }
                }
                
                val isLastStep = (role == "TRAVELER") || (role == "COMPANY" && currentStep == 2)
                
                Button(
                    onClick = {
                        if (!isLastStep) {
                            // Validation before next step
                            if (currentStep == 0 && (name.isBlank() || email.isBlank() || password.isBlank())) {
                                error = "Completa todos los campos básicos"
                            } else if (currentStep == 1 && (businessName.isBlank() || businessLocation.isBlank())) {
                                error = "El nombre y ubicación del negocio son obligatorios"
                            } else if (currentStep == 1 && rfc.length !in 12..13) {
                                error = "El RFC debe tener 12 o 13 caracteres"
                            } else {
                                error = null
                                currentStep++
                            }
                        } else {
                            // Final Registration
                            authViewModel.register(
                                name = name,
                                email = email,
                                pass = password,
                                role = role,
                                companyType = if (role == "COMPANY") companyType.name else "NONE",
                                businessName = businessName,
                                businessDescription = businessDescription,
                                phone = phone,
                                whatsapp = whatsapp.ifBlank { phone },
                                businessLocation = businessLocation,
                                rfc = rfc,
                                rnt = rnt,
                                checkIn = checkIn,
                                checkOut = checkOut,
                                meetingPoint = meetingPoint,
                                onSuccess = onRegistrationSuccess,
                                onError = { error = it }
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(if (isLastStep) t("register") else "Siguiente", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (currentStep == 0) {
                TextButton(onClick = onNavigateToLogin, modifier = Modifier.padding(top = 16.dp)) {
                    Text(t("already_have_account"), fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }

        if (isLoading && currentStep < 0) { // Fallback loading
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
