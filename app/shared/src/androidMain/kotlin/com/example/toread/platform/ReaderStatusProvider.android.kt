package com.example.toread.platform

import android.content.Context
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

@Composable
internal actual fun rememberReaderStatusProvider(): ReaderStatusProvider {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        AndroidReaderStatusProvider(applicationContext)
    }
}

private class AndroidReaderStatusProvider(
    private val applicationContext: Context,
) : ReaderStatusProvider {
    override fun currentStatus(): ReaderStatusSnapshot {
        val now = Calendar.getInstance()
        return ReaderStatusSnapshot(
            clockText = buildString {
                append(now.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0'))
                append(':')
                append(now.get(Calendar.MINUTE).toString().padStart(2, '0'))
            },
            batteryPercent = (
                applicationContext.getSystemService(Context.BATTERY_SERVICE)
                    as? BatteryManager
                )
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 },
        )
    }
}
