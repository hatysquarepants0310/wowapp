package com.azeroth.companion.core.network

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.azeroth.companion.core.model.AuthState
import com.azeroth.companion.core.model.Region
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.authStore by preferencesDataStore(name = "auth")

/**
 * OAuth 2.0 Authorization Code + PKCE puro (§2.1): cliente público, sin
 * client_secret en el APK y sin backend intermedio. El flujo se abre en
 * Custom Tab (nunca WebView) y el redirect vuelve por deep link
 * azerothcompanion://oauth.
 *
 * Ante cualquier fallo irrecuperable el estado pasa a [AuthState.Broken] con
 * motivo accionable: la app sigue funcionando en modo degradado (§11).
 */
@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at_epoch_s")
        val CODE_VERIFIER = stringPreferencesKey("pending_code_verifier")
        val OAUTH_STATE = stringPreferencesKey("pending_oauth_state")
        val BATTLE_TAG = stringPreferencesKey("battle_tag")
    }

    private val _state = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    val state: StateFlow<AuthState> = _state

    /**
     * Blizzard solo acepta redirects HTTPS, no esquemas de app. Esta página
     * estática (GitHub Pages, sin backend) rebota el code al esquema
     * azerothcompanion://oauth que la app sí intercepta. Debe estar registrada
     * EXACTAMENTE igual en el Blizzard Developer Portal.
     */
    val redirectUri = "https://hatysquarepants0310.github.io/wowapp/oauth.html"

    /** Registrado en el Blizzard Developer Portal; viene de BuildConfig (gradle -PblizzardClientId). */
    var clientId: String = com.azeroth.companion.BuildConfig.BLIZZARD_CLIENT_ID

    val isConfigured: Boolean get() = clientId.isNotBlank()

    suspend fun restore() {
        val prefs = context.authStore.data.first()
        _state.value = when {
            prefs[Keys.ACCESS_TOKEN] == null -> AuthState.LoggedOut
            else -> AuthState.LoggedIn(prefs[Keys.BATTLE_TAG])
        }
    }

    /** Construye la URL de autorización y guarda code_verifier + state pendientes. */
    suspend fun buildAuthorizationUri(region: Region): Uri {
        val verifier = randomUrlSafe(64)
        // Blizzard exige el parámetro state (anti-CSRF); se valida en el retorno.
        val oauthState = randomUrlSafe(16)
        context.authStore.edit {
            it[Keys.CODE_VERIFIER] = verifier
            it[Keys.OAUTH_STATE] = oauthState
        }
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        return Uri.parse("${region.oauthHost}/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", "wow.profile")
            .appendQueryParameter("state", oauthState)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
    }

    /** Intercambia el authorization code recibido en el deep link. */
    suspend fun handleRedirect(code: String, returnedState: String?, region: Region) {
        val prefs = context.authStore.data.first()
        val verifier = prefs[Keys.CODE_VERIFIER]
        val expectedState = prefs[Keys.OAUTH_STATE]
        if (verifier == null) {
            _state.value = AuthState.Broken("Flujo OAuth sin code_verifier pendiente. Reintenta el login.")
            return
        }
        if (expectedState != null && returnedState != expectedState) {
            _state.value = AuthState.Broken("El parámetro state no coincide (posible CSRF). Reintenta el login.")
            return
        }
        exchange(
            region,
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .add("client_id", clientId)
                .add("code_verifier", verifier)
                .build(),
        )
    }

    /** Devuelve un token vigente, refrescando si expiró. Null = modo degradado. */
    suspend fun validAccessToken(region: Region): String? {
        val prefs = context.authStore.data.first()
        val token = prefs[Keys.ACCESS_TOKEN] ?: return null
        val expiresAt = prefs[Keys.EXPIRES_AT] ?: 0
        if (Instant.now().epochSecond < expiresAt - 60) return token
        val refresh = prefs[Keys.REFRESH_TOKEN] ?: run {
            _state.value = AuthState.Broken("Token expirado y sin refresh token. Vuelve a iniciar sesión.")
            return null
        }
        exchange(
            region,
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .add("client_id", clientId)
                .build(),
        )
        return context.authStore.data.first()[Keys.ACCESS_TOKEN]
    }

    suspend fun logout() {
        context.authStore.edit { it.clear() }
        _state.value = AuthState.LoggedOut
    }

    // La petición de token es red síncrona: SIEMPRE en Dispatchers.IO. Llamarla
    // desde el hilo principal lanza NetworkOnMainThreadException (sin mensaje),
    // que era el "fallo desconocido" reportado en el login.
    private suspend fun exchange(region: Region, body: FormBody) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val request = Request.Builder().url("${region.oauthHost}/token").post(body).build()
            runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error("El endpoint de token respondió HTTP ${response.code}: ${raw.take(200)}")
                    }
                    val payload = json.decodeFromString(TokenResponse.serializer(), raw)
                    context.authStore.edit {
                        it[Keys.ACCESS_TOKEN] = payload.access_token
                        payload.refresh_token?.let { rt -> it[Keys.REFRESH_TOKEN] = rt }
                        it[Keys.EXPIRES_AT] = Instant.now().epochSecond + payload.expires_in
                    }
                    _state.value = AuthState.LoggedIn(null)
                }
            }.onFailure {
                _state.value = AuthState.Broken(
                    it.message ?: "${it::class.simpleName}: fallo al canjear el token",
                )
            }
        }

    private fun randomUrlSafe(bytes: Int): String {
        val buf = ByteArray(bytes)
        SecureRandom().nextBytes(buf)
        return Base64.encodeToString(buf, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}

@Serializable
private data class TokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Long = 3600,
    val token_type: String = "bearer",
)
