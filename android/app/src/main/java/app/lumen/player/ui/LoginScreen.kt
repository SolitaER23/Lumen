package app.lumen.player.ui

import app.lumen.player.util.L10n
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumen.player.data.CleartextVerdict
import app.lumen.player.data.NetworkPolicy
import app.lumen.player.data.ServerConfig

@Composable
fun LoginScreen(
    initial: ServerConfig,
    loading: Boolean,
    error: String?,
    onRequestLocalNetwork: () -> Unit = {},
    allowPublicCleartext: Boolean = false,
    onAllowPublicCleartextChange: (Boolean) -> Unit = {},
    onSubmit: (ServerConfig) -> Unit,
) {
    var base by remember { mutableStateOf(initial.base.ifBlank { "http://" }) }
    var user by remember { mutableStateOf(initial.user) }
    var pass by remember { mutableStateOf(initial.password) }
    // 明文 HTTP 策略：私网放行（自托管常态），公网必须显式确认
    val verdict = remember(base, allowPublicCleartext) {
        NetworkPolicy.evaluate(base, allowPublicCleartext)
    }
    val haptics = rememberLumenHaptics()
    // 连接失败时给一次"被拒绝"的反馈，比只弹一行红字更容易被注意到
    androidx.compose.runtime.LaunchedEffect(error) {
        if (!error.isNullOrBlank()) haptics.reject()
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("LUMEN", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, letterSpacing = 3.sp)
        Text(
            L10n.t("连接曲库"),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.displaySmallEmphasized,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = base, onValueChange = { base = it }, label = { Text(L10n.t("服务器地址")) },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )
        when (verdict) {
            CleartextVerdict.PUBLIC_CLEARTEXT -> {
                Text(
                    L10n.tf(
                        "该地址在公网使用明文 HTTP。Subsonic 的 token 可被链路上的人截获并长期冒用你的账号，强烈建议改用 HTTPS。",
                        "This public address uses plain HTTP. Subsonic tokens can be captured on the network and replayed against your account. HTTPS is strongly recommended.",
                    ),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = allowPublicCleartext,
                        onCheckedChange = {
                            haptics.toggle(it)
                            onAllowPublicCleartextChange(it)
                        },
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        L10n.tf("我了解风险，仍允许明文 HTTP", "I understand the risk; allow plain HTTP"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
            CleartextVerdict.OK -> if (base.trim().lowercase().startsWith("http://")) {
                Text(
                    L10n.tf(
                        "HTTP 连接不加密。请仅在可信局域网使用，或为服务器配置 HTTPS。",
                        "HTTP is not encrypted. Use it only on a trusted LAN, or enable HTTPS on your server.",
                    ),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
            CleartextVerdict.INVALID -> Unit
        }
        OutlinedTextField(
            value = user, onValueChange = { user = it }, label = { Text(L10n.t("用户名")) },
            singleLine = true, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pass, onValueChange = { pass = it }, label = { Text(L10n.t("密码")) },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        )
        if (initial.passwordUnreadable && pass.isBlank()) {
            Text(
                L10n.tf(
                    "已保存的密码无法解密（系统密钥库被重置或密文损坏）。请重新输入密码。",
                    "The saved password could not be decrypted (keystore reset or corrupted). Please enter it again.",
                ),
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        // Android 17（含 HyperOS 等定制系统）默认拒绝「本地网络」权限时，连接会表现为"无法连接服务器"，
        // 这里直接给出授权入口，避免用户以为是服务器问题。
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val localNetGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            ctx,
            "android.permission.ACCESS_LOCAL_NETWORK",
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!localNetGranted) {
            Text(
                L10n.tf(
                    "系统未授予「本地网络」权限，连接局域网服务器会被拦截。请点击下方按钮授权后重试。",
                    "The system has not granted the Local Network permission; LAN connections will be blocked.",
                ),
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            Button(
                onClick = {
                    haptics.tap()
                    onRequestLocalNetwork()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Text(L10n.t("打开设置授予本地网络权限"), fontSize = 14.sp)
            }
        }
        Button(
            onClick = {
                haptics.confirm()
                onSubmit(ServerConfig(base.trim(), user.trim(), pass))
            },
            enabled = !loading && verdict == CleartextVerdict.OK &&
                user.isNotBlank() && pass.isNotBlank(),
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Text(L10n.t("连接并同步"), fontSize = 15.sp)
        }
    }
}
