package finance.grummans.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// The BUDGET tab: salary cycle, reserves and today's spendable amount (Slice 3).
// Money is never hardcoded here; every figure comes from the server, and the
// buffer/total budget are entered by the user.
internal class BudgetViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("terminal_finance", 0)
    private var refreshJob: Job? = null
    private var refreshGeneration = 0

    var backendUrl by mutableStateOf(preferences.getString("backend_url", "http://127.0.0.1:8080").orEmpty())
        private set
    var snapshot by mutableStateOf<BudgetSnapshot?>(null)
        private set
    var receipts by mutableStateOf<List<SalaryReceipt>>(emptyList())
        private set
    // null means "not loaded yet". It must never be read as "nothing is set", or an
    // empty form would write zero over the stored settings.
    var settings by mutableStateOf<BudgetSettings?>(null)
        private set
    var settingsLoaded by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun updateBackendUrl(url: String) {
        backendUrl = url.trim().removeSuffix("/")
        preferences.edit().putString("backend_url", backendUrl).apply()
        // another server means another budget, so nothing already read still applies
        snapshot = null
        receipts = emptyList()
        settings = null
        settingsLoaded = false
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        val generation = ++refreshGeneration
        refreshJob = viewModelScope.launch {
            loading = true
            message = null
            // the URL is user input, so even building the client can fail
            val result = runCatching {
                val api = FinanceApi(backendUrl)
                Triple(api.getBudget(), api.getBudgetSettings(), api.listSalaryReceipts())
            }
            // a newer refresh or another server owns the state now
            if (generation != refreshGeneration) return@launch
            result.onSuccess { (newSnapshot, newSettings, newReceipts) ->
                snapshot = newSnapshot
                settings = newSettings
                settingsLoaded = true
                receipts = newReceipts
            }.onFailure { message = it.message ?: "Không tải được ngân sách" }
            loading = false
        }
    }

    fun recordSalary(amountVnd: Long, receivedOn: String, idempotencyKey: String, onDone: () -> Unit) {
        viewModelScope.launch {
            loading = true
            message = null
            val result = runCatching { FinanceApi(backendUrl).recordSalaryReceipt(amountVnd, receivedOn, idempotencyKey) }
            if (result.isSuccess) {
                onDone()
                refresh()
                return@launch
            }
            message = result.exceptionOrNull()?.message ?: "Không ghi được ngày lương"
            loading = false
        }
    }

    fun saveSettings(globalBufferVnd: Long, globalBudgetVnd: Long?, idempotencyKey: String, onDone: () -> Unit) {
        viewModelScope.launch {
            loading = true
            message = null
            val result = runCatching { FinanceApi(backendUrl).updateBudgetSettings(globalBufferVnd, globalBudgetVnd, idempotencyKey) }
            val saved = result.getOrNull()
            if (saved != null) {
                settings = saved
                settingsLoaded = true
                onDone()
                refresh()
                return@launch
            }
            message = result.exceptionOrNull()?.message ?: "Không lưu được cấu hình ngân sách"
            loading = false
        }
    }
}
