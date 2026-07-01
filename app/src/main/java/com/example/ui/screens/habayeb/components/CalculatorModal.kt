package com.example.ui.screens.habayeb.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.domain.evaluateSimpleExpression

@Composable
fun CalculatorModal(
    onDismiss: () -> Unit,
    onConfirmExpression: (Double) -> Unit,
    activeThemeColor: Color,
    activeSubColor: Color
) {
    var rawExpression by remember { mutableStateOf("") }
    val resultPreview = remember(rawExpression) {
        if (rawExpression.isEmpty()) null
        else evaluateSimpleExpression(rawExpression)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = activeThemeColor), // Deep Purple theme
            modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top header of Calculator
                Text(stringResource(id = R.string.habayeb_calc_title), color = activeSubColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                // Formula Monitor screen
                Card(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E0F34)),
                    border = BorderStroke(1.dp, Color(0xFF9333EA))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = rawExpression.ifEmpty { "0" },
                            fontSize = 15.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (resultPreview != null) {
                            Text(
                                text = "= ${resultPreview.toInt()}",
                                fontSize = 13.sp,
                                color = Color(0xFF86EFAC),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Layout of Keys
                val keysRow1 = listOf("÷", "9", "8", "7")
                val keysRow2 = listOf("×", "6", "5", "4")
                val keysRow3 = listOf("-", "3", "2", "1")
                val keysRow4 = listOf("+", "00", "0", ".")
                val keysRow5 = listOf("=", "C", "⌫")

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(keysRow1, keysRow2, keysRow3, keysRow4, keysRow5).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { char ->
                                val buttonColor = when (char) {
                                    "÷", "×", "-", "+" -> Color(0xFF9333EA)
                                    "=" -> Color(0xFF059669)
                                    "C", "⌫" -> Color(0xFFEF4444)
                                    else -> Color.White.copy(alpha = 0.1f)
                                }
                                val textColor = Color.White

                                Box(
                                    modifier = Modifier
                                        .weight(if (char == "=" && row.size == 3) 1.2f else 1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(buttonColor)
                                        .clickable {
                                            when (char) {
                                                "=" -> {
                                                    val finalRes = evaluateSimpleExpression(rawExpression)
                                                    if (finalRes != null) {
                                                        rawExpression = if (finalRes % 1.0 == 0.0) finalRes.toInt().toString() else finalRes.toString()
                                                    }
                                                }
                                                "C" -> rawExpression = ""
                                                "⌫" -> if (rawExpression.isNotEmpty()) rawExpression = rawExpression.dropLast(1)
                                                else -> rawExpression += char
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(char, fontSize = if (char.length > 1) 13.sp else 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Confirm and carry forward buttons
                Button(
                    onClick = {
                        val finalValue = evaluateSimpleExpression(rawExpression) ?: rawExpression.toDoubleOrNull() ?: 0.0
                        onConfirmExpression(finalValue)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF7B54)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(stringResource(id = R.string.habayeb_calc_confirm), fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
    }
}
