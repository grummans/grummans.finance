package finance.grummans.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

internal enum class TxnsMode { Timeline, TerminalLog }

// The TXNS tab: timeline/terminal-log view of transactions with date and category
// filters, plus the mutations Slice 2 needs (account, category, transaction, category fix).
internal class TxnsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("terminal_finance", 0)

    var backendUrl by mutableStateOf(preferences.getString("backend_url", "http://127.0.0.1:8080").orEmpty())
        private set
    var mode by mutableStateOf(TxnsMode.Timeline)
        private set
    var from by mutableStateOf("")
        private set
    var to by mutableStateOf("")
        private set
    var categoryFilterId by mutableStateOf<String?>(null)
        private set
    var transactions by mutableStateOf<List<TransactionEntry>>(emptyList())
        private set
    var categories by mutableStateOf<List<Category>>(emptyList())
        private set
    var accounts by mutableStateOf<List<Account>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun updateBackendUrl(url: String) {
        backendUrl = url.trim().removeSuffix("/")
        preferences.edit().putString("backend_url", backendUrl).apply()
        refresh()
    }

    fun chooseMode(value: TxnsMode) {
        mode = value
    }

    fun updateFrom(value: String) {
        from = value.trim()
    }

    fun updateTo(value: String) {
        to = value.trim()
    }

    fun setCategoryFilter(categoryId: String?) {
        categoryFilterId = categoryId
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            message = null
            val api = FinanceApi(backendUrl)
            runCatching {
                if (categories.isEmpty()) categories = api.getCategories()
                if (accounts.isEmpty()) accounts = api.getAccounts()
                api.listTransactions(from = from, to = to, categoryId = categoryFilterId)
            }
                .onSuccess { transactions = it }
                .onFailure { message = it.message ?: "Could not load transactions." }
            loading = false
        }
    }

    fun changeCategory(transactionId: String, categoryId: String?, idempotencyKey: String, onDone: () -> Unit) {
        viewModelScope.launch {
            loading = true
            message = null
            runCatching { FinanceApi(backendUrl).updateTransactionCategory(transactionId, categoryId, idempotencyKey) }
                .onSuccess { updated ->
                    transactions = transactions.map { if (it.id == updated.id) updated else it }
                    onDone()
                }
                .onFailure { message = it.message ?: "Could not change the category." }
            loading = false
        }
    }

    fun createCategory(name: String, idempotencyKey: String, onDone: (Category) -> Unit) {
        viewModelScope.launch {
            loading = true
            message = null
            runCatching { FinanceApi(backendUrl).createCategory(name, idempotencyKey) }
                .onSuccess { created ->
                    if (categories.none { it.id == created.id }) categories = categories + created
                    onDone(created)
                }
                .onFailure { message = it.message ?: "Could not create the category." }
            loading = false
        }
    }

    fun createAccount(name: String, kind: String, openingBalanceVnd: Long, creditLimitVnd: Long?, idempotencyKey: String, onDone: () -> Unit) {
        viewModelScope.launch {
            loading = true
            message = null
            runCatching { FinanceApi(backendUrl).createAccount(name, kind, openingBalanceVnd, creditLimitVnd, idempotencyKey) }
                .onSuccess { created ->
                    if (accounts.none { it.id == created.id }) accounts = accounts + created
                    onDone()
                }
                .onFailure { message = it.message ?: "Could not create the account." }
            loading = false
        }
    }

    fun createTransaction(draft: TransactionDraft, idempotencyKey: String, onDone: () -> Unit) {
        viewModelScope.launch {
            loading = true
            message = null
            runCatching { FinanceApi(backendUrl).createTransaction(draft, idempotencyKey) }
                .onSuccess {
                    onDone()
                    // refetch so a transfer shows both postings and filters stay applied
                    refresh()
                }
                .onFailure { message = it.message ?: "Could not record the transaction." }
            loading = false
        }
    }
}
