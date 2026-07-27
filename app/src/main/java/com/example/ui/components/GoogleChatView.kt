package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.AppViewModel

@Composable
fun GoogleChatView(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    ChatTabScreen(appViewModel = viewModel, modifier = modifier)
}
