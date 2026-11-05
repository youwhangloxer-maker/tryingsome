package com.youwhang.tryingsome.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.youwhang.tryingsome.AppGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (AppGraph.settings.ageConfirmed) AppRoot() else AgeGate()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel()
    NavHost(navController = nav, startDestination = "search") {
        composable("search") {
            SearchScreen(
                vm = vm,
                onOpen = { index -> nav.navigate("post/$index") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable(
            "post/{index}",
            arguments = listOf(navArgument("index") { type = NavType.IntType }),
        ) { entry ->
            PostScreen(
                vm = vm,
                startIndex = entry.arguments?.getInt("index") ?: 0,
                onBack = { nav.popBackStack() },
                onTag = { tag ->
                    vm.search(tag)
                    nav.popBackStack("search", inclusive = false)
                },
            )
        }
        composable("settings") {
            SettingsScreen(onDone = { changed ->
                if (changed) vm.reload()
                nav.popBackStack()
            })
        }
    }
}

@Composable
private fun AgeGate() {
    val activity = LocalContext.current as? Activity
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("성인 확인", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(
            "이 앱으로 보는 e621에는 성인 콘텐츠가 많아요. 만 18세 이상만 사용할 수 있어요.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { activity?.finish() }) { Text("나가기") }
            Button(onClick = { AppGraph.update(AppGraph.settings.copy(ageConfirmed = true)) }) {
                Text("만 18세 이상이에요")
            }
        }
    }
}
