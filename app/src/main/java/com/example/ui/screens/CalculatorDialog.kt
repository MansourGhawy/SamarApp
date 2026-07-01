package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.domain.evaluateSimpleExpression

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CalculatorDialog(
    onDismiss: () -> Unit,
    onValueConfirmed: (Double) -> Unit,
    activeThemeColor: Color? = null,
    activeSubColor: Color? = null
) {
    var rawExpression by remember { mutableStateOf("") }
    
    // Fallback to app's primary theme color dynamically
    val brandPrimary = activeThemeColor ?: MaterialTheme.colorScheme.primary
    val brandSub = activeSubColor ?: MaterialTheme.colorScheme.primaryContainer

    // Evaluate preview in real-time
    val resultPreview = remember(rawExpression) {
        if (rawExpression.isEmpty()) null
        else evaluateSimpleExpression(rawExpression)
    }

    val haptic = LocalHapticFeedback.current

    fun performClickFeedback() {
        try {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (e: Exception) {
            // Ignore in environment without active haptic device
        }
    }

    fun handleDigit(digit: String) {
        performClickFeedback()
        if (rawExpression == "0") {
            rawExpression = digit
        } else {
            rawExpression += digit
        }
    }

    fun handleOperator(op: String) {
        performClickFeedback()
        if (rawExpression.isEmpty()) {
            if (op == "-") {
                rawExpression = "-"
            }
            return
        }
        val lastChar = rawExpression.last()
        if (lastChar in listOf('+', '-', '×', '÷')) {
            rawExpression = rawExpression.dropLast(1) + op
        } else {
            rawExpression += op
        }
    }

    fun handleClear() {
        performClickFeedback()
        rawExpression = ""
    }

    fun handleBackspace() {
        performClickFeedback()
        if (rawExpression.isNotEmpty()) {
            rawExpression = rawExpression.dropLast(1)
        }
    }

    fun evaluate() {
        performClickFeedback()
        val result = evaluateSimpleExpression(rawExpression)
        if (result != null) {
            rawExpression = if (result % 1.0 == 0.0) {
                result.toInt().toString()
            } else {
                result.toString()
            }
        }
    }

    fun confirmAndDismiss() {
        performClickFeedback()
        val finalValue = evaluateSimpleExpression(rawExpression) ?: rawExpression.toDoubleOrNull() ?: 0.0
        onValueConfirmed(finalValue)
    }

    // Cursor blink animation
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorBlink"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF8FAFC) // Beautiful slate white
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header of Calculator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(id = R.string.calc_close_desc),
                            tint = Color(0xFFEF4444)
                        )
                    }

                    Text(
                        text = stringResource(id = R.string.calc_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = brandPrimary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Digital Display Screen
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.End
                    ) {
                        // Expression Line
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = rawExpression.ifEmpty { "0" },
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (rawExpression.isEmpty()) Color.LightGray else Color(0xFF1E293B),
                                textAlign = TextAlign.Right,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            // Red Cursor Blink
                            if (cursorAlpha > 0.5f) {
                                Text(
                                    text = "|",
                                    color = Color(0xFFEF4444),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 2.dp)
                                )
                            }
                        }

                        // Preview / Result Line
                        if (resultPreview != null && resultPreview.toString() != rawExpression) {
                            val formattedPreview = if (resultPreview % 1.0 == 0.0) {
                                resultPreview.toInt().toString()
                            } else {
                                resultPreview.toString()
                            }
                            Text(
                                text = "= $formattedPreview",
                                fontSize = 16.sp,
                                color = brandPrimary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Arabic Right-to-Left Layout Keyboard
                // Design matched perfectly with the screenshot layout (operations on the right)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Row 1: [⌫] [9] [8] [7]
                        // First item is ⌫ (renders on extreme right under RTL layout direction)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CalcButton(text = "⌫", isBackspace = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleBackspace() }
                            CalcButton(text = "9", isNumber = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleDigit("9") }
                            CalcButton(text = "8", isNumber = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleDigit("8") }
                            CalcButton(text = "7", isNumber = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleDigit("7") }
                        }

                        // Row 2: [×] [6] [5] [4]
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CalcButton(text = "×", isOp = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleOperator("×") }
                            CalcButton(text = "6", isNumber = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleDigit("6") }
                            CalcButton(text = "5", isNumber = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleDigit("5") }
                            CalcButton(text = "4", isNumber = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleDigit("4") }
                        }

                        // Row 3: [-] [3] [2] [1]
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CalcButton(text = "-", isOp = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleOperator("-") }
                            CalcButton(text = "3", isNumber = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleDigit("3") }
                            CalcButton(text = "2", isNumber = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleDigit("2") }
                            CalcButton(text = "1", isNumber = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleDigit("1") }
                        }

                        // Row 4: [+] [C] [0] [.]
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CalcButton(text = "+", isOp = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleOperator("+") }
                            CalcButton(text = "C", isOp = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleClear() }
                            CalcButton(text = "0", isNumber = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleDigit("0") }
                            CalcButton(text = ".", isNumber = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleDigit(".") }
                        }

                        // Row 5: [= (weight 2)] [÷] [OK]
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Equals Button (Spans equivalent weight of 2 buttons, renders on extreme right)
                            CalcButton(text = "=", isEquals = true, brandPrimary = brandPrimary, modifier = Modifier.weight(2f)) { evaluate() }
                            
                            // Division Operator
                            CalcButton(text = "÷", isOp = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { handleOperator("÷") }

                            // OK Button (Action to confirm and dismiss)
                            CalcButton(text = "OK", isAction = true, brandPrimary = brandPrimary, modifier = Modifier.weight(1f)) { confirmAndDismiss() }
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
    modifier: Modifier = Modifier,
    isNumber: Boolean = false,
    isOp: Boolean = false,
    isBackspace: Boolean = false,
    isAction: Boolean = false,
    isEquals: Boolean = false,
    brandPrimary: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    // Determine color schemes precisely matching screenshot style but styled with our brand colors
    val backgroundColor = when {
        isEquals -> brandPrimary // Theme Dynamic primary color for Equals action
        isBackspace -> Color(0xFF334155) // Dark slate charcoal
        isOp || isAction -> Color(0xFFF1F5F9) // Light blue-grey tint for operation keys
        else -> Color.White // Standard pristine white for numbers
    }

    val textColor = when {
        isEquals || isBackspace -> Color.White
        isOp || isAction -> brandPrimary // Greenish/Teal/Primary accent for operators
        else -> Color(0xFF1E293B) // Near-black slate for legible numbers
    }

    Card(
        modifier = modifier
            .height(52.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = if (isAction) 16.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
