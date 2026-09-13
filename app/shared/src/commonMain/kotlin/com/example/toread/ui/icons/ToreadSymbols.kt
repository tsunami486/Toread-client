package com.example.toread.ui.icons

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.toread.ui.icons.generated.TwoPager as TwoPagerImageVector
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import toread.client.app.shared.generated.resources.Res
import toread.client.app.shared.generated.resources.*

// Google Material Symbols Rounded from fonts.google.com/icons: 24px, fill 0, weight 400, grade 0.
internal object ToreadSymbols {
    val AccountCircle = Res.drawable.material_symbol_rounded_account_circle
    val Add = Res.drawable.material_symbol_rounded_add
    val ArrowBack = Res.drawable.material_symbol_rounded_arrow_back
    val Article = Res.drawable.material_symbol_rounded_article
    val Assessment = Res.drawable.material_symbol_rounded_assessment
    val ChevronRight = Res.drawable.material_symbol_rounded_chevron_right
    val CloudDone = Res.drawable.material_symbol_rounded_cloud_done
    val CloudOff = Res.drawable.material_symbol_rounded_cloud_off
    val CloudSync = Res.drawable.material_symbol_rounded_cloud_sync
    val DarkMode = Res.drawable.material_symbol_rounded_dark_mode
    val DeleteSweep = Res.drawable.material_symbol_rounded_delete_sweep
    val FolderOpen = Res.drawable.material_symbol_rounded_folder_open
    val GridView = Res.drawable.material_symbol_rounded_grid_view
    val HourglassEmpty = Res.drawable.material_symbol_rounded_hourglass_empty
    val Info = Res.drawable.material_symbol_rounded_info
    val KeyboardArrowDown = Res.drawable.material_symbol_rounded_keyboard_arrow_down
    val KeyboardArrowLeft = Res.drawable.material_symbol_rounded_keyboard_arrow_left
    val KeyboardArrowRight = Res.drawable.material_symbol_rounded_keyboard_arrow_right
    val KeyboardArrowUp = Res.drawable.material_symbol_rounded_keyboard_arrow_up
    val KeyboardDoubleArrowLeft = Res.drawable.material_symbol_rounded_keyboard_double_arrow_left
    val KeyboardDoubleArrowRight = Res.drawable.material_symbol_rounded_keyboard_double_arrow_right
    val Keep = Res.drawable.material_symbol_rounded_keep
    val LightMode = Res.drawable.material_symbol_rounded_light_mode
    val Logout = Res.drawable.material_symbol_rounded_logout
    val Menu = Res.drawable.material_symbol_rounded_menu
    val MenuBook = Res.drawable.material_symbol_rounded_menu_book
    val Palette = Res.drawable.material_symbol_rounded_palette
    val PhotoCamera = Res.drawable.material_symbol_rounded_photo_camera
    val Refresh = Res.drawable.material_symbol_rounded_refresh
    val Remove = Res.drawable.material_symbol_rounded_remove
    val Search = Res.drawable.material_symbol_rounded_search
    val Settings = Res.drawable.material_symbol_rounded_settings
    val Sync = Res.drawable.material_symbol_rounded_sync
    val TextFields = Res.drawable.material_symbol_rounded_text_fields
    val TipsAndUpdates = Res.drawable.material_symbol_rounded_tips_and_updates
    val Tune = Res.drawable.material_symbol_rounded_tune
    val ViewColumn = Res.drawable.material_symbol_rounded_view_column
    val ViewDay = Res.drawable.material_symbol_rounded_view_day
    val TwoPager = TwoPagerImageVector
}

@Composable
internal fun ToreadSymbolIcon(
    symbol: DrawableResource,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    autoMirror: Boolean = false,
) {
    val shouldMirror = autoMirror && LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        painter = painterResource(symbol),
        contentDescription = contentDescription,
        modifier = if (shouldMirror) modifier.scale(scaleX = -1f, scaleY = 1f) else modifier,
        tint = tint,
    )
}

@Composable
internal fun ToreadSymbolIcon(
    symbol: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    autoMirror: Boolean = false,
) {
    val shouldMirror = autoMirror && LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        imageVector = symbol,
        contentDescription = contentDescription,
        modifier = if (shouldMirror) modifier.scale(scaleX = -1f, scaleY = 1f) else modifier,
        tint = tint,
    )
}
