package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.*
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CalculatorDialog(
    onDismiss: () -> Unit,
    onValueConfirmed: (Double) -> Unit
) {
    var displayStr by remember { mutableStateOf("0") }
    var firstNumber by remember { mutableStateOf<BigDecimal?>(null) }
    var pendingOperator by remember { mutableStateOf<String?>(null) }
    var resetInputOnNextDigit by remember { mutableStateOf(false) }

    fun handleDigit(digit: String) {
        if (displayStr == "0" || resetInputOnNextDigit) {
            displayStr = digit
            resetInputOnNextDigit = false
        } else {
            displayStr += digit
        }
    }

    fun handleOperator(op: String) {
        try {
            val currentValue = BigDecimal(displayStr)
            if (firstNumber != null && pendingOperator != null) {
                // intermediate calculation
                val res = when (pendingOperator) {
                    "+" -> firstNumber!!.add(currentValue)
                    "-" -> firstNumber!!.subtract(currentValue)
                    "×" -> firstNumber!!.multiply(currentValue)
                    "÷" -> {
                        if (currentValue.compareTo(BigDecimal.ZERO) == 0) {
                            BigDecimal.ZERO
                        } else {
                            firstNumber!!.divide(currentValue, 6, RoundingMode.HALF_EVEN)
                        }
                    }
                    else -> currentValue
                }
                firstNumber = res
                displayStr = res.stripTrailingZeros().toPlainString()
            } else {
                firstNumber = currentValue
            }
            pendingOperator = op
            resetInputOnNextDigit = true
        } catch (e: Exception) {
            displayStr = "خطأ"
            resetInputOnNextDigit = true
        }
    }

    fun evaluate() {
        val op = pendingOperator ?: return
        val first = firstNumber ?: return
        try {
            val second = BigDecimal(displayStr)
            val res = when (op) {
                "+" -> first.add(second)
                "-" -> first.subtract(second)
                "×" -> first.multiply(second)
                "÷" -> {
                    if (second.compareTo(BigDecimal.ZERO) == 0) {
                        BigDecimal.ZERO
                    } else {
                        first.divide(second, 6, RoundingMode.HALF_EVEN)
                    }
                }
                else -> second
            }
            displayStr = res.stripTrailingZeros().toPlainString()
            firstNumber = null
            pendingOperator = null
            resetInputOnNextDigit = true
        } catch (e: Exception) {
            displayStr = "خطأ"
            resetInputOnNextDigit = true
        }
    }

    fun handleClear() {
        displayStr = "0"
        firstNumber = null
        pendingOperator = null
        resetInputOnNextDigit = false
    }

    fun handleBackspace() {
        if (displayStr.length > 1) {
            displayStr = displayStr.dropLast(1)
        } else {
            displayStr = "0"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        // Pop-up container with a bouncy spring animation entry
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = fadeOut() + scaleOut()
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = CoralAccent)
                        }

                        Text(
                            text = "آلة حاسبة ذكية 🧮",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Digital Display Screen
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFE9ECE9))
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            if (firstNumber != null && pendingOperator != null) {
                                Text(
                                    text = "${firstNumber!!.stripTrailingZeros().toPlainString()} $pendingOperator",
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Right
                                )
                            }
                            Text(
                                text = displayStr,
                                color = Color(0xFF1B2F1C),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Customized Keyboard Grid (Right Thumb Friendly: operations on the RIGHT in LTR)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "7", textColor = Color.White) { handleDigit("7") } }
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "8", textColor = Color.White) { handleDigit("8") } }
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "9", textColor = Color.White) { handleDigit("9") } }
                                Box(modifier = Modifier.weight(1f)) {
                                    Button(
                                        onClick = { handleBackspace() },
                                        contentPadding = PaddingValues(0.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth().height(50.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "تراجع", tint = Color.White)
                                    }
                                }
                            }
                            // Row 2
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "4", textColor = Color.White) { handleDigit("4") } }
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "5", textColor = Color.White) { handleDigit("5") } }
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "6", textColor = Color.White) { handleDigit("6") } }
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "×", color = Color(0xFF6366F1), textColor = Color.White) { handleOperator("×") } }
                            }
                            // Row 3
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "1", textColor = Color.White) { handleDigit("1") } }
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "2", textColor = Color.White) { handleDigit("2") } }
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "3", textColor = Color.White) { handleDigit("3") } }
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "-", color = Color(0xFF6366F1), textColor = Color.White) { handleOperator("-") } }
                            }
                            // Row 4
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = ".", textColor = Color.White) { handleDigit(".") } }
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "0", textColor = Color.White) { handleDigit("0") } }
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "00", textColor = Color.White) { handleDigit("00") } }
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "+", color = Color(0xFF6366F1), textColor = Color.White) { handleOperator("+") } }
                            }
                            // Row 5
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(2f)) {
                                    Button(
                                        onClick = { 
                                            val dVal = displayStr.toDoubleOrNull() ?: 0.0
                                            onValueConfirmed(dVal) 
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth().height(50.dp)
                                    ) {
                                        Text("تأكيد ✔️", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "÷", color = Color(0xFF6366F1), textColor = Color.White) { handleOperator("÷") } }
                                Box(modifier = Modifier.weight(1f)) { CalcButton(text = "=", color = SoftGreen, textColor = Color.White) { evaluate() } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalcButton(
    text: String,
    color: Color = Color.Gray.copy(alpha = 0.1f),
    textColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = if (textColor == Color.Unspecified) MaterialTheme.colorScheme.onBackground else textColor
        ),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
