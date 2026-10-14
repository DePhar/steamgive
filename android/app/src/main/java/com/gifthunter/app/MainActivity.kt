package com.gifthunter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

// Android emulator reaches the host machine through 10.0.2.2.
// Change this base URL for a deployed HTTPS API or a physical device.
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

interface GiftHunterApi {
    @GET("giveaways")
    suspend fun giveaways(): List<Giveaway>
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
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        error = null
        try {
            giveaways = withContext(Dispatchers.IO) { api.giveaways() }
        } catch (e: Exception) {
            error = "Не удалось загрузить раздачи. Проверьте, запущен ли сервер GiftHunter."
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
                Button(onClick = { scope.launch { refresh() } }) {
                    Text("Обновить")
                }
                if (loading) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!loading && error == null && giveaways.isEmpty()) {
                    Text("Пока нет активных раздач. Они появятся здесь после запуска API.")
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
                                Text("Вход: ${item.cost_points} балл(ов) · Победителей: ${item.winner_count}")
                                Text("Окончание: ${item.ends_at}")
                            }
                        }
                    }
                }
            }
        }
    }
}
