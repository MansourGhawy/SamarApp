package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.CustomCategory
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBottomSheet(
    schoolExpensesEnabled: Boolean,
    customCategories: List<CustomCategory>,
    onCategorySelected: (String, String) -> Unit, // (name, emoji)
    onAddCustomCategory: (String, String, String) -> Unit, // (name, emoji, tab)
    onDeleteCategory: (CustomCategory) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTabIdx by remember { mutableStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }

    // Externalized Predefined Tabs
    val tabFoodDar = stringResource(id = R.string.cat_tab_food_dar)
    val tabBillsDar = stringResource(id = R.string.cat_tab_bills_dar)
    val tabFamily = stringResource(id = R.string.cat_tab_family)
    val tabOther = stringResource(id = R.string.cat_tab_other)

    val tabs = remember(tabFoodDar, tabBillsDar, tabFamily, tabOther) {
        listOf(tabFoodDar, tabBillsDar, tabFamily, tabOther)
    }

    // Filter categories based on selected tab
    val currentTabName = tabs.getOrElse(selectedTabIdx) { "" }
    val currentItems = customCategories.filter { it.tabType == currentTabName }

    // Modal dialog styled like a bottom drawer (Slide-up feel)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            // Document container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.70f) // Take 70% of screen height
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .clickable(enabled = false) { } // prevent dismiss click propagation
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Bottom sheet handle indicator
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Gray.copy(alpha = 0.5f))
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(id = R.string.cat_close),
                            tint = CoralAccent
                        )
                    }

                    Text(
                        text = stringResource(id = R.string.cat_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldPrimary,
                        textAlign = TextAlign.Center
                    )

                    TextButton(onClick = { isEditMode = !isEditMode }) {
                        Text(
                            text = if (isEditMode) stringResource(id = R.string.cat_btn_done) else stringResource(id = R.string.cat_btn_edit),
                            color = if (isEditMode) SoftGreen else EmeraldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Beautiful scrolling Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIdx,
                    edgePadding = 8.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            color = CoralAccent,
                            height = 3.dp,
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIdx])
                        )
                    },
                    containerColor = Color.Transparent,
                    contentColor = EmeraldPrimary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIdx == index,
                            onClick = { selectedTabIdx = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTabIdx == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Category Grid items view
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Quick Add button
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CoralAccent),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.cat_btn_add_new),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (currentItems.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(id = R.string.cat_empty_list),
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(currentItems, key = { it.name }) { cat ->
                                    Box(contentAlignment = Alignment.TopStart) {
                                        CategoryCard(name = cat.name, emoji = cat.iconEmoji) {
                                            if (!isEditMode) {
                                                onCategorySelected(cat.name, cat.iconEmoji)
                                            }
                                        }
                                        if (isEditMode) {
                                            IconButton(
                                                onClick = { onDeleteCategory(cat) },
                                                modifier = Modifier
                                                    .offset(x = (-4).dp, y = (-4).dp)
                                                    .size(24.dp)
                                                    .background(SoftRed, RoundedCornerShape(12.dp))
                                                    .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = stringResource(id = R.string.cat_delete_desc),
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Pop-up details for entering custom categories
    if (showAddDialog) {
        var customName by remember { mutableStateOf("") }
        var selectedEmoji by remember { mutableStateOf("🏷️") }
        var selectedTabType by remember { mutableStateOf(tabs.getOrElse(selectedTabIdx) { tabs.firstOrNull() ?: "" }) }

        val focusRequester = remember { FocusRequester() }
        val softwareKeyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(300)
            try {
                focusRequester.requestFocus()
                softwareKeyboardController?.show()
            } catch (e: Exception) {}
        }

        val emojiList = listOf(
            "🏷️", "🍔", "🚕", "🎁", "☕", "🎮", "👚", "📱", "🏠", "💐", "🧱", "🛠️", "🛒", "🔌", "🧾", "💰", "🥩", "🧼", "👶", "⛽", "📦", "✈️", "🏫", "💵"
        )

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = stringResource(id = R.string.cat_dialog_title),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.End
                ) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text(stringResource(id = R.string.cat_label_name)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.cat_label_select_tab),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Small tab selector inside dialog
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        items(tabs, key = { it }) { tab ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedTabType == tab) EmeraldPrimary else Color(0xFFF1F5F9))
                                    .clickable { selectedTabType = tab }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab,
                                    fontSize = 11.sp,
                                    color = if (selectedTabType == tab) Color.White else Color.Gray
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(id = R.string.cat_label_select_emoji),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    ) {
                        items(emojiList, key = { it }) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selectedEmoji == emoji) CoralAccent.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(
                                        width = if (selectedEmoji == emoji) 2.dp else 0.dp,
                                        color = if (selectedEmoji == emoji) CoralAccent else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedEmoji = emoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (customName.isNotBlank()) {
                            onAddCustomCategory(customName, selectedEmoji, selectedTabType)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text(text = stringResource(id = R.string.cat_btn_add_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(
                        text = stringResource(id = R.string.cat_btn_cancel),
                        color = Color.Gray
                    )
                }
            }
        )
    }
}

@Composable
private fun CategoryCard(name: String, emoji: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F1EF)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Text(text = emoji, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
