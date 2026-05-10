package fr.axllvy.insane.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CompletableDeferred

/**
 * Glue between [AndroidNotificationScheduler.requestPermission] (suspend, called
 * from a click handler in a Composable) and Android's permission API (requires
 * an [ActivityResultLauncher] registered before [ComponentActivity.onStart]).
 *
 * MainActivity registers the launcher via [attach]; the scheduler resolves the
 * suspending request through the deferred set up here.
 */
object AndroidPermissionRequester {
    private var launcher: ActivityResultLauncher<String>? = null
    private var pending: CompletableDeferred<Boolean>? = null

    // Lint flags this for Fragment <1.3.0; we use ComponentActivity (androidx.activity 1.10.1).
    @SuppressLint("InvalidFragmentVersionForActivityResult")
    fun attach(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            pending?.complete(granted)
            pending = null
        }
    }

    fun detach() {
        pending?.complete(false)
        pending = null
        launcher = null
    }

    suspend fun request(): PermissionResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return PermissionResult.Granted
        val l = launcher ?: return PermissionResult.Unavailable
        val deferred = CompletableDeferred<Boolean>()
        pending?.complete(false)
        pending = deferred
        l.launch(Manifest.permission.POST_NOTIFICATIONS)
        return if (deferred.await()) PermissionResult.Granted else PermissionResult.Denied
    }
}
