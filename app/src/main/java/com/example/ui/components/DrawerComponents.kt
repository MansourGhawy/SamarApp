package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmeraldPrimary

import androidx.compose.material3.MaterialTheme

@Composable
fun DrawerItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val primaryColor = Color(0xFF4B36A2)
    val accentColor = Color(0xFF00B2FE)
    val backgroundLight = Color(0xFFF0F3FC)
    val textPrimary = Color(0xFF1E1A3E)

    val containerColor = if (selected) backgroundLight else Color.Transparent
    val fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium

    Surface(
        onClick = onClick,
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = fontWeight,
                    color = textPrimary
                )
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight(0.6f)
                        .align(Alignment.CenterEnd)
                        .background(
                            color = accentColor,
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}

@Composable
fun ContactIcon(
    icon: ImageVector,
    onClick: () -> Unit
) {
    val primaryColor = Color(0xFF4B36A2)
    val backgroundLight = Color(0xFFF0F3FC)

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(backgroundLight)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(18.dp)
        )
    }
}
