package com.gifthunter.app

import android.os.Bundle
import java.time.Instant
import java.time.temporal.ChronoUnit
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

// Emulator URL. For a physical device use the computer's LAN IP during development.
// Production builds must use an HTTPS API endpoint.
private const val API_BASE_URL = "http://10.0.2.2:8000/"

data class Giveaway(
    val id: Int,
    val title: String,
    val description: String,
    val prize_name: String,
    val cost_points: Int,
    val winner_count: Int,
    val ends_at: String
)

data class RegisterRequest(val email: String, val username: String, val password: String)
data class LoginRequest(val email: String, val password: String)
data class CreateGiveawayRequest(val title: String, val description: String, val prize_name: String, val cost_points: Int, val winner_count: Int, val ends_at: String)
data class TokenResponse(val access_token: String, val token_type: String = "bearer")
data class UserProfile(
    val id: Int,
    val email: String,
    val username: String,
    val points: Int,
    val is_admin: Boolean
)

interface GiftHunterApi {
    @GET("giveaways")
    suspend fun giveaways(): List<Giveaway>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): UserProfile

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @GET("users/me")
    suspend fun me(@Header("Authorization") authorization: String): UserProfile

    @POST("giveaways")
    suspend fun createGiveaway(@Body request: CreateGiveawayRequest, @Header("Authorization") authorization: String): Giveaway

    @POST("giveaways/{id}/enter")
    suspend fun enter(@Path("id") giveawayId: Int, @Header("Authorization") authorization: String): Map<String, String>
}

private val api: GiftHunterApi by lazy {
    Retrofit.Builder()
        .baseUrl(API_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GiftHunterApi::class.java)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GiftHunterScreen() }
    }
}

@Composable
private fun GiftHunterScreen() {
    val scope = rememberCoroutineScope()
    var giveaways by remember { mutableStateOf<List<Giveaway>>(emptyList()) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var token by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var isRegister by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showCreateForm by remember { mutableStateOf(false) }
    var giveawayTitle by remember { mutableStateOf("") }
    var prizeName by remember { mutableStateOf("") }
    var giveawayDescription by remember { mutableStateOf("") }
    var entryCost by remember { mutableStateOf("1") }
    var winnerCount by remember { mutableStateOf("1") }

    suspend fun refresh() {
        loading = true
        error = null
        try {
            giveaways = withContext(Dispatchers.IO) { api.giveaways() }
            token?.let { savedToken ->
                profile = withContext(Dispatchers.IO) { api.me("Bearer $savedToken") }
            }
        } catch (e: Exception) {
            error = "Не удалось связаться с сервером. Проверьте API и адрес подключения."
        } finally {
            loading = false
        }
    }

    suspend fun submitAuth() {
        loading = true
        error = null
        notice = null
        try {
            if (isRegister) {
                withContext(Dispatchers.IO) { api.register(RegisterRequest(email.trim(), username.trim(), password)) }
            }
            val response = withContext(Dispatchers.IO) { api.login(LoginRequest(email.trim(), password)) }
            token = response.access_token
            profile = withContext(Dispatchers.IO) { api.me("Bearer ${response.access_token}") }
            password = ""
            notice = "Вы вошли в GiftHunter."
            refresh()
        } catch (e: Exception) {
            error = if (isRegister) {
                "Регистрация не удалась. Проверьте email, имя (3–32 символа) и пароль (не менее 10 символов)."
            } else {
                "Вход не удался. Проверьте email и пароль."
            }
        } finally {
            loading = false
        }
    }

    suspend fun submitGiveaway() {
        val activeToken = token ?: return
        val cost = entryCost.toIntOrNull()
        val winners = winnerCount.toIntOrNull()
        if (giveawayTitle.trim().length < 3 || prizeName.trim().length < 2 ||
            cost == null || cost !in 1..10000 || winners == null || winners !in 1..100) {
            error = "Проверьте название, приз, стоимость (1–10000) и число победителей (1–100)."
            return
        }
        loading = true
        error = null
        notice = null
        try {
            val request = CreateGiveawayRequest(
                title = giveawayTitle.trim(),
                description = giveawayDescription.trim(),
                prize_name = prizeName.trim(),
                cost_points = cost,
                winner_count = winners,
                ends_at = Instant.now().plus(24, ChronoUnit.HOURS).toString()
            )
            withContext(Dispatchers.IO) { api.createGiveaway(request, "Bearer $activeToken") }
            giveawayTitle = ""
            prizeName = ""
            giveawayDescription = ""
            showCreateForm = false
            notice = "Раздача опубликована. Она завершится через 24 часа."
            refresh()
        } catch (e: Exception) {
            error = "Не удалось создать раздачу. Проверьте соединение и корректность данных."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("GiftHunter", style = MaterialTheme.typography.headlineLarge)
                Text("Охоться за раздачами", style = MaterialTheme.typography.titleMedium)

                if (profile == null) {
                    Text(if (isRegister) "Создать аккаунт" else "Войти в аккаунт", style = MaterialTheme.typography.titleLarge)
                    if (isRegister) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Имя пользователя") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль (минимум 10 символов)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !loading, onClick = { scope.launch { submitAuth() } }) {
                            Text(if (isRegister) "Зарегистрироваться" else "Войти")
                        }
                        TextButton(onClick = {
                            isRegister = !isRegister
                            error = null
                            notice = null
                        }) {
                            Text(if (isRegister) "Уже есть аккаунт" else "Создать аккаунт")
                        }
                    }
                } else {
                    Text("Привет, ${profile?.username}!", style = MaterialTheme.typography.titleLarge)
                    Text("Баланс: ${profile?.points ?: 0} баллов")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch { refresh() } }, enabled = !loading) { Text("Обновить") }
                        TextButton(onClick = { showCreateForm = !showCreateForm }) {
                            Text(if (showCreateForm) "Отмена" else "Создать раздачу")
                        }
                        TextButton(onClick = {
                            token = null
                            profile = null
                            showCreateForm = false
                            notice = "Вы вышли из аккаунта."
                        }) { Text("Выйти") }
                    }
                    if (showCreateForm) {
                        Text("Новая раздача", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(value = giveawayTitle, onValueChange = { giveawayTitle = it }, label = { Text("Название (минимум 3 символа)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = prizeName, onValueChange = { prizeName = it }, label = { Text("Приз") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = giveawayDescription, onValueChange = { giveawayDescription = it }, label = { Text("Описание (необязательно)") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = entryCost, onValueChange = { entryCost = it.filter(Char::isDigit) }, label = { Text("Баллы за вход") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = winnerCount, onValueChange = { winnerCount = it.filter(Char::isDigit) }, label = { Text("Победителей") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Text("Срок раздачи — 24 часа с момента публикации.")
                        Button(onClick = { scope.launch { submitGiveaway() } }, enabled = !loading) { Text("Опубликовать") }
                    }
                }

                notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (loading) CircularProgressIndicator()

                Text("Раздачи", style = MaterialTheme.typography.titleLarge)
                if (!loading && error == null && giveaways.isEmpty()) {
                    Text("Пока нет раздач. Создайте первую раздачу кнопкой выше.")
                }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(giveaways, key = { it.id }) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(item.title, style = MaterialTheme.typography.titleLarge)
                                Text(item.prize_name, style = MaterialTheme.typography.titleMedium)
                                if (item.description.isNotBlank()) Text(item.description)
                                Text("Вход: ${item.cost_points} баллов · Победителей: ${item.winner_count}")
                                Text("Окончание: ${item.ends_at}")
                                if (token != null) {
                                    Button(
                                        enabled = !loading,
                                        onClick = {
                                            scope.launch {
                                                loading = true
                                                error = null
                                                try {
                                                    withContext(Dispatchers.IO) { api.enter(item.id, "Bearer ${token}") }
                                                    notice = "Вы участвуете в раздаче «${item.title}»."
                                                    refresh()
                                                } catch (e: Exception) {
                                                    error = "Не удалось вступить. Возможно, раздача закрыта, баллов недостаточно или вы уже участвуете."
                                                } finally {
                                                    loading = false
                                                }
                                            }
                                        }
                                    ) { Text("Участвовать") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
