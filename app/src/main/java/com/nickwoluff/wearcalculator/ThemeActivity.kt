package com.nickwoluff.wearcalculator

import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Style
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.compose.ui.unit.dp

class ThemeActivity : WearActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CalculatorTheme { AppScaffold(timeText = { FloatingTime() }) { ThemeScreen() } }
            }
        })
    }
}

@Composable private fun ThemeScreen() {
    val appearance = AppAppearance.observe()
    val context = LocalContext.current
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val round = LocalConfiguration.current.isScreenRound
    val side = if (round) (LocalConfiguration.current.screenWidthDp * 0.052f).dp else 12.dp
    if (round) {
        val state = rememberTransformingLazyColumnState()
        val spec = rememberTransformationSpec()
        ScreenScaffold(scrollInfoProvider = remember(state) { ScrollInfoProvider(state) },
            contentPadding = PaddingValues(horizontal = side)) { padding ->
            TransformingLazyColumn(state = state, contentPadding = padding,
                rotaryScrollableBehavior = RotaryScrollableDefaults.behavior(state, hapticFeedbackEnabled = false),
                modifier = Modifier.fillMaxSize().testTag("theme_list").background(MaterialTheme.colorScheme.background)) {
                item { ListHeader(Modifier.fillMaxWidth().transformedHeight(this, spec)
                    .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(spec)) { Text(stringResource(R.string.theme_settings)) } }
                items(2) { index ->
                    ThemeOption(index, appearance, { picker = it }, Modifier.fillMaxWidth().transformedHeight(this, spec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding), SurfaceTransformation(spec))
                }
            }
        }
    } else {
        val state = rememberLazyListState()
        val focus = remember { FocusRequester() }
        ScreenScaffold(scrollInfoProvider = remember(state) { ScrollInfoProvider(state) },
            contentPadding = PaddingValues(start = side, end = side, top = 28.dp, bottom = 28.dp)) { padding ->
            LazyColumn(state = state, contentPadding = padding, verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize().testTag("theme_list").background(MaterialTheme.colorScheme.background)
                    .requestFocusOnHierarchyActive()
                    .rotaryScrollable(RotaryScrollableDefaults.behavior(state, hapticFeedbackEnabled = false), focus)) {
                item { ListHeader(Modifier.fillMaxWidth()) { Text(stringResource(R.string.theme_settings)) } }
                items(2) { index -> ThemeOption(index, appearance, { picker = it }, Modifier.fillMaxWidth()) }
            }
        }
    }
    // 嗷呜文件采用两个折叠入口，点击后打开官方单选弹窗；选择即保存并收起。
    CalculatorAlertDialog(visible = picker == "accent", onDismissRequest = { picker = null },
        modifier = Modifier.testTag("accent_dialog"),
        title = { Text(stringResource(R.string.accent_color)) }, content = {
            accents.forEachIndexed { index, accent -> item {
                RadioButton(selected = appearance.accent == accent.argb,
                    onSelect = { AppAppearance.write(context, appearance.copy(accent = accent.argb)); picker = null },
                    modifier = Modifier.fillMaxWidth().testTag("accent_$index")) { Text(accent.label) }
            } }
        })
    CalculatorAlertDialog(visible = picker == "palette", onDismissRequest = { picker = null },
        modifier = Modifier.testTag("palette_dialog"),
        title = { Text(stringResource(R.string.palette_style)) }, content = {
            PaletteStyle.entries.forEach { palette -> item {
                RadioButton(selected = appearance.palette == palette,
                    onSelect = { AppAppearance.write(context, appearance.copy(palette = palette)); picker = null },
                    modifier = Modifier.fillMaxWidth().testTag("palette_${palette.name}")) { Text(palette.label) }
            } }
        })
}

@Composable private fun ThemeOption(index: Int, appearance: Appearance, onOpen: (String) -> Unit,
    modifier: Modifier, transformation: SurfaceTransformation? = null) {
    val accent = index == 0
    Button(onClick = { onOpen(if (accent) "accent" else "palette") },
        modifier = modifier.testTag(if (accent) "accent_picker" else "palette_picker"),
        transformation = transformation, colors = ButtonDefaults.filledTonalButtonColors(),
        secondaryLabel = { Text(if (accent) accents.firstOrNull { it.argb == appearance.accent }?.label.orEmpty()
            else appearance.palette.label) },
        icon = { Icon(if (accent) Icons.Default.Palette else Icons.Default.Style,
            null, Modifier.size(ButtonDefaults.IconSize)) }
    ) { Text(stringResource(if (accent) R.string.accent_color else R.string.palette_style)) }
}
