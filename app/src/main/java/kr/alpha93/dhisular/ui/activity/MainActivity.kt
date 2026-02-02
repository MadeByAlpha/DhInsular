package kr.alpha93.dhisular.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.oasisfeng.common.app.AppListProvider.PackageChangeObserver
import com.oasisfeng.island.R
import com.oasisfeng.island.data.IslandAppInfo
import com.oasisfeng.island.data.IslandAppListProvider
import com.oasisfeng.island.util.Users
import kr.alpha93.dhisular.ui.theme.DhisularTheme


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        IslandAppListProvider.getInstance(this).registerObserver(mAppChangeObserver)

        setContent {
            DhisularTheme {
                AppContent()
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        IslandAppListProvider.getInstance(this).unregisterObserver(mAppChangeObserver)
        super.onDestroy()
    }

    @Composable
    private fun AppContent() {
        val navController = rememberNavController()
        var currentTab by rememberSaveable { mutableIntStateOf(Destination.APPS.ordinal) }

        Scaffold(
            topBar = {
                MediumTopAppBar(
                    title = { Text(stringResource(R.string.app_name)) }
                )
            }
        ) { innerPadding ->
            PrimaryTabRow(
                selectedTabIndex = currentTab,
                modifier = Modifier.padding(innerPadding)
            ) {
                Destination.entries.forEachIndexed { i, dest ->
                    Tab(
                        selected = currentTab == i,
                        onClick = {
                            navController.navigate(route = dest.route)
                            currentTab = i
                        },
                        text = { Text(stringResource(dest.resId)) },
                    )
                }
            }
            NavHost(
                navController = navController,
                startDestination = Destination.APPS.route,
            ) {
                Destination.entries.forEach { destination ->
                    composable(destination.route) {
                        when (destination) {
                            Destination.APPS -> AppList()
                            Destination.MANAGED_APPS -> LazyColumn { }
                            Destination.POLICY -> LazyColumn { }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AppList(modifier: Modifier = Modifier) {
        val provider = remember { IslandAppListProvider.getInstance(this).installedApps(Users.profile!!).toList().sortedBy { it.label } }

        LazyColumn(modifier = modifier) {
            items(provider) {
                ListItem(
                    selected = false,
                    onClick = {},
                    leadingContent = { Image(painter = rememberDrawablePainter(it.loadUnbadgedIcon(packageManager))) },
                    supportingContent = { Text(text = it.packageName) }
                ) {
                    Text(text = it.label)
                }
            }
        }
    }

    enum class Destination(
        val route: String,
        val resId: Int,
    ) {
        APPS("mainland", com.oasisfeng.island.shared.R.string.mainland_name),
        MANAGED_APPS("island", R.string.tab_island),
        POLICY("policy", R.string.tab_discovery);
    }

    val mAppChangeObserver: PackageChangeObserver<IslandAppInfo?> = object : PackageChangeObserver<IslandAppInfo?> {
        override fun onPackageUpdate(apps: MutableCollection<IslandAppInfo?>) {
            invalidateOptionsMenu()
        }

        override fun onPackageRemoved(apps: MutableCollection<IslandAppInfo?>) {
            invalidateOptionsMenu()
        }
    }

}
