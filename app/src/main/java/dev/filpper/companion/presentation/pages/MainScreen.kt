package dev.filpper.companion.presentation.pages

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.filpper.companion.R
import dev.filpper.companion.presentation.pages.device.DeviceRoute
import dev.filpper.companion.presentation.pages.files.FilesRoute
import dev.filpper.companion.presentation.theme.FlipperTheme
import dev.filpper.companion.presentation.theme.ThemeViewModel

private enum class MainTab(val route: String, val titleRes: Int) {
    DEVICE("device", R.string.tab_device),
    FILES("files", R.string.tab_files),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onThemeResolved: (Boolean) -> Unit,
    themeViewModel: ThemeViewModel = hiltViewModel(),
) {
    val preference by themeViewModel.theme.collectAsStateWithLifecycle()
    FlipperTheme(preference = preference) { isDark ->
        SideEffect { onThemeResolved(isDark) }
        val navController = rememberNavController()
        val navBackStack by navController.currentBackStackEntryAsState()
        val current = MainTab.entries.firstOrNull {
            it.route == navBackStack?.destination?.route
        } ?: MainTab.DEVICE

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(current.titleRes)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    actions = {
                        IconButton(onClick = { themeViewModel.toggle(isDark) }) {
                            Icon(
                                if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                contentDescription = stringResource(
                                    if (isDark) R.string.switch_to_light_theme
                                    else R.string.switch_to_dark_theme,
                                ),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        MainTab.DEVICE -> Icons.Outlined.Bluetooth
                                        MainTab.FILES -> Icons.Outlined.Folder
                                    },
                                    contentDescription = stringResource(tab.titleRes),
                                )
                            }
                        )
                    }
                }
            },
        ) { insets ->
            NavHost(
                navController = navController,
                startDestination = MainTab.DEVICE.route,
                modifier = Modifier.padding(insets),
            ) {
                composable(MainTab.DEVICE.route) { DeviceRoute() }
                composable(MainTab.FILES.route) { FilesRoute() }
            }
        }
    }
}
