// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.ui.activities

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import io.bitdrift.gradletestapp.R
import io.bitdrift.gradletestapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        navController = findNavController(R.id.nav_host_fragment_content_main)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.FirstFragment) {
                supportActionBar?.hide()
            } else {
                supportActionBar?.show()
            }
            invalidateOptionsMenu()
        }
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.pipContainer.setContent {
            PipContent()
        }

        updatePipParams()
    }

    @androidx.compose.runtime.Composable
    private fun PipContent() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "PiP Mode",
                color = Color.White,
                fontSize = 24.sp
            )
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipMode()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        updateUiForPipMode(isInPictureInPictureMode)
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(9, 16))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(9, 16))
                .setAutoEnterEnabled(true)
                .setSeamlessResizeEnabled(true)
                .build()
            setPictureInPictureParams(params)
        }
    }

    private fun updateUiForPipMode(isInPipMode: Boolean) {
        val contentContainer = findViewById<View>(R.id.content_main_container)
        if (isInPipMode) {
            supportActionBar?.hide()
            contentContainer?.visibility = View.GONE
            binding.pipContainer.visibility = View.VISIBLE
        } else {
            binding.pipContainer.visibility = View.GONE
            contentContainer?.visibility = View.VISIBLE
            navController.currentDestination?.let { destination ->
                if (destination.id != R.id.FirstFragment) {
                    supportActionBar?.show()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean =
        if (isNotEntryPointFragment()) {
            false
        } else {
            menuInflater.inflate(R.menu.menu_main, menu)
            true
        }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                navController.navigate(R.id.action_FirstFragment_to_ConfigFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfiguration) ||
            super.onSupportNavigateUp()

    private fun isNotEntryPointFragment(): Boolean = navController.currentDestination?.id != R.id.FirstFragment
}
