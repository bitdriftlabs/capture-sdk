package io.bitdrift.flappychippy.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.bitdrift.flappychippy.util.LogUtil
import io.bitdrift.flappychippy.ui.theme.GroundDividerPurple
import io.bitdrift.flappychippy.R
import io.bitdrift.flappychippy.model.TempRoadWidthOffset
import io.bitdrift.flappychippy.model.GameAction
import io.bitdrift.flappychippy.model.ViewState
import io.bitdrift.flappychippy.viewmodel.GameViewModel

@Composable
fun NearForeground(
    modifier: Modifier = Modifier,
    state: ViewState = ViewState()
) {
    LogUtil.printLog(message = "NearForeground()")
    val viewModel: GameViewModel = viewModel()

    Column(
        modifier
    ) {
        // Divider between background and foreground
        Divider(
            color = GroundDividerPurple,
            thickness = 5.dp
        )

        // Road
        Box(modifier = Modifier.fillMaxWidth()) {
            state.roadStateList.forEach { roadState ->
                LogUtil.printLog(message = "NearForeground() roadState:$roadState")
                Image(
                    painter = painterResource(id = R.drawable.foreground_road),
                    contentScale = ContentScale.FillBounds,
                    contentDescription = "Road",
                    modifier = modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.23f)
                        .offset(x = roadState.offset)
                )
            }
        }

        // Earth
        Image(
            painter = painterResource(id = R.drawable.foreground_earth),
            contentScale = ContentScale.FillBounds,
            contentDescription = "Earth",
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.77f)
        )

        // Send road reset action when road dismissed.
        if (state.playZoneSize.first > 0) {
            state.roadStateList.forEachIndexed { index, roadState ->
                LogUtil.printLog(message = "Road offset:${roadState.offset}")
                if (roadState.offset <= - TempRoadWidthOffset) {
                    // Road need reset.
                    LogUtil.printLog(message = "Road reset")
                    viewModel.dispatch(GameAction.RoadExit, roadIndex = index)
                }
            }
        }
    }
}

@Preview(widthDp = 411, heightDp = 180)
@Composable
fun previewForeground() {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Divider(
            color = GroundDividerPurple,
            thickness = 5.dp
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            // Road
            Image(
                painter = painterResource(id = R.drawable.foreground_road),
                contentScale = ContentScale.FillBounds,
                contentDescription = "Road",
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.23f)
                    .offset(x = (-10).dp)
            )

            Image(
                painter = painterResource(id = R.drawable.foreground_road),
                contentScale = ContentScale.FillBounds,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.23f)
                    .offset(x = 290.dp)
            )
        }

        // Earth
        Image(
            painter = painterResource(id = R.drawable.foreground_earth),
            contentScale = ContentScale.FillBounds,
            contentDescription = "Earth",
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.77f)
        )
    }
}
