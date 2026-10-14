package tr.fotografkurtar.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.graphics.Color
import android.graphics.Typeface
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var extractButton: Button
    private val archives = mutableListOf<Uri>()
    private var destination: Uri? = null
    @Volatile private var busy = false

    private val pickArchives = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            archives.clear()
            archives.addAll(uris)
            status.text = "Seçilen arşiv: ${archives.size}\nUSB klasörünü seçip çıkarma işlemini başlat."
            refreshButton()
        }
    }
    private val pickDestination = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            destination = uri
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val folder = DocumentFile.fromTreeUri(this, uri)
            status.text = "Hedef: ${folder?.name ?: "Seçilen klasör"}\nArşiv: ${archives.size}\nHazır."
            refreshButton()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(13, 35, 58)
        window.navigationBarColor = Color.rgb(13, 35, 58)
        buildUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(24))
            setBackgroundColor(Color.rgb(246, 248, 251))
        }
        scroll.addView(root)
        root.addView(TextView(this).apply {
            text = "◎"; textSize = 48f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(20, 111, 180))
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "Fotoğraf Kurtar"; textSize = 27f; gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD); setTextColor(Color.rgb(13, 35, 58))
        }, matchWrap())
        root.addView(TextView(this).apply {
            text = "Google Fotoğraflar dışa aktarımını telefonda aç, arşivleri USB belleğine çıkar."
            textSize = 16f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(70, 82, 96)); setPadding(0, dp(8), 0, dp(20))
        }, matchWrap())
        root.addView(actionButton("1  •  Google Takeout'u aç") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://takeout.google.com/")))
        })
        root.addView(actionButton("2  •  İndirilen ZIP arşivlerini seç") {
            pickArchives.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
        })
        root.addView(actionButton("3  •  USB bellekte hedef klasörü seç") {
            pickDestination.launch(null)
        })
        extractButton = actionButton("4  •  Fotoğraf ve videoları çıkar") { extractArchives() }
        root.addView(extractButton)
        status = TextView(this).apply {
            text = "Durum: Henüz arşiv ve hedef klasör seçilmedi."
            textSize = 15f; setTextColor(Color.rgb(31, 47, 64))
            setBackgroundColor(Color.WHITE); setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        root.addView(status, matchWrap().apply { topMargin = dp(12) })
        root.addView(TextView(this).apply {
            text = "Önemli: Bu uygulama Google hesabına gizlice erişmez. Önce Takeout'ta dışa aktarımı oluşturup ZIP dosyalarını indirmen gerekir. Büyük arşivlerde telefonun ve USB belleğinin boş alanını kontrol et."
            textSize = 13f; setTextColor(Color.rgb(92, 101, 112))
            setPadding(dp(2), dp(18), dp(2), 0)
        }, matchWrap())
        setContentView(scroll)
        refreshButton()
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 14f; isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
    }

    private fun refreshButton() {
        if (::extractButton.isInitialized) extractButton.isEnabled = !busy && archives.isNotEmpty() && destination != null
    }

    private fun extractArchives() {
        val targetUri = destination ?: return
        if (busy) return
        busy = true; refreshButton()
        Thread {
            var extracted = 0
            var skipped = 0
            var errors = 0
            try {
                val root = DocumentFile.fromTreeUri(this, targetUri) ?: error("USB klasörü açılamadı")
                for ((index, archiveUri) in archives.withIndex()) {
                    runOnUiThread { status.text = "Arşiv ${index + 1}/${archives.size} işleniyor..." }
                    try {
                        contentResolver.openInputStream(archiveUri).use { raw ->
                            if (raw == null) error("ZIP okunamadı")
                            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                                var entry = zip.nextEntry
                                while (entry != null) {
                                    if (!entry.isDirectory) {
                                        val path = entry.name.replace('\\', '/')
                                        val name = path.split('/').filter { it.isNotBlank() && it != "." && it != ".." }.lastOrNull().orEmpty()
                                        val lower = name.lowercase()
                                        val isMedia = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic", ".heif", ".mp4", ".mov", ".m4v", ".3gp", ".avi", ".mkv", ".dng", ".tif", ".tiff").any { lower.endsWith(it) }
                                        if (isMedia) {
                                            try {
                                                var finalName = name
                                                var candidate = root.findFile(finalName)
                                                var suffix = 1
                                                while (candidate != null) {
                                                    val dot = name.lastIndexOf('.')
                                                    finalName = if (dot > 0) name.substring(0, dot) + "_${suffix}" + name.substring(dot) else "${name}_${suffix}"
                                                    candidate = root.findFile(finalName); suffix++
                                                }
                                                val mime = when {
                                                    lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
                                                    lower.endsWith(".png") -> "image/png"
                                                    lower.endsWith(".webp") -> "image/webp"
                                                    lower.endsWith(".gif") -> "image/gif"
                                                    lower.endsWith(".mp4") || lower.endsWith(".m4v") -> "video/mp4"
                                                    lower.endsWith(".mov") -> "video/quicktime"
                                                    else -> "application/octet-stream"
                                                }
                                                val outputFile = root.createFile(mime, finalName) ?: error("USB'ye dosya oluşturulamadı")
                                                contentResolver.openOutputStream(outputFile.uri, "w").use { output ->
                                                    if (output == null) error("USB yazma akışı açılamadı")
                                                    val buffer = ByteArray(64 * 1024)
                                                    while (true) {
                                                        val count = zip.read(buffer)
                                                        if (count <= 0) break
                                                        output.write(buffer, 0, count)
                                                    }
                                                    output.flush()
                                                }
                                                extracted++
                                            } catch (_: Exception) { errors++ }
                                        } else skipped++
                                    }
                                    zip.closeEntry()
                                    entry = zip.nextEntry
                                }
                            }
                        }
                    } catch (_: Exception) { errors++ }
                }
                runOnUiThread {
                    status.text = "İşlem tamamlandı.\nAktarılan medya dosyası: ${extracted}\nAtlanan medya dışı kayıt: ${skipped}\nHata sayısı: ${errors}\nUSB belleğini çıkarmadan önce dosyaları kontrol et."
                    Toast.makeText(this, "Aktarım tamamlandı: ${extracted} dosya", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Hata: ${e.message ?: "İşlem tamamlanamadı"}" }
            } finally {
                busy = false
                runOnUiThread { refreshButton() }
            }
        }.start()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
