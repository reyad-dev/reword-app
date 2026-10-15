package com.reword.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reword.app.ui.theme.*

@Composable
fun ConnectScreen(
    initialSyncId: String = "",
    initialWorkerUrl: String = "https://reword-vault-sync.moasaar6.workers.dev",
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onConnect: (syncId: String, workerUrl: String, masterPassword: String) -> Unit
) {
    var syncId by remember { mutableStateOf(initialSyncId) }
    var workerUrl by remember { mutableStateOf(initialWorkerUrl) }
    var masterPassword by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Logo Icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(PrimaryViolet, AccentCyan)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "ربط خزينة ReWord",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "أدخل بيانات المزامنة وكلمة المرور الرئيسية لسحب الخزينة المشفرة وتفعيل الملء التلقائي.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Sync ID Field
        OutlinedTextField(
            value = syncId,
            onValueChange = { syncId = it },
            label = { Text("معرف المزامنة (Sync ID)") },
            leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null, tint = AccentCyan) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = BorderDark,
                focusedLabelColor = AccentCyan,
                unfocusedLabelColor = TextMuted,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Worker URL Field
        OutlinedTextField(
            value = workerUrl,
            onValueChange = { workerUrl = it },
            label = { Text("رابط خادم التزامن (Worker URL)") },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = AccentCyan) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentCyan,
                unfocusedBorderColor = BorderDark,
                focusedLabelColor = AccentCyan,
                unfocusedLabelColor = TextMuted,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Master Password Field
        OutlinedTextField(
            value = masterPassword,
            onValueChange = { masterPassword = it },
            label = { Text("كلمة المرور الرئيسية (Master Password)") },
            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = PrimaryViolet) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryViolet,
                unfocusedBorderColor = BorderDark,
                focusedLabelColor = PrimaryViolet,
                unfocusedLabelColor = TextMuted,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard
            )
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = errorMessage,
                color = ErrorRose,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Connect Button
        Button(
            onClick = { onConnect(syncId, workerUrl, masterPassword) },
            enabled = !isLoading && syncId.isNotBlank() && workerUrl.isNotBlank() && masterPassword.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryViolet)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = "مزامنة وفك تشفير الخزينة",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
