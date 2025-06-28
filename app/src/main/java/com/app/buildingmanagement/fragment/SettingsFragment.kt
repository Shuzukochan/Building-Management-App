package com.app.buildingmanagement.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.app.buildingmanagement.fragment.ui.settings.ComposeSettings

@androidx.compose.runtime.Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {}
) {
    ComposeSettings(onNavigateBack = onNavigateBack)
}

class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ComposeSettings()
            }
        }
    }
}