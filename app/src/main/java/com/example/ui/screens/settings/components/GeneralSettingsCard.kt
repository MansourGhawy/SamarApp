package com.example.ui.screens.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
fun GeneralSettingsCard(
    currencySymbol: String,
    onCurrencySymbolChange: (String) -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = stringResource(R.string.settings_currency_title),
                fontWeight = FontWeight.Bold,
                color = Color(0xFF075E54),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = currencySymbol,
                onValueChange = onCurrencySymbolChange,
                label = { Text(stringResource(R.string.settings_currency_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
            )
        }
    }
}
