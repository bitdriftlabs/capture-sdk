// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradletestapp.init

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Makes saved profiles readable by `adb`. The system writes profiles to the app's internal storage,
 * which adb reads through `run-as` on a debuggable build or after `adb root` on a userdebug or eng
 * device. Anywhere else a profile is only reachable after it is copied to the app's external files
 * directory, which this class does on request.
 */
class ProfileExporter(
    private val packageName: String,
    private val profilesDir: () -> File,
    private val exportDir: () -> File?,
    val access: AdbAccess,
) {
    /** How adb can read this app's saved profiles on this device. */
    enum class AdbAccess {
        RUN_AS,
        ROOT,
        EXPORT,
    }

    sealed interface Result {
        /** [adbCommand] pulls the exported file, or the whole export directory for a bulk export. */
        data class Exported(
            val copied: Int,
            val alreadyExported: Int,
            val adbCommand: String,
        ) : Result

        data class Failed(
            val reason: String,
        ) : Result
    }

    /** The adb command that reads [profile] in place, or null when it has to be exported first. */
    fun adbCommand(profile: File): String? =
        when (access) {
            AdbAccess.RUN_AS -> "adb exec-out run-as $packageName cat files/$PROFILING_DIR/${profile.name} > ${profile.name}"
            AdbAccess.ROOT -> "adb root && adb pull ${profile.path}"
            AdbAccess.EXPORT -> null
        }

    suspend fun export(profile: File): Result = withContext(Dispatchers.IO) { copy(listOf(profile)) }

    suspend fun exportAll(): Result =
        withContext(Dispatchers.IO) {
            copy(profilesDir().listFiles()?.filter(File::isFile).orEmpty())
        }

    /** Deletes every exported profile and returns how many were removed. */
    suspend fun clearExported(): Int =
        withContext(Dispatchers.IO) {
            exportDir()?.listFiles()?.count { it.isFile && it.delete() } ?: 0
        }

    private fun copy(profiles: List<File>): Result {
        if (profiles.isEmpty()) return Result.Failed("no saved profiles")
        val destination = exportDir() ?: return Result.Failed("external storage is not available")
        var copied = 0
        for (profile in profiles) {
            val target = File(destination, profile.name)
            if (target.exists() && target.length() == profile.length()) continue
            // Copy under a temporary name so an interrupted copy never looks like a complete profile.
            val partial = File(destination, "${profile.name}$PARTIAL_SUFFIX")
            try {
                profile.copyTo(partial, overwrite = true)
                if (!partial.renameTo(target)) throw IOException("could not rename ${partial.name}")
                copied++
            } catch (e: IOException) {
                partial.delete()
                return Result.Failed("${profile.name}: ${e.message}")
            }
        }
        val pullTarget = profiles.singleOrNull()?.let { File(destination, it.name).path } ?: "${destination.path}/"
        return Result.Exported(copied, profiles.size - copied, "adb pull $pullTarget")
    }

    companion object {
        private const val PROFILING_DIR = "profiling"
        private const val PARTIAL_SUFFIX = ".part"

        fun create(context: Context): ProfileExporter {
            val access =
                when {
                    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 -> AdbAccess.RUN_AS
                    Build.TYPE != "user" -> AdbAccess.ROOT
                    else -> AdbAccess.EXPORT
                }
            return ProfileExporter(
                packageName = context.packageName,
                profilesDir = { File(context.filesDir, PROFILING_DIR) },
                exportDir = { context.getExternalFilesDir(PROFILING_DIR) },
                access = access,
            )
        }
    }
}
