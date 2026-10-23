package android.print

import android.annotation.SuppressLint
import android.content.Context
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Prints an HTML page to an A4 PDF without the system print dialog, so the PDF report looks
 * exactly like the HTML one. Lives in `android.print` because the layout/write callbacks have
 * package-private constructors. Callers keep a fallback in case a device refuses it.
 */
object HtmlPdfWriter {

    suspend fun write(context: Context, html: String, out: File, timeoutMs: Long = 30_000) =
        withContext(Dispatchers.Main) {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    @SuppressLint("SetJavaScriptEnabled")
                    val webView = WebView(context)
                    webView.settings.javaScriptEnabled = false
                    webView.webViewClient = object : WebViewClient() {
                        private var started = false

                        override fun onPageFinished(view: WebView, url: String?) {
                            if (started) return
                            started = true
                            runCatching { print(view, out, cont) }
                                .onFailure { if (cont.isActive) cont.resumeWithException(it) }
                        }
                    }
                    cont.invokeOnCancellation { webView.destroy() }
                    webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                }
            }
        }

    private fun print(
        view: WebView,
        out: File,
        cont: kotlinx.coroutines.CancellableContinuation<Unit>,
    ) {
        val adapter = view.createPrintDocumentAdapter("report")
        val attrs = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
            .build()
        adapter.onStart()
        adapter.onLayout(null, attrs, CancellationSignal(), object : PrintDocumentAdapter.LayoutResultCallback() {
            override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
                val pfd = ParcelFileDescriptor.open(
                    out,
                    ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE,
                )
                adapter.onWrite(arrayOf(PageRange.ALL_PAGES), pfd, CancellationSignal(), object : PrintDocumentAdapter.WriteResultCallback() {
                    override fun onWriteFinished(pages: Array<out PageRange>) {
                        pfd.close()
                        adapter.onFinish()
                        view.destroy()
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onWriteFailed(error: CharSequence?) {
                        pfd.close()
                        adapter.onFinish()
                        view.destroy()
                        if (cont.isActive) cont.resumeWithException(IllegalStateException("PDF write failed: $error"))
                    }
                })
            }

            override fun onLayoutFailed(error: CharSequence?) {
                view.destroy()
                if (cont.isActive) cont.resumeWithException(IllegalStateException("PDF layout failed: $error"))
            }
        }, null)
    }
}
