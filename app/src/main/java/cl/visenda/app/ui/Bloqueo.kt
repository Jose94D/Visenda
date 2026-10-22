package cl.visenda.app.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** CA-8.2: tras 5 minutos en segundo plano la app vuelve a pedir huella o PIN. */
const val MINUTOS_SIN_BLOQUEO = 5

/** El bloqueo usa el del propio teléfono (huella, rostro, PIN o patrón): sin un PIN de Visenda que guardar ni olvidar. */
fun telefonoConBloqueo(context: Context) = context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

/**
 * Pide huella o el bloqueo del teléfono. Android 10 o más usa BiometricPrompt; Android 8 y 9, la pantalla de PIN del sistema.
 * Devuelve una función para lanzar la verificación.
 */
@Composable
fun rememberVerificacion(motivo: String, alVerificar: () -> Unit, alFallar: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val pinSistema = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) alVerificar() else alFallar("No se pudo verificar.")
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val constructor = BiometricPrompt.Builder(context).setTitle("Visenda").setSubtitle(motivo)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) constructor.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            else @Suppress("DEPRECATION") constructor.setDeviceCredentialAllowed(true)
            try {
                constructor.build().authenticate(CancellationSignal(), context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = alVerificar()
                    override fun onAuthenticationError(codigo: Int, mensaje: CharSequence) = alFallar(mensaje.toString())
                })
            } catch (e: SecurityException) {
                alFallar("No se pudo pedir la verificación: ${e.message}")
            }
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(KeyguardManager::class.java)?.createConfirmDeviceCredentialIntent("Visenda", motivo)
                ?.let { pinSistema.launch(it) } ?: alVerificar() // sin bloqueo en el teléfono no hay nada que verificar
        }
    }
}

/** Cubre toda la app hasta verificar. Lanza la verificación sola al aparecer. */
@Composable
fun PantallaBloqueo(alDesbloquear: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val verificar = rememberVerificacion("Desbloquea para ver tus clientes", alDesbloquear) { error = it }
    LaunchedEffect(Unit) { verificar() }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Iconos.Candado, null, tint = Colores.Azul, modifier = Modifier.size(48.dp))
        Text("Visenda está bloqueada", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Usa tu huella o el bloqueo de pantalla del teléfono.", color = Colores.Secundario, textAlign = TextAlign.Center)
        error?.let { Text(it, color = Colores.Naranja, fontSize = 14.sp, textAlign = TextAlign.Center) }
        Button(onClick = { error = null; verificar() }, shape = RoundedCornerShape(14.dp)) { Text("Desbloquear") }
    }
}
