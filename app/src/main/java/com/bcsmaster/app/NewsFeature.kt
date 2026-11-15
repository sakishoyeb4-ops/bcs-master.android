
package com.bcsmaster.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Xml
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NewsItem(
    val title: String,
    val link: String,
    val description: String,
    val date: String,
    val source: String,
    val score: Int
)

object NewsRepository {

    private const val PREF = "bcs_news_cache"
    private const val KEY = "news_items"

    private val feeds = listOf(
        "প্রথম আলো" to "https://www.prothomalo.com/feed/",
        "The Daily Star" to "https://www.thedailystar.net/frontpage/rss.xml",
        "bdnews24" to "https://bdnews24.com/?widgetName=rssfeed&widgetId=1150&getXmlFeed=true",
        "Jugantor" to "https://www.jugantor.com/feed/rss.xml",
        "Jago News" to "https://www.jagonews24.com/rss/rss.xml",
        "BBC Bangla" to "https://feeds.bbci.co.uk/bengali/rss.xml"
    )

    private val importantWords = listOf(
        "বাংলাদেশ", "সরকার", "সংবিধান", "নির্বাচন", "জাতিসংঘ",
        "অর্থনীতি", "বাজেট", "মূল্যস্ফীতি", "রপ্তানি", "আমদানি",
        "ব্যাংক", "জিডিপি", "দারিদ্র্য", "জলবায়ু", "পরিবেশ",
        "দুর্যোগ", "নদী", "শিক্ষা", "স্বাস্থ্য", "বিজ্ঞান",
        "প্রযুক্তি", "আন্তর্জাতিক", "চুক্তি", "কূটনীতি",
        "বাংলাদেশ ব্যাংক", "বিশ্বব্যাংক", "আইএমএফ",
        "united nations", "world bank", "imf",
        "economy", "inflation", "gdp", "budget",
        "climate change", "environment", "election",
        "constitution", "diplomacy", "international",
        "technology", "science", "trade", "export",
        "import", "poverty", "development", "parliament",
        "government", "security", "summit"
    )

    private fun calculateScore(title: String, description: String): Int {
        val text = "$title $description".lowercase()
        return importantWords.count { text.contains(it.lowercase()) }
    }

    private fun cleanHtml(value: String): String {
        return value
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun readTag(parser: XmlPullParser): String {
        return cleanHtml(parser.nextText())
    }

    private fun parseFeed(source: String, feedUrl: String): List<NewsItem> {
        val connection = URL(feedUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.setRequestProperty("User-Agent", "BCSMaster/1.0")

        try {
            connection.inputStream.use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, "UTF-8")

                val result = mutableListOf<NewsItem>()
                var event = parser.eventType

                var insideItem = false
                var title = ""
                var link = ""
                var description = ""
                var date = ""

                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name.lowercase()) {
                                "item", "entry" -> {
                                    insideItem = true
                                    title = ""
                                    link = ""
                                    description = ""
                                    date = ""
                                }

                                "title" -> if (insideItem) {
                                    title = readTag(parser)
                                }

                                "link" -> if (insideItem) {
                                    val href = parser.getAttributeValue(null, "href")
                                    link = href ?: readTag(parser)
                                }

                                "description", "summary", "content" ->
                                    if (insideItem) {
                                        description = readTag(parser)
                                    }

                                "pubdate", "published", "updated" ->
                                    if (insideItem) {
                                        date = readTag(parser)
                                    }
                            }
                        }

                        XmlPullParser.END_TAG -> {
                            if (parser.name.lowercase() in listOf("item", "entry")) {
                                if (title.isNotBlank() && link.startsWith("http")) {
                                    result.add(
                                        NewsItem(
                                            title = title,
                                            link = link,
                                            description = description,
                                            date = date,
                                            source = source,
                                            score = calculateScore(title, description)
                                        )
                                    )
                                }
                                insideItem = false
                            }
                        }
                    }
                    event = parser.next()
                }
                return result
            }
        } finally {
            connection.disconnect()
        }
    }

    fun loadSaved(context: Context): List<NewsItem> {
        return try {
            val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY, "[]") ?: "[]"

            val array = JSONArray(raw)

            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                NewsItem(
                    title = o.optString("title"),
                    link = o.optString("link"),
                    description = o.optString("description"),
                    date = o.optString("date"),
                    source = o.optString("source"),
                    score = o.optInt("score")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(context: Context, items: List<NewsItem>) {
        val array = JSONArray()

        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("title", item.title)
                    put("link", item.link)
                    put("description", item.description)
                    put("date", item.date)
                    put("source", item.source)
                    put("score", item.score)
                }
            )
        }

        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }

    fun fetchLatest(context: Context): List<NewsItem> {
        val all = mutableListOf<NewsItem>()

        feeds.forEach { (source, url) ->
            try {
                all.addAll(parseFeed(source, url))
            } catch (_: Exception) {
                // অন্য সংবাদমাধ্যম কাজ না করলেও বাকি ফিড চেষ্টা করবে।
            }
        }

        val unique = all
            .distinctBy { it.link }
            .sortedWith(
                compareByDescending<NewsItem> { it.score }
                    .thenByDescending { it.date }
            )
            .take(100)

        if (unique.isNotEmpty()) {
            save(context, unique)
        }

        return unique
    }
}

// প্রতিদিন স্বয়ংক্রিয় সংবাদ আপডেট
class NewsRefreshWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            NewsRepository.fetchLatest(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

fun scheduleDailyNewsUpdate(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request =
        PeriodicWorkRequestBuilder<NewsRefreshWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "BCS_DAILY_NEWS_UPDATE",
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}

@Composable
fun NewsScreen() {
    val context = LocalContext.current

    val news = remember {
        mutableStateListOf<NewsItem>().apply {
            addAll(NewsRepository.loadSaved(context))
        }
    }

    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }

    var message by remember {
        mutableStateOf("সংবাদ আপডেট করতে Refresh চাপুন।")
    }

    LaunchedEffect(Unit) {
        scheduleDailyNewsUpdate(context)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text(
            "BCS News Intelligence",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            "বাংলা ও ইংরেজি গুরুত্বপূর্ণ সংবাদ",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(10.dp))

        Button(
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                loading = true
                message = "সংবাদ সংগ্রহ করা হচ্ছে..."

                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        NewsRepository.fetchLatest(context)
                    }

                    news.clear()
                    news.addAll(
                        if (result.isNotEmpty()) result
                        else NewsRepository.loadSaved(context)
                    )

                    message = if (result.isNotEmpty()) {
                        "${result.size}টি সংবাদ পাওয়া গেছে।"
                    } else {
                        "নতুন সংবাদ পাওয়া যায়নি। সংরক্ষিত সংবাদ দেখানো হচ্ছে।"
                    }

                    loading = false
                }
            }
        ) {
            Text(if (loading) "আপডেট হচ্ছে..." else "নতুন খবর আপডেট")
        }

        Text(message)

        Spacer(Modifier.height(8.dp))

        if (news.isEmpty()) {
            Text("এখনো কোনো সংবাদ সংরক্ষিত নেই। ইন্টারনেট চালু করে Refresh চাপুন।")
        }

        news.forEach { item ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(Modifier.height(5.dp))

                    Text(
                        "${item.source} | ${item.date}",
                        style = MaterialTheme.typography.labelSmall
                    )

                    Text(
                        "BCS গুরুত্ব স্কোর: ${item.score}",
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (item.description.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(item.description)
                    }

                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(item.link)
                                    )
                                )
                            } catch (_: Exception) {
                            }
                        }
                    ) {
                        Text("মূল সংবাদ পড়ুন")
                    }
                }
            }
        }
    }
}
