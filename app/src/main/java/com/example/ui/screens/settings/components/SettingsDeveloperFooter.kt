package com.example.ui.screens.settings.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun SettingsDeveloperFooter(
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.settings_app_version),
            fontSize = 11.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_developer_info),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = Color(0xFF788282),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subtle centered Circular social rows
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Call Button
            IconButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:774004399"))
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = stringResource(R.string.settings_desc_call_support),
                    tint = Color(0xFF788282),
                    modifier = Modifier.size(16.dp)
                )
            }

            // WhatsApp Direct Chat Button
            IconButton(
                onClick = {
                    val waUrl = "https://wa.me/967774004399"
                    val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                    context.startActivity(waIntent)
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Gray.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = stringResource(R.string.settings_desc_whatsapp),
                    tint = Color(0xFF788282),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
