package com.example.ui.screens.ledger.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.*
import com.example.ui.screens.CalculatorDialog
import com.example.ui.screens.CategoryBottomSheet
import com.example.ui.theme.CoralAccent
import com.example.ui.theme.EmeraldPrimary
import kotlinx.coroutines.delay

@Composable
fun TransactionRecordDialog(
    editingTransaction: TransactionDb?,
    txDialogType: String,
    currencySymbol: String,
    schoolExpensesEnabled: Boolean,
    customCategories: List<CustomCategoryDb>,
    onDismiss: () -> Unit,
    onSaveTransaction: (amount: Double, description: String, category: String) -> Unit,
    onAddCustomCategory: (name: String, tab: String, emoji: String) -> Unit,
    onDeleteCustomCategory: (CustomCategoryDb) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    var numAmount by rememberSaveable(editingTransaction) { 
        mutableStateOf(editingTransaction?.amount?.toString() ?: "") 
    }
    var descriptionStr by rememberSaveable(editingTransaction) { 
        mutableStateOf(editingTransaction?.description ?: "") 
    }
    var categoryName by rememberSaveable(editingTransaction) { 
        mutableStateOf(
            editingTransaction?.category ?: if (txDialogType == "INCOME") {
                context.getString(R.string.ledger_category_overall_income)
            } else {
                context.getString(R.string.ledger_category_expense)
            }
        )
    }

    var showCalcPopup by rememberSaveable { mutableStateOf(false) }
    var isSavingTx by remember { mutableStateOf(false) }
    var showCategoryPickerSheet by remember { mutableStateOf(false) }

    // تحسين الأداء باستخدام derivedStateOf للتحقق المباشر من قيمة المدخلات
    val isConfirmButtonEnabled by remember {
        derivedStateOf {
            !isSavingTx && (numAmount.toDoubleOrNull() ?: 0.0) > 0.0
        }
    }

    val focusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val softwareKeyboardController = LocalSoftwareKeyboardController.current

    // توجيه الفوكس التلقائي على حقل المبلغ عند فتح الحوار
    LaunchedEffect(Unit) {
        delay(300)
        try {
            focusRequester.requestFocus()
            softwareKeyboardController?.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingTransaction != null) {
                    stringResource(id = R.string.ledger_edit_transaction_title)
                } else if (txDialogType == "INCOME") {
                    stringResource(id = R.string.ledger_add_income_title)
                } else {
                    stringResource(id = R.string.ledger_add_expense_title)
                },
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.End
            ) {
                // حقل المبلغ بتركيز تلقائي
                OutlinedTextField(
                    value = numAmount,
                    onValueChange = { numAmount = it },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { descriptionFocusRequester.requestFocus() }
                    ),
                    label = { Text(stringResource(id = R.string.ledger_amount_label, currencySymbol)) },
                    singleLine = true,
                    leadingIcon = {
                        IconButton(onClick = { showCalcPopup = true }) {
                            Icon(
                                imageVector = Icons.Default.Calculate, 
                                contentDescription = stringResource(id = R.string.habayeb_calculator), 
                                tint = CoralAccent
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // حقل تفاصيل الحركة
                OutlinedTextField(
                    value = descriptionStr,
                    onValueChange = { descriptionStr = it },
                    label = { Text(stringResource(id = R.string.ledger_desc_label_placeholder)) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(descriptionFocusRequester),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            softwareKeyboardController?.hide()
                        }
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // زر اختيار تصنيف مخصص للحوار (متاح فقط للإيرادات INCOME)
                if (txDialogType == "INCOME" && editingTransaction != null) {
                    Button(
                        onClick = { showCategoryPickerSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (categoryName.isNotBlank()) {
                                stringResource(id = R.string.ledger_category_label, categoryName)
                            } else {
                                stringResource(id = R.string.ledger_choose_category_placeholder)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = isConfirmButtonEnabled,
                onClick = {
                    if (isSavingTx) return@Button
                    isSavingTx = true

                    val amtParsed = numAmount.toDoubleOrNull() ?: 0.0
                    if (amtParsed > 0) {
                        onSaveTransaction(amtParsed, descriptionStr, categoryName)
                    } else {
                        isSavingTx = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text(stringResource(id = R.string.ledger_save_tx_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.common_cancel), color = Color.Gray)
            }
        }
    )

    // عرض منتقي التصنيفات
    if (showCategoryPickerSheet) {
        CategoryBottomSheet(
            schoolExpensesEnabled = schoolExpensesEnabled,
            customCategories = customCategories,
            onCategorySelected = { name, emoji ->
                categoryName = "$name $emoji"
                showCategoryPickerSheet = false
            },
            onAddCustomCategory = onAddCustomCategory,
            onDeleteCategory = onDeleteCustomCategory,
            onDismiss = { showCategoryPickerSheet = false }
        )
    }

    // عرض الآلة الحاسبة المدمجة
    if (showCalcPopup) {
        CalculatorDialog(
            onDismiss = { showCalcPopup = false },
            onValueConfirmed = { calcResult ->
                numAmount = calcResult.toString()
                showCalcPopup = false
            }
        )
    }
}