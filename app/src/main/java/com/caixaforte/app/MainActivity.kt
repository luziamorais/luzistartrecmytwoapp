package com.caixaforte.app

import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CaixaForteApp() }
    }

    @Composable
    fun CaixaForteApp() {
        val vm = remember { VaultViewModel(this) }
        var showSetPin by remember { mutableStateOf(false) }
        var showAskPin by remember { mutableStateOf(false) }
        var pin1 by remember { mutableStateOf("") }
        var pin2 by remember { mutableStateOf("") }
        var pinInput by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            vm.loadInstalledApps()
            if (!vm.hasPin()) showSetPin = true
            else if (vm.biometricEnabled && vm.biometricAvailable()) vm.promptBiometric(this@MainActivity) { vm.unlocked = true }
        }

        MaterialTheme {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Caixa Forte") },
                        actions = {
                            IconButton(onClick = {
                                if (vm.biometricAvailable()) vm.promptBiometric(this@MainActivity) { vm.unlocked = true }
                                else Toast.makeText(this@MainActivity, "Biometria indisponível.", Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Filled.Fingerprint, contentDescription = "Biometria") }
                            IconButton(onClick = { vm.toggleBiometric() }) {
                                Icon(if (vm.biometricEnabled) Icons.Filled.LockOpen else Icons.Filled.Lock, contentDescription = "Alternar Biometria")
                            }
                        }
                    )
                }
            ) { inner ->
                Column(Modifier.padding(inner).fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (vm.unlocked) "Status: Desbloqueada ✅" else "Status: Bloqueada 🔒",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        if (!vm.unlocked) Button(onClick = { showAskPin = true }) { Text("Desbloquear (PIN)") }
                        else Button(onClick = { vm.unlocked = false }) { Text("Bloquear") }
                    }
                    Divider()
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(vm.apps) { app ->
                            AppRow(
                                label = app.label,
                                pkg = app.packageName,
                                protected = vm.isProtected(app.packageName),
                                onToggleProtect = { checked -> vm.setProtected(app.packageName, checked) },
                                onOpen = {
                                    if (!vm.unlocked && vm.isProtected(app.packageName)) {
                                        Toast.makeText(this@MainActivity, "App protegido — desbloqueie (biometria ou PIN).", Toast.LENGTH_SHORT).show()
                                    } else vm.launchApp(app.packageName)
                                }
                            )
                        }
                    }
                }

                if (showSetPin) {
                    AlertDialog(onDismissRequest = { }, confirmButton = {
                        TextButton(onClick = {
                            if (pin1.length >= 4 && pin1 == pin2) { vm.setPin(pin1); showSetPin = false; Toast.makeText(this@MainActivity, "PIN definido.", Toast.LENGTH_SHORT).show() }
                            else Toast.makeText(this@MainActivity, "PIN inválido ou não confere.", Toast.LENGTH_SHORT).show()
                        }) { Text("Salvar") }
                    }, title = { Text("Definir PIN") }, text = {
                        Column {
                            OutlinedTextField(value = pin1, onValueChange = { pin1 = it }, label = { Text("PIN (4+ dígitos)") })
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = pin2, onValueChange = { pin2 = it }, label = { Text("Confirmar PIN") })
                        }
                    })
                }

                if (showAskPin) {
                    AlertDialog(onDismissRequest = { showAskPin = false }, confirmButton = {
                        TextButton(onClick = {
                            if (vm.checkPin(pinInput)) { vm.unlocked = true; showAskPin = false; pinInput = "" }
                            else Toast.makeText(this@MainActivity, "PIN incorreto.", Toast.LENGTH_SHORT).show()
                        }) { Text("Confirmar") }
                    }, dismissButton = { TextButton(onClick = { showAskPin = false }) { Text("Cancelar") } },
                    title = { Text("Digite o PIN") }, text = {
                        OutlinedTextField(value = pinInput, onValueChange = { pinInput = it }, label = { Text("PIN") })
                    })
                }
            }
        }
    }

    @Composable
    fun AppRow(label: String, pkg: String, protected: Boolean, onToggleProtect: (Boolean) -> Unit, onOpen: () -> Unit) {
        Row(Modifier.fillMaxWidth().clickable { onOpen() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(10.dp))
            Checkbox(checked = protected, onCheckedChange = onToggleProtect)
        }
        Divider()
    }
}

data class AppInfo(val label: String, val packageName: String)

class VaultViewModel(private val activity: ComponentActivity) {
    private val ctx = activity.applicationContext
    private val masterKey by lazy { MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build() }
    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            ctx, "vault_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    var unlocked by mutableStateOf(false)
    var biometricEnabled by mutableStateOf(prefs.getBoolean("biometric_enabled", true))
    var apps by mutableStateOf(listOf<AppInfo>())

    fun loadInstalledApps() {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolve = pm.queryIntentActivities(intent, 0)
        apps = resolve.map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }.sortedBy { it.label.lowercase() }
    }
    fun hasPin(): Boolean = prefs.contains("pin_hash")
    fun setPin(pin: String) {
        val salt = java.util.UUID.randomUUID().toString()
        val hash = sha256(pin + ":" + salt)
        prefs.edit().putString("pin_salt", salt).putString("pin_hash", hash).apply()
    }
    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString("pin_salt", null) ?: return false
        val stored = prefs.getString("pin_hash", null) ?: return false
        val attempt = sha256(pin + ":" + salt)
        if (attempt == stored) { unlocked = true; return true }
        return false
    }
    private fun sha256(s: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    fun isProtected(pkg: String): Boolean = (prefs.getStringSet("protected_pkgs", emptySet()) ?: emptySet()).contains(pkg)
    fun setProtected(pkg: String, protect: Boolean) {
        val set = prefs.getStringSet("protected_pkgs", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (protect) set.add(pkg) else set.remove(pkg)
        prefs.edit().putStringSet("protected_pkgs", set).apply()
    }
    fun toggleBiometric() { biometricEnabled = !biometricEnabled; prefs.edit().putBoolean("biometric_enabled", biometricEnabled).apply() }
    fun biometricAvailable(): Boolean {
        val bm = BiometricManager.from(ctx)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
    fun promptBiometric(activity: ComponentActivity, onSuccess: () -> Unit) {
        val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlocked = true; onSuccess() }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Desbloquear com biometria").setSubtitle("Use sua digital/rosto").setNegativeButtonText("Cancelar").build()
        prompt.authenticate(info)
    }
    fun launchApp(pkg: String) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) { intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(intent) }
        else Toast.makeText(ctx, "Não foi possível abrir o app.", Toast.LENGTH_SHORT).show()
    }
}
