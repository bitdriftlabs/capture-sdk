// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.attributes

import android.content.Context
import android.content.pm.InstallSourceInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.nhaarman.mockitokotlin2.mock
import io.bitdrift.capture.ErrorHandler
import junit.framework.TestCase.assertEquals
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class ClientAttributesTest {
    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun foreground() {
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(appContext, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("foreground", "1")
    }

    @Test
    fun not_foreground() {
        val mockedLifecycleOwnerLifecycleStateCreated = obtainMockedLifecycleOwnerWith(Lifecycle.State.CREATED)

        val clientAttributes = ClientAttributes(appContext, mockedLifecycleOwnerLifecycleStateCreated).invoke()

        assertThat(clientAttributes).containsEntry("foreground", "0")
    }

    @Test
    fun app_id() {
        val packageName = "my.bitdrift.test"
        val context = spy(appContext)
        doReturn(packageName).`when`(context).packageName
        val mockedLifecycleOwnerLifecycleStateStarted =
            obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("app_id", packageName)
    }

    @Test
    fun app_id_unknown() {
        val context = spy(appContext)
        doReturn(null).`when`(context).packageName
        val mockedLifecycleOwnerLifecycleStateStarted =
            obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("app_id", "unknown")
    }

    @Test
    fun app_version() {
        val versionName = "1.2.3.4"
        val context = spy(appContext)
        val mockedPackageInfo = obtainMockedPackageInfo(context)
        mockedPackageInfo.versionName = versionName
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("app_version", versionName)
    }

    @Test
    fun app_version_unknown() {
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(appContext, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("app_version", "?.?.?")
    }

    @Test
    fun app_version_code() {
        val versionCode = 66
        val context = spy(appContext)
        val mockedPackageInfo = obtainMockedPackageInfo(context)
        mockedPackageInfo.versionCode = versionCode
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("_app_version_code", versionCode.toString())
    }

    @Test
    fun app_version_code_unknown() {
        val context = spy(appContext)
        val packageManager = obtainMockedPackageManager(context)
        doReturn(null).`when`(packageManager).getPackageInfo(anyString(), eq(0))
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("_app_version_code", "-1")
    }

    @Test
    @Config(sdk = [28])
    fun app_version_code_android_pie() {
        val versionCode = 66L
        val context = spy(appContext)
        val mockedPackageInfo = obtainMockedPackageInfo(context)
        doReturn(versionCode).`when`(mockedPackageInfo).longVersionCode
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("_app_version_code", versionCode.toString())
    }

    @Test
    @Config(sdk = [28])
    fun app_version_code_unknown_android_pie() {
        val context = spy(appContext)
        val packageManager = obtainMockedPackageManager(context)
        doReturn(null).`when`(packageManager).getPackageInfo(anyString(), eq(0))
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        val clientAttributes = ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        assertThat(clientAttributes).containsEntry("_app_version_code", "-1")
    }

    @Test
    @Config(sdk = [33])
    fun package_info_android_tiramisu() {
        val context = spy(appContext)
        val packageManager = obtainMockedPackageManager(context)
        val mockedLifecycleOwnerLifecycleStateStarted = obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED)

        ClientAttributes(context, mockedLifecycleOwnerLifecycleStateStarted).invoke()

        verify(packageManager).getPackageInfo(anyString(), any(PackageManager.PackageInfoFlags::class.java))
    }

    @Test
    @Config(sdk = [31])
    fun checkInstallationSource_viaInstallSourceInfo_shouldReturnValidInstaller() {
        val hasValidInstallationSource = true
        val expectedInstallationSource = DEFAULT_INSTALLER_PACKAGE_NAME

        assertInstallationSource(hasValidInstallationSource, expectedInstallationSource)
    }

    @Test
    @Config(sdk = [24])
    fun checkInstallationSource_viaInstallerPackageName_shouldReturnValidInstaller() {
        val hasValidInstallationSource = true
        val expectedInstallationSource = DEFAULT_INSTALLER_PACKAGE_NAME

        assertInstallationSource(hasValidInstallationSource, expectedInstallationSource)
    }

    @Test
    @Config(sdk = [24])
    fun checkInstallationSource_withDebugBuilds_shouldReturnDebugBuildMessage() {
        val hasValidInstallationSource = true
        val expectedInstallationSource = "Debug build installation"
        val isDebugBuild = true

        assertInstallationSource(
            hasValidInstallationSource,
            expectedInstallationSource,
            isDebugBuild,
        )
    }

    @Test
    fun checkInstallationSource_viaInstallerPackageName_shouldReturnDefaultPlaceHolder() {
        val hasValidInstallationSource = false
        val expectedInstallationSource = "unknown"

        assertInstallationSource(hasValidInstallationSource, expectedInstallationSource)
    }

    @Test
    fun architecture_withStartedLifecycle_shouldReturnArm() {
        val clientAttributes =
            ClientAttributes(appContext, obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED))

        val fields = clientAttributes.invoke()

        assertThat(fields).containsEntry("_architecture", "armeabi-v7a")
    }

    @Test
    fun osVersion_withStartedLifecycle_shouldMatchConfigVersion() {
        val clientAttributes =
            ClientAttributes(appContext, obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED))

        val fields = clientAttributes.invoke()

        assertThat(fields).containsEntry("os_version", "7.0")
    }

    @Test
    fun osApiLevel_withStartedLifecycle_shouldMatchConfigSdkInt() {
        val clientAttributes =
            ClientAttributes(appContext, obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED))

        val fields = clientAttributes.invoke()

        assertThat(fields).containsEntry("osApiLevel", "24")
    }

    private fun assertInstallationSource(
        hasValidInstallationSource: Boolean,
        expectedInstallationSource: String,
        isDebugBuild: Boolean = false,
    ) {
        val errorHandler: ErrorHandler = mock()

        val context = spy(appContext)
        val installerSourceName =
            if (isDebugBuild) {
                "Debug build installation"
            } else {
                context.applicationInfo.flags = 0
                DEFAULT_INSTALLER_PACKAGE_NAME
            }

        val packageManager =
            obtainMockedPackageManager(context).apply {
                if (hasValidInstallationSource) addInstallationSource(context, installerSourceName)
            }
        doReturn(packageManager).`when`(context).packageManager

        val attributes =
            ClientAttributes(
                context,
                obtainMockedLifecycleOwnerWith(Lifecycle.State.STARTED),
            )
        assertEquals(attributes.getInstallationSource(context, errorHandler), expectedInstallationSource)
    }

    private fun obtainMockedLifecycleOwnerWith(state: Lifecycle.State): LifecycleOwner {
        val mockedLifecycle = mock(Lifecycle::class.java)
        doReturn(state).`when`(mockedLifecycle).currentState
        val mockedLifecycleOwner = mock(LifecycleOwner::class.java)
        doReturn(mockedLifecycle).`when`(mockedLifecycleOwner).lifecycle
        return mockedLifecycleOwner
    }

    private fun obtainMockedPackageInfo(context: Context): PackageInfo {
        val mockedPackageManager = obtainMockedPackageManager(context)
        val mockedPackageInfo: PackageInfo = mock(PackageInfo::class.java)
        doReturn(mockedPackageInfo).`when`(mockedPackageManager).getPackageInfo(anyString(), eq(0))
        return mockedPackageInfo
    }

    private fun obtainMockedPackageManager(context: Context): PackageManager {
        val mockedPackageManager: PackageManager = mock(PackageManager::class.java)
        doReturn(mockedPackageManager).`when`(context).packageManager
        return mockedPackageManager
    }

    private fun PackageManager.addInstallationSource(
        context: Context,
        installerSourceName: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mockSourceInfo = mock(InstallSourceInfo::class.java)
            `when`(mockSourceInfo.installingPackageName)
                .thenReturn(
                    installerSourceName,
                )
            `when`(getInstallSourceInfo(context.packageName)).thenReturn(
                mockSourceInfo,
            )
        } else {
            `when`(getInstallerPackageName(context.packageName))
                .thenReturn(installerSourceName)
        }
    }

    private companion object {
        private const val DEFAULT_INSTALLER_PACKAGE_NAME = "com.android.vending"
    }
}
