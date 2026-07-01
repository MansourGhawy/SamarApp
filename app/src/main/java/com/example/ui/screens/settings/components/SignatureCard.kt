package com.example.ui.screens.settings.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun SignatureCard() {
    val contextForSig = LocalContext.current
    val clipboardSigManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val sha1Fingerprint = remember {
        try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                contextForSig.packageManager.getPackageInfo(
                    contextForSig.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(android.content.pm.PackageManager.GET_SIGNATURES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                contextForSig.packageManager.getPackageInfo(
                    contextForSig.packageName,
                    android.content.pm.PackageManager.GET_SIGNATURES
                )
            }
            val signatures = @Suppress("DEPRECATION") packageInfo.signatures
            if (signatures != null && signatures.isNotEmpty()) {
                val md = java.security.MessageDigest.getInstance("SHA-1")
                val publicKey = md.digest(signatures[0].toByteArray())
                publicKey.joinToString(":") { String.format("%02X", it) }
            } else {
                contextForSig.getString(R.string.settings_signature_unavailable)
            }
        } catch (e: Exception) {
            contextForSig.getString(R.string.settings_signature_unavailable)
        }
    }
    val sha256Fingerprint = remember {
        try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                contextForSig.packageManager.getPackageInfo(
                    contextForSig.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(android.content.pm.PackageManager.GET_SIGNATURES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                contextForSig.packageManager.getPackageInfo(
                    contextForSig.packageName,
                    android.content.pm.PackageManager.GET_SIGNATURES
                )
            }
            val signatures = @Suppress("DEPRECATION") packageInfo.signatures
            if (signatures != null && signatures.isNotEmpty()) {
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val publicKey = md.digest(signatures[0].toByteArray())
                publicKey.joinToString(":") { String.format("%02X", it) }
            } else {
                contextForSig.getString(R.string.settings_signature_unavailable)
            }
        } catch (e: Exception) {
            contextForSig.getString(R.string.settings_signature_unavailable)
        }
    }

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.settings_signature_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_signature_desc),
                fontSize = 11.sp,
                color = Color(0xFF4B5563),
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f))

            Spacer(modifier = Modifier.height(8.dp))

            // SHA-1 field
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            clipboardSigManager.setText(AnnotatedString(sha1Fingerprint))
                            Toast.makeText(contextForSig, contextForSig.getString(R.string.settings_sha1_copied), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.settings_signature_desc_sha1),
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.settings_sha1_label),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF374151)
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = sha1Fingerprint,
                        fontSize = 10.sp,
                        color = Color(0xFF1F2937),
                        textAlign = TextAlign.Left,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // SHA-256 field
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            clipboardSigManager.setText(AnnotatedString(sha256Fingerprint))
                            Toast.makeText(contextForSig, contextForSig.getString(R.string.settings_sha256_copied), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.settings_signature_desc_sha256),
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.settings_sha256_label),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF374151)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = sha256Fingerprint,
                        fontSize = 10.sp,
                        color = Color(0xFF1F2937),
                        textAlign = TextAlign.Left,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
