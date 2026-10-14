package com.nickwoluff.wearcalculator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.*

@Composable fun FloatingTime() {
    // 官方时间源的注销发生在协程取消时，使用应用 Context 避免 Activity
    // 销毁时系统先行清理 Receiver 与协程注销之间的竞态。
    CompositionLocalProvider(LocalContext provides LocalContext.current.applicationContext) {
        if (LocalConfiguration.current.isScreenRound) {
            TimeText(modifier = Modifier.testTag("floating_time"))
        } else {
            val source = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat())
            val style = TimeTextDefaults.timeTextStyle()
            Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.TopCenter) {
                Text(source.currentTime(),
                    modifier = Modifier.testTag("floating_time").clearAndSetSemantics { }
                        .background(TimeTextDefaults.backgroundColor(), RoundedCornerShape(50)).padding(horizontal = 4.dp),
                    style = TextStyle(color = style.color, fontSize = style.fontSize,
                        fontFamily = style.fontFamily, fontWeight = style.fontWeight))
            }
        }
    }
}
