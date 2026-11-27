package app.lumen.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
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
import androidx.compose.material3.Icon
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
import com.gpo.yoin.symbols.YoinSymbols

@Composable
fun ConsentScreen(
    termsRead: Boolean,
    privacyRead: Boolean,
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
                style = MaterialTheme.typography.displaySmallEmphasized,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                L10n.tf(
                    "使用 Lumen 前，请阅读并同意用户协议与隐私条款。条款说明应用如何处理账号与曲库数据。",
                    "Before using Lumen, please read and accept the Terms of Service and Privacy Policy. They explain how accounts and library data are handled.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 10.dp),
            )

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConsentPoint(L10n.tf("账号与曲库", "Account and library"), L10n.tf("服务器地址、用户名与密码用于连接你自建的 Navidrome；密码使用 Android Keystore 加密。", "Server address, username and password connect to your own Navidrome; the password is encrypted with the Android Keystore."))
                    ConsentPoint(L10n.tf("本机数据", "On-device data"), L10n.tf("播放历史只保存在本机；音频不会上传。", "Play history stays on device; audio is never uploaded."))
                    ConsentPoint(L10n.tf("你的选择", "Your choices"), L10n.tf("你可以随时在设置中清除本机数据，或卸载应用。", "You can clear local data in Settings or uninstall the app at any time."))
                }
            }

            val remaining = listOfNotNull(
                L10n.tf("用户协议", "Terms of Service").takeUnless { termsRead },
                L10n.tf("隐私条款", "Privacy Policy").takeUnless { privacyRead },
            )
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConsentStep(
                        number = "1",
                        title = L10n.tf("用户协议", "Terms of Service"),
                        read = termsRead,
                        onClick = onOpenTerms,
                    )
                    ConsentStep(
                        number = "2",
                        title = L10n.tf("隐私条款", "Privacy Policy"),
                        read = privacyRead,
                        onClick = onOpenPrivacy,
                    )
                }
            }

            Text(
                if (remaining.isEmpty()) {
                    L10n.tf("两项均已阅读完成，可以同意并进入应用", "Both documents read. You can agree and continue.")
                } else {
                    L10n.tf(
                        "还需完成：${remaining.joinToString("、")}（分别打开并滚动到最底部）",
                        "Still needed: ${remaining.joinToString(", ")} (open each and scroll to the bottom)",
                    )
                },
                color = if (remaining.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { if (termsRead && privacyRead) onAccept() },
                enabled = termsRead && privacyRead,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = CircleShape,
            ) {
                Text(
                    L10n.tf("同意并进入应用", "Agree and enter"),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
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
private fun ConsentStep(
    number: String,
    title: String,
    read: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    if (read) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (read) {
                Icon(
                    YoinSymbols.Check,
                    contentDescription = L10n.t("已完成"),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Text(
                    number,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmallEmphasized,
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                if (read) L10n.tf("已阅读至底部", "Read to the bottom")
                else L10n.tf("打开并滚动到最底部", "Open and scroll to the bottom"),
                color = if (read) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
        Text(
            if (read) L10n.tf("已完成", "Done") else L10n.tf("去阅读", "Read"),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ConsentPoint(title: String, body: String) {
    Column {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 17.sp)
    }
}
