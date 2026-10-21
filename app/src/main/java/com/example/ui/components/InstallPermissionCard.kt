package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberCardBorder
import com.example.ui.theme.CyberPrimary
import com.example.ui.theme.CyberSecondary
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberWarning

@Composable
fun InstallPermissionCard(
    hasPermission: Boolean,
    onCheckPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (hasPermission) CyberSurfaceVariant else CyberSurfaceVariant)
            .border(
                1.dp,
                if (hasPermission) CyberSecondary.copy(alpha = 0.5f) else CyberWarning.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (hasPermission) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                contentDescription = "Security Status",
                tint = if (hasPermission) CyberSecondary else CyberWarning,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasPermission) "Unknown App Installation Allowed" else "Install Permission Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (hasPermission) CyberSecondary else CyberWarning
                )
                Text(
                    text = if (hasPermission)
                        "APK Builder is authorized to trigger system app installations."
                    else
                        "Required by Android to install APKs built directly on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        if (!hasPermission) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = { onCheckPermission() },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag("check_permission_btn")
                ) {
                    Text("Recheck", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        openUnknownAppSourcesSettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberWarning),
                    modifier = Modifier.testTag("grant_install_permission_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.surface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Enable in Settings",
                        color = MaterialTheme.colorScheme.surface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

fun openUnknownAppSourcesSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SECURITY_SETTINGS)
            context.startActivity(fallback)
        }
    }
}
