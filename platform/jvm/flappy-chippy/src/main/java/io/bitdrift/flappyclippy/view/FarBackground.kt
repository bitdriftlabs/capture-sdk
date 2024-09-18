package io.bitdrift.flappychippy.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import io.bitdrift.flappychippy.util.LogUtil
import io.bitdrift.flappychippy.R

@Composable
fun FarBackground() {
    LogUtil.printLog(message = "FarBackground()")

    Column {
        Image(
            painter = painterResource(id = R.drawable.background),
            contentScale = ContentScale.FillHeight,
            contentDescription = "Far Background",
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(widthDp = 411, heightDp = 600)
@Composable
fun previewBackground() {
    FarBackground()
}
