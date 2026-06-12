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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.mochilapp.mobile.R
import com.mochilapp.mobile.ui.theme.t
import com.mochilapp.mobile.ui.viewmodels.AuthViewModel

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: (String) -> Unit,
    onNavigateToRegistration: () -> Unit
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoginSelected by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    val isLoading by authViewModel.isLoading.collectAsState()

    // Google Sign-In Setup
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { token ->
                authViewModel.signInWithGoogle(
                    idToken = token,
                    onSuccess = onLoginSuccess,
                    onError = { error = it }
                )
            }
        } catch (e: ApiException) {
            error = "Google Sign-In failed: ${e.message}"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Top Image with Gradient (Playa del Caribe)
        Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            AsyncImage(
                model = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?q=80&w=2000&auto=format&fit=crop", 
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.White),
                            startY = 350f
                        )
                    )
            )
            
            // Brand Tag
            Surface(
                modifier = Modifier
                    .padding(24.dp)
                    .align(Alignment.TopStart),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.9f),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logocolor),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Mochilapp",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(240.dp))

            // Title
            Text(
                text = t("welcome"),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp
                ),
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = t("search_title"),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Auth Toggle Switch (Premium Design)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF1F3F5)
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isLoginSelected) Color.White else Color.Transparent,
                        shadowElevation = if (isLoginSelected) 6.dp else 0.dp,
                        onClick = { isLoginSelected = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = t("login"),
                                fontWeight = FontWeight.Bold,
                                color = if (isLoginSelected) Color(0xFF007BFF) else Color.Gray
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (!isLoginSelected) Color.White else Color.Transparent,
                        shadowElevation = if (!isLoginSelected) 6.dp else 0.dp,
                        onClick = { 
                            isLoginSelected = false
                            onNavigateToRegistration() 
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = t("register"),
                                fontWeight = FontWeight.Bold,
                                color = if (!isLoginSelected) Color(0xFF007BFF) else Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Form
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = t("email"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)) },
                    placeholder = { Text(t("email"), color = Color.LightGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF007BFF),
                        unfocusedBorderColor = Color(0xFFE9ECEF)
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(text = t("password"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    placeholder = { Text("........", color = Color.LightGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF007BFF),
                        unfocusedBorderColor = Color(0xFFE9ECEF)
                    )
                )

                if (error != null) {
                    Text(text = error!!, color = Color.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                
                if (infoMessage != null) {
                    Text(text = infoMessage!!, color = Color(0xFF28A745), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }

                TextButton(
                    onClick = { 
                        if (email.isNotBlank()) {
                            authViewModel.resetPassword(email, { infoMessage = "Correo enviado" }, { error = it })
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(t("cancel"), color = Color(0xFF007BFF), fontSize = 13.sp, fontWeight = FontWeight.Bold) // Using 'cancel' for testing
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Action Button (Improved Shading and Shape)
            Button(
                onClick = { 
                    authViewModel.login(email, password, onLoginSuccess, { error = it })
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .shadow(12.dp, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007BFF)),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 2.dp
                )
            ) {
                Text(t("continue"), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Social Login Divider
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE9ECEF))
                Text(
                    text = " O continúa con ",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE9ECEF))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Google Sign-In
            OutlinedButton(
                onClick = { googleLauncher.launch(googleSignInClient.signInIntent) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, Color(0xFFE9ECEF))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(id = R.drawable.ic_google_logo), contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Unspecified)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Continuar con Google", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Legal Text
            Text(
                text = buildAnnotatedString {
                    append("Al continuar, aceptas nuestros ")
                    pushStringAnnotation("terms", "https://...")
                    withStyle(SpanStyle(color = Color.Black, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                        append("Términos de Servicio")
                    }
                    pop()
                    append(" y ")
                    pushStringAnnotation("policy", "https://...")
                    withStyle(SpanStyle(color = Color.Black, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                        append("Política de Privacidad")
                    }
                    pop()
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}
