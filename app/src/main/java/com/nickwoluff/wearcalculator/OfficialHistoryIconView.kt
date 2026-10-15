package com.nickwoluff.wearcalculator

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme

/** 承载 Material 图标库的官方 History 图标。 */
class OfficialHistoryIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {
    init {
        contentDescription = context.getString(R.string.history_title)
    }

    @Composable
    override fun Content() {
        CalculatorTheme {
            val appearance = AppAppearance.observe()
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = stringResource(R.string.history_title),
                modifier = Modifier.fillMaxSize(),
                tint = if (appearance.accent == CLASSIC_ACCENT) Color(0xffb0b0b0)
                    else MaterialTheme.colorScheme.primary
            )
        }
    }
}
