package io.bitdrift.flappychippy.util

import android.app.Activity
import android.content.res.Resources
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.bitdrift.capture.Capture
import io.bitdrift.flappychippy.R

object StatusBarUtil {
    fun transparentStatusBar(activity: Activity) {
        with(activity) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            val option = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            val vis = window.decorView.systemUiVisibility
            window.decorView.systemUiVisibility = option or vis
            window.statusBarColor = Color.TRANSPARENT
        }
    }
}

object DensityUtil {
    fun dxToDp(resources: Resources, px: Int): Int =
        (px / resources.displayMetrics.density + 0.5f).toInt()
}

object ValueUtil {
    fun getRandomDp(fromDp: Dp, toDp: Dp): Dp  = (fromDp.value.toInt()..toDp.value.toInt()).random().dp
}

object LogUtil {
    fun printLog(tag : String = "FlappyChippy", message: String) {
        //Log.d(tag, message)
    }
}

val ScoreFontFamily = FontFamily(
    Font(R.font.riskofrainsquare, FontWeight.Bold)
)