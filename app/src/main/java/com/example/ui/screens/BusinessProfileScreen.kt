package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.R
import com.example.ui.viewmodel.FinanceViewModel
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessProfileScreen(
    viewModel: FinanceViewModel,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val activeThemeColor = MaterialTheme.colorScheme.primary

    // Load saved profile data
    val prefs = remember { context.getSharedPreferences("business_profile", Context.MODE_PRIVATE) }
    val altPrefs = remember { context.getSharedPreferences("business_profile_prefs", Context.MODE_PRIVATE) }

    var bizName by remember { 
        mutableStateOf(
            prefs.getString("biz_name", "").orEmpty()
                .ifBlank { altPrefs.getString("business_name", "").orEmpty() }
        ) 
    }
    
    var bizDesc by remember { 
        mutableStateOf(
            prefs.getString("biz_desc", "").orEmpty()
                .ifBlank { altPrefs.getString("business_slogan", "").orEmpty() }
        )
    }
    
    // Load logo path
    var logoPath by remember { 
        mutableStateOf(
            prefs.getString("biz_logo_path", "").orEmpty()
                .ifBlank { altPrefs.getString("logo_path", "").orEmpty() }
        ) 
    }
    
    var logoBitmapState by remember { mutableStateOf<Bitmap?>(null) }

    // Read stored logo
    LaunchedEffect(logoPath) {
        if (logoPath.isNotEmpty()) {
            try {
                val file = File(logoPath)
                if (file.exists()) {
                    logoBitmapState = BitmapFactory.decodeFile(file.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Load phones
    val phoneList = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) {
        val phonesJson = prefs.getString("biz_phones", "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(phonesJson)
            phoneList.clear()
            for (i in 0 until jsonArray.length()) {
                phoneList.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (phoneList.isEmpty()) {
            val fallbackPhone = altPrefs.getString("business_phone", "").orEmpty()
            phoneList.add(fallbackPhone) // Use fallback if available, else empty string
        }
    }

    // Temp selected image for editing modal
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var editingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropShapeIsCircle by remember { mutableStateOf(false) } // true: circle, false: square

    // Official android system photo picker (PickVisualMedia) ensuring safe lightweight operation
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingImageUri = uri
            val original = uriToBitmap(context, uri)
            if (original != null) {
                // Safeguard large bitmaps immediately with 800px scale constraint
                editingBitmap = scaleBitmap(original, 800)
            } else {
                Toast.makeText(context, context.getString(R.string.biz_toast_logo_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.biz_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("biz_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.biz_back),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = activeThemeColor
                )
            )
        },
        containerColor = Color(0xFFF8FAFC)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Logo Picker & Edit
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.biz_logo_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                            .border(2.dp, activeThemeColor.copy(alpha = 0.3f), CircleShape)
                            .clickable { 
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                ) 
                            }
                            .testTag("biz_logo_box"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (logoBitmapState != null) {
                            Image(
                                bitmap = logoBitmapState!!.asImageBitmap(),
                                contentDescription = stringResource(id = R.string.biz_logo_desc),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    tint = activeThemeColor,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(id = R.string.biz_add_logo),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = activeThemeColor
                                )
                            }
                        }

                        // Edit Overlay Icon
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(activeThemeColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(id = R.string.biz_edit),
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        // Delete Overlay Icon (Only when logo is selected/exists)
                        if (logoBitmapState != null) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                        .clickable {
                                            logoPath = ""
                                            logoBitmapState = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "حذف الشعار",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (logoBitmapState != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.biz_logo_tip),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            // Section 2: Business Profile Information Form
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.biz_details_section),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = bizName,
                        onValueChange = { bizName = it },
                        label = { Text(text = stringResource(id = R.string.biz_label_name), fontSize = 13.sp) },
                        placeholder = { Text(text = stringResource(id = R.string.biz_placeholder_name), fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("biz_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeThemeColor,
                            focusedLabelColor = activeThemeColor,
                            cursorColor = activeThemeColor
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )

                    OutlinedTextField(
                        value = bizDesc,
                        onValueChange = { bizDesc = it },
                        label = { Text(text = stringResource(id = R.string.biz_label_desc), fontSize = 13.sp) },
                        placeholder = { Text(text = stringResource(id = R.string.biz_placeholder_desc), fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("biz_desc_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeThemeColor,
                            focusedLabelColor = activeThemeColor,
                            cursorColor = activeThemeColor
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                    )
                }
            }

            // Section 3: Dynamic Phones List
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.biz_phones_section),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            modifier = Modifier.testTag("biz_phones_section")
                        )
                        
                        if (phoneList.size < 3) {
                            TextButton(
                                onClick = { phoneList.add("") },
                                colors = ButtonDefaults.textButtonColors(contentColor = activeThemeColor),
                                modifier = Modifier.testTag("biz_add_phone_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = stringResource(id = R.string.biz_btn_add_phone),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    phoneList.forEachIndexed { index, phone ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { newVal -> phoneList[index] = newVal },
                                label = { 
                                    Text(
                                        text = if (index == 0) {
                                            stringResource(id = R.string.biz_label_primary_phone)
                                        } else {
                                            stringResource(id = R.string.biz_label_secondary_phone, index + 1)
                                        }, 
                                        fontSize = 13.sp
                                    ) 
                                },
                                placeholder = { Text(text = stringResource(id = R.string.biz_placeholder_phone), fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = activeThemeColor,
                                    focusedLabelColor = activeThemeColor,
                                    cursorColor = activeThemeColor
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Right)
                            )

                            if (index > 0) {
                                IconButton(
                                    onClick = { phoneList.removeAt(index) },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFFEF4444))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(id = R.string.biz_desc_delete_phone)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Save Buttons
            Button(
                onClick = {
                    if (bizName.isBlank()) {
                        Toast.makeText(context, context.getString(R.string.biz_toast_err_empty_name), Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Save to PRIMARY SharedPreferences ("business_profile")
                    val editor = prefs.edit()
                    editor.putString("biz_name", bizName.trim())
                    editor.putString("biz_desc", bizDesc.trim())
                    
                    // Serialize Phone List
                    val jsonArray = JSONArray()
                    phoneList.filter { it.isNotBlank() }.forEach {
                        jsonArray.put(it.trim())
                    }
                    editor.putString("biz_phones", jsonArray.toString())
                    editor.putString("biz_logo_path", logoPath)
                    editor.apply()

                    // Save to SECONDARY SharedPreferences ("business_profile_prefs") to guarantee PDF correctness
                    val altEditor = altPrefs.edit()
                    altEditor.putString("business_name", bizName.trim())
                    altEditor.putString("business_slogan", bizDesc.trim())
                    altEditor.putString("logo_path", logoPath)
                    val primaryPhone = phoneList.firstOrNull { it.isNotBlank() } ?: ""
                    altEditor.putString("business_phone", primaryPhone)
                    altEditor.apply()

                    Toast.makeText(context, context.getString(R.string.biz_toast_save_success), Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("biz_save_button"),
                colors = ButtonDefaults.buttonColors(containerColor = activeThemeColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        text = stringResource(id = R.string.biz_btn_save), 
                        fontSize = 15.sp, 
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // --- PHOTO EDITOR MODAL DIALOG ---
    if (editingBitmap != null) {
        Dialog(onDismissRequest = { editingBitmap = null; pendingImageUri = null }) {
            val density = LocalDensity.current.density
            val kPx = 200f * density
            val centerPx = kPx / 2f

            var scale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }

            // Compute centering and layout bounds to constrain dragging accurately
            val w = editingBitmap!!.width.toFloat()
            val h = editingBitmap!!.height.toFloat()
            val s0 = kPx / Math.min(w, h)
            val wDraw = w * s0
            val hDraw = h * s0
            val x0 = (kPx - wDraw) / 2f
            val y0 = (kPx - hDraw) / 2f

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.biz_crop_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )

                    // Image Preview container with adaptive clip
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .background(Color(0xFFF1F5F9), if (cropShapeIsCircle) CircleShape else RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFCBD5E1), if (cropShapeIsCircle) CircleShape else RoundedCornerShape(8.dp))
                            .clip(if (cropShapeIsCircle) CircleShape else RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = editingBitmap!!.asImageBitmap(),
                            contentDescription = stringResource(id = R.string.biz_crop_preview_desc),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        val maxTx = (centerPx - x0) * scale - centerPx
                                        val minTx = centerPx - (x0 + wDraw - centerPx) * scale
                                        val maxTy = (centerPx - y0) * scale - centerPx
                                        val minTy = centerPx - (y0 + hDraw - centerPx) * scale
                                        offsetX = (offsetX + pan.x).coerceIn(minTx, maxTx)
                                        offsetY = (offsetY + pan.y).coerceIn(minTy, maxTy)
                                    }
                                },
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Interactive Zoom Slider
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.biz_zoom_and_pan),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = String.format(java.util.Locale.ENGLISH, "%.1fx", scale),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeThemeColor
                            )
                        }
                        Slider(
                            value = scale,
                            onValueChange = { newScale ->
                                scale = newScale
                                val maxTx = (centerPx - x0) * scale - centerPx
                                val minTx = centerPx - (x0 + wDraw - centerPx) * scale
                                val maxTy = (centerPx - y0) * scale - centerPx
                                val minTy = centerPx - (y0 + hDraw - centerPx) * scale
                                offsetX = offsetX.coerceIn(minTx, maxTx)
                                offsetY = offsetY.coerceIn(minTy, maxTy)
                            },
                            valueRange = 1f..5f,
                            colors = SliderDefaults.colors(
                                thumbColor = activeThemeColor,
                                activeTrackColor = activeThemeColor,
                                inactiveTrackColor = Color(0xFFE2E8F0)
                            )
                        )
                    }

                    // Crop shape selector & Rotate buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Square selector
                        Button(
                            onClick = { cropShapeIsCircle = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!cropShapeIsCircle) activeThemeColor else Color(0xFFF1F5F9),
                                contentColor = if (!cropShapeIsCircle) Color.White else Color(0xFF475569)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.biz_crop_square), 
                                fontSize = 11.sp, 
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Circular selector
                        Button(
                            onClick = { cropShapeIsCircle = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (cropShapeIsCircle) activeThemeColor else Color(0xFFF1F5F9),
                                contentColor = if (cropShapeIsCircle) Color.White else Color(0xFF475569)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.biz_crop_circle), 
                                fontSize = 11.sp, 
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Rotate button
                        Button(
                            onClick = {
                                editingBitmap = rotateBitmap(editingBitmap!!, 90f)
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF1F5F9),
                                contentColor = Color(0xFF475569)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(id = R.string.biz_rotate), 
                                fontSize = 11.sp, 
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Save/Cancel buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                editingBitmap = null
                                pendingImageUri = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(text = stringResource(id = R.string.biz_btn_cancel), fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val croppedResult = cropWithTransform(
                                    editingBitmap!!,
                                    scale,
                                    offsetX,
                                    offsetY,
                                    density,
                                    cropShapeIsCircle
                                )

                                // Scale down the logo to a max of 400px to ensure tiny file sizes (e.g., ~30KB) and prevent huge reports
                                val scaledResult = scaleBitmap(croppedResult, 400)

                                // Save locally
                                val localPath = saveBitmapToInternalStorage(context, scaledResult)
                                if (localPath != null) {
                                    logoPath = localPath
                                    logoBitmapState = scaledResult
                                    Toast.makeText(context, context.getString(R.string.biz_toast_logo_success), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.biz_toast_logo_save_err), Toast.LENGTH_SHORT).show()
                                }

                                editingBitmap = null
                                pendingImageUri = null
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = activeThemeColor)
                        ) {
                            Text(text = stringResource(id = R.string.biz_btn_apply), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// Helpers
private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        BitmapFactory.decodeStream(inputStream)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun cropWithTransform(
    bitmap: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    density: Float,
    isCircle: Boolean
): Bitmap {
    val kPx = 200f * density
    val centerPx = kPx / 2f

    val w = bitmap.width.toFloat()
    val h = bitmap.height.toFloat()
    val s0 = kPx / Math.min(w, h)
    val wDraw = w * s0
    val hDraw = h * s0
    val x0 = (kPx - wDraw) / 2f
    val y0 = (kPx - hDraw) / 2f

    // Un-transformed component coordinates
    val pTLx = (-offsetX - centerPx) / scale + centerPx
    val pTLy = (-offsetY - centerPx) / scale + centerPx
    val pBRx = (kPx - offsetX - centerPx) / scale + centerPx
    val pBRy = (kPx - offsetY - centerPx) / scale + centerPx

    // Source image pixels
    val leftPx = ((pTLx - x0) / s0).toInt().coerceIn(0, bitmap.width)
    val topPx = ((pTLy - y0) / s0).toInt().coerceIn(0, bitmap.height)
    val rightPx = ((pBRx - x0) / s0).toInt().coerceIn(0, bitmap.width)
    val bottomPx = ((pBRy - y0) / s0).toInt().coerceIn(0, bitmap.height)

    val cropW = (rightPx - leftPx).coerceAtLeast(10)
    val cropH = (bottomPx - topPx).coerceAtLeast(10)

    val actualCropW = Math.min(cropW, bitmap.width - leftPx)
    val actualCropH = Math.min(cropH, bitmap.height - topPx)

    // Create the square cropped bitmap
    val squareBitmap = Bitmap.createBitmap(bitmap, leftPx, topPx, actualCropW, actualCropH)

    if (!isCircle) {
        return squareBitmap
    }

    // Convert to circular bitmap
    val size = Math.min(squareBitmap.width, squareBitmap.height)
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint().apply {
        isAntiAlias = true
        color = 0xff424242.toInt()
    }
    val rect = Rect(0, 0, size, size)
    canvas.drawARGB(0, 0, 0, 0)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(squareBitmap, rect, rect, paint)
    
    return output
}

private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= maxDimension && height <= maxDimension) return bitmap
    val ratio = width.toFloat() / height.toFloat()
    val newWidth: Int
    val newHeight: Int
    if (ratio > 1f) {
        newWidth = maxDimension
        newHeight = (maxDimension / ratio).toInt()
    } else {
        newHeight = maxDimension
        newWidth = (maxDimension * ratio).toInt()
    }
    return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
}

private fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap): String? {
    return try {
        val file = File(context.filesDir, "business_logo.png")
        if (file.exists()) {
            file.delete()
        }
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
