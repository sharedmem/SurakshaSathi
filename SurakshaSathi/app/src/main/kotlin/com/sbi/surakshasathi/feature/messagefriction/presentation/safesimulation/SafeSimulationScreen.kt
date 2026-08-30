package com.sbi.surakshasathi.feature.messagefriction.presentation.safesimulation

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sbi.surakshasathi.core.designsystem.theme.maliciousColor
import com.sbi.surakshasathi.core.designsystem.theme.safeColor
import com.sbi.surakshasathi.core.designsystem.theme.warningColor

/** JS injected after every page load inside the sandbox — finds password/OTP/PIN/card-shaped
 * input fields and makes them physically un-typeable (readonly + no pointer events), not just
 * visually blurred, so nothing can be entered into this page even if the user tries. */
private const val SENSITIVE_FIELD_GUARD_JS = """
(function() {
  var inputs = document.querySelectorAll('input, textarea');
  inputs.forEach(function(el) {
    var type = (el.getAttribute('type') || '').toLowerCase();
    var hint = ((el.getAttribute('name') || '') + ' ' + (el.getAttribute('id') || '') + ' ' +
      (el.getAttribute('autocomplete') || '') + ' ' + (el.getAttribute('placeholder') || '')).toLowerCase();
    var isSensitive = type === 'password' || type === 'tel' ||
      hint.indexOf('otp') !== -1 || hint.indexOf('pin') !== -1 || hint.indexOf('cvv') !== -1 ||
      hint.indexOf('card') !== -1 || hint.indexOf('mpin') !== -1 || hint.indexOf('password') !== -1;
    if (isSensitive) {
      el.setAttribute('readonly', 'readonly');
      el.setAttribute('disabled', 'disabled');
      el.style.filter = 'blur(4px)';
      el.style.pointerEvents = 'none';
      el.style.backgroundColor = '#ffd6d6';
    }
  });
})();
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SafeSimulationScreen(
    navController: NavController,
    viewModel: SafeSimulationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SandboxBanner()

        when (val state = uiState) {
            is SafeSimulationUiState.Loading ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            is SafeSimulationUiState.NoUrl ->
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No link was found to preview for this message.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

            is SafeSimulationUiState.Content -> {
                DomainSafetyBanner(state)
                SandboxedWebView(url = state.url, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun SandboxBanner() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(
                "SANDBOX PREVIEW — nothing you type here is sent anywhere, and password/OTP fields are locked.",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DomainSafetyBanner(state: SafeSimulationUiState.Content) {
    val looksFake = !state.isOfficialDomain && (state.isPwaSpoofing || (state.domainAgeDays ?: Int.MAX_VALUE) < 30)
    val bannerColor =
        when {
            state.isOfficialDomain -> MaterialTheme.colorScheme.safeColor
            looksFake -> MaterialTheme.colorScheme.maliciousColor
            else -> MaterialTheme.colorScheme.warningColor
        }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bannerColor.copy(alpha = 0.14f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.GppMaybe, contentDescription = null, tint = bannerColor, modifier = Modifier.padding(end = 8.dp))
                Text(
                    when {
                        state.isOfficialDomain -> "This matches a known official banking domain."
                        looksFake -> "This looks like a fake or lookalike site."
                        else -> "This domain could not be verified as official."
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = bannerColor,
                )
            }
            Text(state.url, style = MaterialTheme.typography.bodySmall)
            if (state.domainAgeDays != null) {
                Text("Domain age: ${state.domainAgeDays} days", style = MaterialTheme.typography.bodySmall)
            }
            if (state.isPwaSpoofing) {
                Text(
                    "This app-like page's branding doesn't match its own manifest — a common impersonation sign.",
                    style = MaterialTheme.typography.bodySmall,
                    color = bannerColor,
                )
            }
        }
    }
}

@Composable
private fun SandboxedWebView(
    url: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            // Cookies/local storage disabled and cleared on teardown below -- this WebView's
            // state is disposable by design, never persisted or synced anywhere.
            CookieManager.getInstance().setAcceptCookie(false)

            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.saveFormData = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setGeolocationEnabled(false)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                        ): Boolean {
                            val scheme = request.url.scheme?.lowercase()
                            // Block anything that would hand off to another app (tel:, intent:,
                            // market:, custom schemes) -- the sandbox must never leave the sandbox.
                            return scheme != "http" && scheme != "https"
                        }

                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            super.onPageFinished(view, url)
                            view.evaluateJavascript(SENSITIVE_FIELD_GUARD_JS, null)
                        }
                    }

                loadUrl(url)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearHistory()
            CookieManager.getInstance().removeAllCookies(null)
            webView.destroy()
        },
    )
}
