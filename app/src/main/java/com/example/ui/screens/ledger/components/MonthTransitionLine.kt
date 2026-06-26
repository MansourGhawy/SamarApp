package com.example.ui.screens.ledger.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IvoryBackground

@Composable
fun MonthTransitionLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp),
        contentAlignment = Alignment.Center
    ) {
        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        Canvas(
            modifier = Modifier.fillMaxWidth(0.8f).matchParentSize()
        ) {
            drawLine(
                color = EmeraldPrimary.copy(alpha = 0.4f),
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
                pathEffect = pathEffect,
                strokeWidth = 2.dp.toPx()
            )
        }
        Text(
            text = stringResource(id = R.string.ledger_beginning_new_month),
            color = EmeraldPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.background(IvoryBackground).padding(horizontal = 12.dp)
        )
    }
}
