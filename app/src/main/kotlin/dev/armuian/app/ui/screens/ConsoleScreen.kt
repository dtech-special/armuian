package dev.armuian.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.armuian.app.R
import dev.armuian.app.ui.LocalBottomBarPadding
import dev.armuian.app.ui.LocalMonoFont
import dev.armuian.app.ui.LogView
import dev.armuian.app.ui.MainViewModel
import dev.armuian.core.ArmLog

private const val LOGO = "╔═╗╦═╗╔╦╗╦ ╦╦╔═╗╔╗╔\n╠═╣╠╦╝║║║║ ║║╠═╣║║║\n╩ ╩╩╚═╩ ╩╚═╝╩╩ ╩╝╚╝"

@Composable
fun ConsoleScreen(vm: MainViewModel, onCopy: (String) -> Unit) {
    val entries by ArmLog.entries.collectAsStateWithLifecycle()
    val bottomPad = LocalBottomBarPadding.current

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(LOGO, fontFamily = LocalMonoFont.current, fontSize = 14.sp, lineHeight = 16.sp, softWrap = false, color = MaterialTheme.colorScheme.primary)
        LogView(entries, onCopy, Modifier.weight(1f))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp + bottomPad), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = vm::diagRoot, label = { Text(stringResource(R.string.diag_root)) })
            AssistChip(onClick = vm::diagCompat, label = { Text(stringResource(R.string.diag_compat)) })
            AssistChip(onClick = vm::diagDevice, label = { Text(stringResource(R.string.diag_device)) })
        }
    }
}
