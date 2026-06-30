package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.navigation.Screen
import com.example.ui.helper.dialPhoneNumber
import com.example.ui.helper.openWhatsAppChat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigationDrawer(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    onBackupClick: () -> Unit,
    onCurrencyClick: () -> Unit,
    versionName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val supportPhoneNumber = stringResource(id = R.string.support_phone_number)
    
    ModalDrawerSheet(
        drawerContainerColor = Color(0xFFFFFFFF),
        modifier = modifier
            .fillMaxWidth(0.85f)
            .widthIn(max = 310.dp)
            .fillMaxHeight(),
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        // Header of Drawer
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF4B36A2)),
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Text(
                    text = stringResource(id = R.string.app_name_main),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Drawer Items Column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DrawerItem(
                selected = currentScreen == Screen.BUSINESS_PROFILE,
                icon = Icons.Default.People,
                label = stringResource(id = R.string.drawer_business_profile_label),
                onClick = { onScreenSelected(Screen.BUSINESS_PROFILE) }
            )
            
            DrawerItem(
                selected = false,
                icon = Icons.Default.Settings,
                label = stringResource(id = R.string.drawer_currency_label),
                onClick = onCurrencyClick
            )
            
            DrawerItem(
                selected = currentScreen == Screen.SECURITY,
                icon = Icons.Default.Lock,
                label = stringResource(id = R.string.drawer_security_label),
                onClick = { onScreenSelected(Screen.SECURITY) }
            )
            
            DrawerItem(
                selected = currentScreen == Screen.REPORTS,
                icon = Icons.Default.List,
                label = stringResource(id = R.string.drawer_reports_label),
                onClick = { onScreenSelected(Screen.REPORTS) }
            )
            
            DrawerItem(
                selected = currentScreen == Screen.TRASH,
                icon = Icons.Default.Delete,
                label = stringResource(id = R.string.drawer_trash_label),
                onClick = { onScreenSelected(Screen.TRASH) }
            )

            DrawerItem(
                selected = false,
                icon = Icons.Default.Refresh,
                label = stringResource(id = R.string.drawer_backup_label1),
                onClick = onBackupClick
            )
        }
        
        // Footer / Developer info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalDivider(color = Color(0xFFF0F3FC), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = stringResource(id = R.string.drawer_app_version, versionName),
                fontSize = 11.sp,
                color = Color(0xFF6B7280),
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = stringResource(id = R.string.developer_credit),
                fontSize = 11.sp,
                color = Color(0xFF6B7280),
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContactIcon(
                    icon = Icons.Default.Call,
                    onClick = {
                        dialPhoneNumber(context, supportPhoneNumber)
                    }
                )
                
                ContactIcon(
                    icon = Icons.Default.Share,
                    onClick = {
                        val msg = context.getString(R.string.whatsapp_contact_msg)
                        openWhatsAppChat(context, supportPhoneNumber, msg)
                    }
                )
            }
        }
    }
}

