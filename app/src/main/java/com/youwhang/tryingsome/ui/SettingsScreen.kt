package com.youwhang.tryingsome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.youwhang.tryingsome.AppGraph
import com.youwhang.tryingsome.api.friendlyError
import com.youwhang.tryingsome.net.BypassMode
import com.youwhang.tryingsome.net.DnsMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onDone: (changed: Boolean) -> Unit) {
    val original = remember { AppGraph.settings }
    var s by remember { mutableStateOf(AppGraph.settings) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun finish() {
        AppGraph.update(s)
        onDone(s != original)
    }
    BackHandler { finish() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = { finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Section("차단 우회 방식")
            BypassMode.entries.forEach { mode ->
                RadioRow(
                    selected = s.bypass == mode,
                    title = mode.label,
                    desc = mode.desc,
                    onClick = { s = s.copy(bypass = mode) },
                )
            }

            Divider()
            Section("DNS (주소 찾기)")
            DnsMode.entries.forEach { mode ->
                RadioRow(selected = s.dns == mode, title = mode.label, desc = null, onClick = { s = s.copy(dns = mode) })
            }

            Divider()
            Section("연결 테스트")
            Text(
                "지금 설정으로 e621에 접속해 봐요. 안 되면 우회 방식을 바꿔 가며 테스트해 보세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = !testing, onClick = {
                    AppGraph.update(s)
                    testing = true
                    testResult = null
                    scope.launch {
                        val start = System.currentTimeMillis()
                        testResult = try {
                            AppGraph.api.posts("", 1, limit = 1)
                            val ms = System.currentTimeMillis() - start
                            "✅ 접속 성공 (${ms}ms)"
                        } catch (e: Exception) {
                            "❌ " + friendlyError(e)
                        }
                        testing = false
                    }
                }) { Text("테스트") }
                Spacer(Modifier.width(12.dp))
                if (testing) CircularProgressIndicator(Modifier.width(24.dp).height(24.dp))
            }
            testResult?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            Divider()
            Section("보기")
            Row(
                Modifier.fillMaxWidth().clickable { s = s.copy(safeMode = !s.safeMode) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("e926 모드 (전체 이용가만)")
                    Text(
                        "성인 게시물을 빼고 보여줘요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.safeMode, onCheckedChange = { s = s.copy(safeMode = it) })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = s.blacklist,
                onValueChange = { s = s.copy(blacklist = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("블랙리스트 태그") },
                supportingText = { Text("띄어쓰기로 구분. 예: gore scat rating:e") },
                minLines = 2,
            )

            Divider()
            Section("e621 계정 (선택)")
            Text(
                "로그인하면 비로그인 상태에서 숨겨지는 게시물도 볼 수 있어요. API 키는 e621 웹사이트의 계정 설정 > API Access에서 만들 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = s.username,
                onValueChange = { s = s.copy(username = it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("사용자 이름") },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = s.apiKey,
                onValueChange = { s = s.copy(apiKey = it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("API 키") },
                visualTransformation = PasswordVisualTransformation(),
            )

            Divider()
            Text(
                "플래시 재생: Ruffle (ruffle.rs, MIT/Apache-2.0)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun RadioRow(selected: Boolean, title: String, desc: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title)
            if (desc != null) {
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
