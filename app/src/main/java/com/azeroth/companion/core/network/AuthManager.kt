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

    /**
     * Blizzard NO acepta clientes públicos: su endpoint de token exige
     * client_secret (invalid_client con PKCE puro). Sin backend propio, la
     * única vía es incluirlo en el APK; el riesgo queda acotado por el scope
     * de solo lectura y el redirect fijado.
     */
    private val clientSecret: String = com.azeroth.companion.BuildConfig.BLIZZARD_CLIENT_SECRET

    val isConfigured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    suspend fun restore(region: Region? = null) {
        val prefs = context.authStore.data.first()
        val token = prefs[Keys.ACCESS_TOKEN]
        if (token == null) {
            _state.value = AuthState.LoggedOut
            return
        }
        val expiresAt = prefs[Keys.EXPIRES_AT] ?: 0
        val expired = Instant.now().epochSecond >= expiresAt
        _state.value = if (expired) AuthState.Expired else AuthState.LoggedIn(prefs[Keys.BATTLE_TAG])
        // Al abrir la app se intenta alargar el token: es lo que evita que la
        // sesión muera sola. Si ya caducó no hay nada que alargar.
        if (!expired && region != null) extendToken(region, token)
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
        // client_id NO va en el cuerpo: ya se envía por HTTP Basic auth en exchange().
        // Enviar ambos hace que Blizzard rechace con invalid_client (RFC 6749 §2.3:
        // un cliente no puede usar más de un método de autenticación por petición).
        exchange(
            region,
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .add("code_verifier", verifier)
                .build(),
        )
    }

    /** Devuelve un token vigente, alargándolo o refrescándolo si toca. Null = modo degradado. */
    suspend fun validAccessToken(region: Region): String? {
        val prefs = context.authStore.data.first()
        val token = prefs[Keys.ACCESS_TOKEN] ?: return null
        val expiresAt = prefs[Keys.EXPIRES_AT] ?: 0
        val now = Instant.now().epochSecond
        // Alargar mucho antes de que caduque: si el token muere sin haberse
        // alargado, no hay forma de recuperarlo sin volver a iniciar sesión.
        if (now > expiresAt - RENEW_MARGIN_SECONDS) {
            extendToken(region, token)?.let { return it }
        }
        if (now < expiresAt - 60) return token
        val refresh = prefs[Keys.REFRESH_TOKEN]
        if (refresh != null) {
            // Igual que en el login: client_id solo por Basic auth, nunca también en el cuerpo.
            exchange(
                region,
                FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refresh)
                    .build(),
            )
            return context.authStore.data.first()[Keys.ACCESS_TOKEN]
        }
        // Sin sesión de usuario los datos del personaje SIGUEN funcionando con el
        // token de aplicación (los endpoints /profile/wow/character/... lo aceptan);
        // solo la importación del roster de la cuenta necesita al usuario. Por eso
        // esto es "Expired" y no "Broken": no hay nada que el usuario deba arreglar
        // salvo que quiera volver a leer la lista de personajes de su cuenta.
        _state.value = AuthState.Expired
        return null
    }

    /**
     * Alarga la vida del token con el grant `token_extension` de Blizzard.
     * Blizzard NO habilita `refresh_token` para los clientes de desarrollador
     * (el endpoint responde `invalid_client: Unauthorized grant type`) y los
     * tokens de usuario caducan a las 24 h, que era la causa de tener que
     * reconectar la cuenta todos los días. `token_extension` sí está habilitado
     * y devuelve el mismo token con ~90 días de vida, así que basta con abrir la
     * app una vez cada tres meses para no volver a iniciar sesión.
     */
    @Volatile private var lastExtendAttempt: Long = 0

    private suspend fun extendToken(region: Region, token: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (clientId.isBlank() || clientSecret.isBlank()) return@withContext null
            // El interceptor pasa por aquí en CADA petición: sin este freno, un
            // token que no se puede alargar dispararía una llamada extra por
            // request.
            val now = Instant.now().epochSecond
            if (now - lastExtendAttempt < EXTEND_RETRY_SECONDS) return@withContext null
            lastExtendAttempt = now
            val request = Request.Builder()
                .url("${region.oauthHost}/token")
                .header("Authorization", okhttp3.Credentials.basic(clientId, clientSecret))
                .post(
                    FormBody.Builder()
                        .add("grant_type", "token_extension")
                        .add("token", token)
                        .build(),
                )
                .build()
            runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@withContext null
                    val payload = json.decodeFromString(TokenResponse.serializer(), raw)
                    context.authStore.edit {
                        it[Keys.ACCESS_TOKEN] = payload.access_token
                        it[Keys.EXPIRES_AT] = Instant.now().epochSecond + payload.expires_in
                    }
                    _state.value = AuthState.LoggedIn(prefsBattleTag())
                    payload.access_token
                }
            }.getOrNull()
        }

    private suspend fun prefsBattleTag(): String? =
        context.authStore.data.first()[Keys.BATTLE_TAG]

    /** Hay sesión guardada, aunque esté caducada (el roster local sigue sirviendo). */
    suspend fun hasStoredSession(): Boolean =
        context.authStore.data.first()[Keys.ACCESS_TOKEN] != null

    suspend fun logout() {
        context.authStore.edit { it.clear() }
        _state.value = AuthState.LoggedOut
    }

    // Token de aplicación (client_credentials) para datos de juego públicos
    // (journal de mazmorras/bandas, etc.): NO requiere sesión del usuario, así
    // que el contenido de la app funciona para cualquiera. Cacheado hasta expirar.
    @Volatile private var appToken: String? = null
    @Volatile private var appTokenExpiry: Long = 0

    suspend fun appAccessToken(region: Region): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            appToken?.let { if (Instant.now().epochSecond < appTokenExpiry - 60) return@withContext it }
            if (clientId.isBlank() || clientSecret.isBlank()) return@withContext null
            val request = Request.Builder()
                .url("${region.oauthHost}/token")
                .header("Authorization", okhttp3.Credentials.basic(clientId, clientSecret))
                .post(FormBody.Builder().add("grant_type", "client_credentials").build())
                .build()
            runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@withContext null
                    val payload = json.decodeFromString(TokenResponse.serializer(), raw)
                    appToken = payload.access_token
                    appTokenExpiry = Instant.now().epochSecond + payload.expires_in
                    payload.access_token
                }
            }.getOrNull()
        }

    // La petición de token es red síncrona: SIEMPRE en Dispatchers.IO. Llamarla
    // desde el hilo principal lanza NetworkOnMainThreadException (sin mensaje),
    // que era el "fallo desconocido" reportado en el login.
    private suspend fun exchange(region: Region, body: FormBody) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val request = Request.Builder()
                .url("${region.oauthHost}/token")
                .header("Authorization", okhttp3.Credentials.basic(clientId, clientSecret))
                .post(body)
                .build()
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

    private companion object {
        /**
         * Se intenta alargar el token cuando le queda menos de una semana. Un
         * token caducado ya no se puede alargar, así que el margen tiene que ser
         * mucho mayor que el intervalo con el que el usuario abre la app.
         */
        const val RENEW_MARGIN_SECONDS = 7L * 24 * 3600

        /** Espera mínima entre intentos de alargar, para no llamar por request. */
        const val EXTEND_RETRY_SECONDS = 3600L
    }
}

@Serializable
private data class TokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Long = 3600,
    val token_type: String = "bearer",
)
