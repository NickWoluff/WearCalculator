package com.nickwoluff.wearcalculator

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text

/** 启动检查仅在发现新版本时显示官方 Wear 弹窗。 */
class StartupUpdateUiController(activity: ComponentActivity, model: StartupUpdateViewModel) {
    private var version by mutableStateOf<String?>(null)
    private val toast = ArcToastView(activity)

    val view = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            CalculatorTheme {
                AlertDialog(visible = version != null, onDismissRequest = model::dismiss,
                    modifier = Modifier.testTag("startup_update_dialog"),
                    title = { Text(stringResource(R.string.new_version, version.orEmpty())) },
                    text = { Text(stringResource(R.string.update_instructions)) },
                    content = {
                        item {
                            Button(onClick = {
                                model.dismiss()
                                try {
                                    activity.startActivity(Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/NickWoluff/WearCalculator/releases")))
                                } catch (_: ActivityNotFoundException) {
                                    toast.show(activity.getString(R.string.no_browser))
                                }
                            }, modifier = Modifier.fillMaxWidth().testTag("startup_open_release")) {
                                Text(stringResource(R.string.open_release))
                            }
                        }
                    })
            }
        }
    }

    init { model.availableVersion.observe(activity) { version = it } }

    fun dismissToast() = toast.dismiss()
}
