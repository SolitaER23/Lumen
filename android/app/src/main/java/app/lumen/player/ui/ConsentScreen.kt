package app.lumen.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.util.L10n

@Composable
fun ConsentScreen(
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                )
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                "LUMEN",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                letterSpacing = 3.sp,
            )
            Text(
                L10n.tf("开始之前", "Before we start"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 38.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                L10n.tf(
                    "使用 Lumen 前，请阅读并同意用户协议与隐私条款。条款说明应用如何处理账号、曲库与端侧分析数据。",
                    "Before using Lumen, please read and accept the Terms of Service and Privacy Policy. They explain how accounts, library data and on-device analysis are handled.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 10.dp),
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConsentPoint(L10n.tf("账号与曲库", "Account and library"), L10n.tf("服务器地址、用户名与密码用于连接你自建的 Navidrome；密码使用 Android Keystore 加密。", "Server address, username and password connect to your own Navidrome; the password is encrypted with the Android Keystore."))
                    ConsentPoint(L10n.tf("端侧分析", "On-device analysis"), L10n.tf("播放历史、评分与声音分析只保存在本机；音频不会上传。", "History, ratings and audio analysis stay on device; audio is never uploaded."))
                    ConsentPoint(L10n.tf("你的选择", "Your choices"), L10n.tf("你可以随时在设置中清除本机数据，或卸载应用。", "You can clear local data in Settings or uninstall the app at any time."))
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenTerms) { Text(L10n.tf("用户协议", "Terms of Service"), fontSize = 12.sp) }
                TextButton(onClick = onOpenPrivacy) { Text(L10n.tf("隐私条款", "Privacy Policy"), fontSize = 12.sp) }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = CircleShape,
            ) {
                Text(L10n.tf("同意并继续", "Agree and continue"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Text(
                    L10n.tf("暂不同意并退出", "Decline and exit"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            Text(
                L10n.tf("继续即表示你已阅读并同意上述条款。", "Continuing means you have read and accepted these terms."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ConsentPoint(title: String, body: String) {
    Column {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp)
    }
}
