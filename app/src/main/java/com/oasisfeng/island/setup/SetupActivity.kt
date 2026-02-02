package com.oasisfeng.island.setup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.oasisfeng.island.R
import kr.alpha93.dhisular.ui.theme.DhisularTheme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Island setup wizard
 * 
 * Created by Oasis on 2016/9/13.
 */
class SetupActivity : ComponentActivity() {

    private val setup by lazy { SetupViewModel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            val view = LocalView.current

            SetupScreen(setup.message, setup.message_params, R.string.button_instructions_online) {
                setup.onExtraButtonClick(view)
            }
        }
    }

    @Composable
    fun SetupScreen(
        // ViewModel에서 State를 호이스팅해서 전달받는 것이 좋습니다.
        messageCode: Int, // setup.message
        messageParams: Array<Any>?, // setup.message_params (가정)
        actionExtra: Int, // setup.action_extra
        onExtraButtonClick: () -> Unit
    ) {
        val scrollState = rememberScrollState()
        val activity = LocalActivity.current

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                // SetupWizardLayout의 헤더(app:suwHeaderText) 대체
                CenterAlignedTopAppBar(
                    title = { }, // 보통 위자드 첫 화면은 타이틀을 본문에 크게 적으므로 비워두거나 설정
                )
            },
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        setup.onNavigateNext(activity)
                    }) { Text(stringResource(R.string.setup_accept)) }
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp) // 위자드 스타일의 좌우 여백
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.Start // 기본적으로 왼쪽 정렬 (취향에 따라 Center)
            ) {
                // 1. 헤더 (기존 app:suwHeaderText)
                // SetupWizardLayout은 보통 아이콘과 큰 제목을 가집니다.
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(id = R.string.setup_profile_welcome), // @string/setup_profile_welcome
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(24.dp))

                // 2. 본문 분기 로직
                if (messageCode == 0) {
                    // --- Intro Section ---
                    // bind:shown="@{setup.message == 0}"
                    Text(
                        text = stringResource(id = R.string.setup_island_intro), // @string/setup_island_intro
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // --- Error / Message Section ---
                    // bind:shown="@{setup.message != 0}"

                    // 기존 TextFormat.getText(...) 호출
                    // Compose에서 구형 Java 유틸리티 호출 시 context가 필요하면 LocalContext 사용
                    val formattedText = remember(messageCode, messageParams) {
                        // 실제 코드: TextFormat.getText(context, messageCode, messageParams).toString()
                        "Error Message Placeholder: $messageCode" // 임시 대체
                    }

                    Text(
                        text = formattedText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error // 에러 메시지 느낌 강조
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Manual Setup 안내 문구
                    // bind:shown="@{setup.action_extra == R.string.button_instructions_online}"
                    if (actionExtra == R.string.button_instructions_online) {
                        Text(
                            text = stringResource(id = R.string.dialog_manual_setup),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    // Action Button (Borderless)
                    // bind:shown="@{setup.action_extra != 0}"
                    if (actionExtra != 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = onExtraButtonClick,
                            // style="@style/AppTheme.BorderlessActionButton" 대체
                        ) {
                            Text(
                                text = if (actionExtra != 0) stringResource(id = actionExtra) else "",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    private fun PreviewSetupScreen() {
        DhisularTheme {
            SetupScreen(
                messageCode = 0, // 0이면 Intro, 아니면 Error
                messageParams = null,
                actionExtra = 0, // R.string.button_instructions_online 등
                onExtraButtonClick = {}
            )
        }
    }

}
