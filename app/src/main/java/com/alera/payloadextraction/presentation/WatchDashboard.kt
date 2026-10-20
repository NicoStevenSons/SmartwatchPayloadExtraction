package com.alera.payloadextraction.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices

// Static colors and simple, non-animated layouts keep the watch UI lightweight.
private val Purple = Color(0xFF6242AD)
private val DarkPurple = Color(0xFF302052)
private val MutedPurple = Color(0xFF716486)
private val LightLavender = Color(0xFFF5F1FC)
private val PalePurple = Color(0xFFEAE1FA)
private val White = Color.White

@Composable
fun WatchDashboard(
    heartRateBpm: Double?,
    spo2Percent: Double?,
    spo2Status: Int?,
    isWorn: Boolean?,
    batteryPercent: Int?,
    phoneConnected: Boolean?
) {
    val wristLabel = when (isWorn) {
        true -> "On wrist"
        false -> "Not worn"
        null -> "Wrist: checking"
    }
    val phoneLabel = when (phoneConnected) {
        true -> "Phone connected"
        false -> "Phone disconnected"
        null -> "Phone: checking"
    }
    val oxygen = spo2Percent?.toInt()?.toString() ?: "--"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightLavender)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "alera",
            color = Purple,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Text(
            text = "Health at a glance",
            color = MutedPurple,
            fontSize = 10.sp
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(White, RoundedCornerShape(22.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "HEART RATE",
                color = MutedPurple,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = heartRateBpm?.toInt()?.toString() ?: "--",
                    color = DarkPurple,
                    fontSize = 43.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 45.sp
                )
                Text(
                    text = " BPM",
                    color = Purple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallMetric(
                label = "OXYGEN",
                value = oxygen,
                unit = "%",
                modifier = Modifier.weight(1f)
            )
            SmallMetric(
                label = "BATTERY",
                value = batteryPercent?.takeIf { it >= 0 }?.toString() ?: "--",
                unit = "%",
                modifier = Modifier.weight(1f)
            )
        }

        if (spo2Percent == null && spo2Status == 0) {
            Text(
                text = "Measuring oxygen…",
                color = Purple,
                fontSize = 10.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Purple, CircleShape)
            )
            Text(
                text = "  $wristLabel  ·  $phoneLabel",
                color = DarkPurple,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SmallMetric(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(PalePurple, RoundedCornerShape(18.dp))
            .padding(vertical = 10.dp, horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = MutedPurple,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = DarkPurple,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = unit,
                color = Purple,
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@WearPreviewDevices
@Composable
fun WatchDashboardPreview() {
    WatchDashboard(
        heartRateBpm = 78.0,
        spo2Percent = 97.0,
        spo2Status = 2,
        isWorn = true,
        batteryPercent = 82,
        phoneConnected = true
    )
}
