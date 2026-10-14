package com.nickwoluff.wearcalculator

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Watch
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import java.util.function.Consumer
import java.util.Calendar

/** Java 页面与官方 Compose 组件之间的状态桥接。 */
class SettingsUiController @JvmOverloads constructor(
    activity: ComponentActivity,
    private val version: String,
    private val onCheck: Runnable,
    private val onRelease: Runnable,
    private val onLayout: Consumer<Boolean> = Consumer { },
    private val onTheme: Runnable = Runnable { }
) {
    private var checking by mutableStateOf(false)
    private val context = activity.applicationContext
    private var autoCheck by mutableStateOf(UpdatePreferences.autoCheck(context))
    private var layoutPickerVisible by mutableStateOf(false)
    private var atListStart: () -> Boolean = { false }
    fun isAtListStart() = !layoutPickerVisible && atListStart()
    var releaseTag by mutableStateOf<String?>(null)
        private set

    val view = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            CalculatorTheme {
                AppScaffold(timeText = { FloatingTime() }) {
                    SettingsScreen()
                }
            }
        }
    }

    fun updateChecking(value: Boolean) {
        checking = value
        if (value) releaseTag = null
    }

    fun showRelease(tag: String) { releaseTag = tag }

    @Composable
    private fun SettingsScreen() {
        val config = LocalConfiguration.current
        val side = if (config.isScreenRound) (config.screenWidthDp * 0.052f).dp else 12.dp
        if (config.isScreenRound) {
            val state = rememberTransformingLazyColumnState()
            SideEffect { atListStart = { !state.canScrollBackward } }
            val spec = rememberTransformationSpec()
            ScreenScaffold(scrollInfoProvider = remember(state) { ScrollInfoProvider(state) },
                contentPadding = PaddingValues(horizontal = side)) { padding ->
                TransformingLazyColumn(state = state, contentPadding = padding,
                    modifier = Modifier.fillMaxSize().testTag("settings_list").background(MaterialTheme.colorScheme.background)) {
                    item {
                        ListHeader(modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                            .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                            transformation = SurfaceTransformation(spec)) { Text(stringResource(R.string.settings_title)) }
                    }
                    item {
                        UpdateButton(Modifier.fillMaxWidth().transformedHeight(this, spec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                            SurfaceTransformation(spec))
                    }
                    if (releaseTag != null) {
                        item {
                            ReleaseButton(Modifier.fillMaxWidth().transformedHeight(this, spec)
                                .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                                SurfaceTransformation(spec))
                        }
                    }
                    item {
                        AutoUpdateSwitch(Modifier.fillMaxWidth().transformedHeight(this, spec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                            SurfaceTransformation(spec))
                    }
                    item {
                        ThemeButton(Modifier.fillMaxWidth().transformedHeight(this, spec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding), SurfaceTransformation(spec))
                    }
                    item {
                        LayoutPickerButton(Modifier.fillMaxWidth().transformedHeight(this, spec)
                            .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
                            SurfaceTransformation(spec))
                    }
                    item { CopyrightText(Modifier.fillMaxWidth()) }
                }
            }
        } else {
            val state = rememberLazyListState()
            SideEffect { atListStart = { !state.canScrollBackward } }
            val focus = remember { FocusRequester() }
            ScreenScaffold(scrollInfoProvider = remember(state) { ScrollInfoProvider(state) },
                contentPadding = PaddingValues(start = side, end = side, top = 28.dp, bottom = 28.dp)) { padding ->
                LazyColumn(state = state, contentPadding = padding,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize().testTag("settings_list").background(MaterialTheme.colorScheme.background)
                        .requestFocusOnHierarchyActive()
                        .rotaryScrollable(RotaryScrollableDefaults.behavior(state), focus)) {
                    item { ListHeader(Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_title)) } }
                    item { UpdateButton(Modifier.fillMaxWidth()) }
                    if (releaseTag != null) item { ReleaseButton(Modifier.fillMaxWidth()) }
                    item { AutoUpdateSwitch(Modifier.fillMaxWidth()) }
                    item { ThemeButton(Modifier.fillMaxWidth()) }
                    item { LayoutPickerButton(Modifier.fillMaxWidth()) }
                    item { CopyrightText(Modifier.fillMaxWidth()) }
                }
            }
        }
        // 与 WearFiles 一致：折叠入口打开官方单选弹窗，选择后保存并收起。
        AlertDialog(visible = layoutPickerVisible, onDismissRequest = { layoutPickerVisible = false },
            modifier = Modifier.testTag("layout_dialog"),
            title = { Text(stringResource(R.string.screen_layout)) }, content = {
                items(2) { index -> LayoutButton(index == 1) }
            })
    }

    @Composable
    private fun CopyrightText(modifier: Modifier) {
        val horizontalPadding = if (LocalConfiguration.current.isScreenRound) 32.dp else 8.dp
        val copyright = remember {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            buildString {
                append("© 2026")
                if (currentYear > 2026) append('-').append(currentYear)
                append(" Nick Woluff. All Rights Reserved.")
            }
        }
        Text(copyright, modifier = modifier.padding(horizontal = horizontalPadding, vertical = 12.dp)
            .testTag("settings_copyright"), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }

    @Composable
    private fun ThemeButton(modifier: Modifier, transformation: SurfaceTransformation? = null) {
        Button(onClick = { onTheme.run() }, modifier = modifier.testTag("theme_settings"),
            transformation = transformation, colors = ButtonDefaults.filledTonalButtonColors(),
            icon = { Icon(Icons.Default.Palette, null, Modifier.size(ButtonDefaults.IconSize)) }) {
            Text(stringResource(R.string.theme_settings))
        }
    }

    @Composable
    private fun LayoutPickerButton(modifier: Modifier, transformation: SurfaceTransformation? = null) {
        val round = LocalConfiguration.current.isScreenRound
        Button(onClick = { layoutPickerVisible = true }, modifier = modifier.testTag("layout_picker"),
            transformation = transformation, colors = ButtonDefaults.filledTonalButtonColors(),
            secondaryLabel = { Text(stringResource(if (round) R.string.layout_round else R.string.layout_square)) },
            icon = { Icon(Icons.Default.Watch, null, Modifier.size(ButtonDefaults.IconSize)) }) {
            Text(stringResource(R.string.screen_layout))
        }
    }

    @Composable
    private fun LayoutButton(round: Boolean) {
        RadioButton(selected = LocalConfiguration.current.isScreenRound == round,
            onSelect = { layoutPickerVisible = false; onLayout.accept(round) },
            modifier = Modifier.fillMaxWidth().testTag(if (round) "layout_round" else "layout_square")) {
            Text(stringResource(if (round) R.string.layout_round else R.string.layout_square))
        }
    }

    @Composable
    private fun UpdateButton(modifier: Modifier, transformation: SurfaceTransformation? = null) {
        Button(onClick = { onCheck.run() }, enabled = !checking,
            modifier = modifier.testTag("check_update"), transformation = transformation,
            colors = ButtonDefaults.filledTonalButtonColors(),
            secondaryLabel = { Text(stringResource(R.string.current_version, version)) },
            icon = {
                if (checking) CircularProgressIndicator(modifier = Modifier.size(ButtonDefaults.IconSize))
                else Icon(Icons.Default.Update, contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize))
            }) {
            Text(stringResource(if (checking) R.string.checking_update else R.string.check_update))
        }
    }

    @Composable
    private fun AutoUpdateSwitch(modifier: Modifier, transformation: SurfaceTransformation? = null) {
        val round = LocalConfiguration.current.isScreenRound
        val labelStyle = if (round) MaterialTheme.typography.labelMedium
            else MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp)
        val descriptionStyle = if (round) MaterialTheme.typography.labelSmall
            else MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp)
        SwitchButton(checked = autoCheck,
            onCheckedChange = { autoCheck = it; UpdatePreferences.setAutoCheck(context, it) },
            modifier = modifier.testTag("auto_check_update"), transformation = transformation,
            contentPadding = if (round) SwitchButtonDefaults.ContentPadding
                else PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            icon = { Icon(Icons.Default.Autorenew, null, Modifier.size(ButtonDefaults.IconSize)) },
            secondaryLabel = {
                Text(stringResource(if (autoCheck) R.string.auto_update_enabled else R.string.auto_update_disabled),
                    style = descriptionStyle)
            }
        ) { Text(stringResource(R.string.auto_check_update), style = labelStyle) }
    }

    @Composable
    private fun ReleaseButton(modifier: Modifier, transformation: SurfaceTransformation? = null) {
        Button(onClick = { onRelease.run() }, modifier = modifier.testTag("open_release"),
            transformation = transformation,
            icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(ButtonDefaults.IconSize)) },
            secondaryLabel = {
                Column {
                    Text(stringResource(R.string.new_version, releaseTag.orEmpty()))
                    Text(stringResource(R.string.update_instructions))
                }
            }) { Text(stringResource(R.string.open_release)) }
    }
}
