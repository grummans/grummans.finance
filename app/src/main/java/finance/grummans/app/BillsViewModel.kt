package finance.grummans.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

internal class BillsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("terminal_finance", 0)

    var backendUrl by mutableStateOf(preferences.getString("backend_url", "http://127.0.0.1:8080").orEmpty())
        private set
    var occurrenceId by mutableStateOf(preferences.getString("occurrence_id", "").orEmpty())
        private set
    var occurrence by mutableStateOf<Occurrence?>(null)
        private set
    var payments by mutableStateOf<List<Payment>>(emptyList())
        private set
    var candidates by mutableStateOf<List<TransactionEntry>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
        private set
	var loading by mutableStateOf(false)
		private set
	var pendingKey by mutableStateOf(preferences.getString("pending_key", "").orEmpty())
		private set
	var pendingAmount by mutableStateOf(preferences.getLong("pending_amount", 0))
		private set
	private val pendingBackendUrl = preferences.getString("pending_backend_url", "").orEmpty()
	private val pendingOccurrenceId = preferences.getString("pending_occurrence_id", "").orEmpty()

    fun connect(url: String, id: String) {
        backendUrl = url.trim().removeSuffix("/")
        occurrenceId = id.trim()
		if (pendingKey.isNotBlank() && (backendUrl != pendingBackendUrl || occurrenceId != pendingOccurrenceId)) {
			clearPendingPayment()
		}
        preferences.edit().putString("backend_url", backendUrl).putString("occurrence_id", occurrenceId).apply()
        reload()
    }

    fun reload() {
        if (occurrenceId.isBlank()) {
            message = "Enter an occurrence ID to load a bill."
            return
        }
        viewModelScope.launch {
            loading = true
            message = null
            runCatching { FinanceApi(backendUrl).getOccurrence(occurrenceId) }
                .onSuccess { occurrence = it; loadPayments() }
                .onFailure { message = it.message ?: "Could not load bill." }
            loading = false
        }
    }

	fun loadPayments() {
		val targetUrl = backendUrl
		val targetOccurrenceId = occurrenceId
		viewModelScope.launch {
			runCatching { FinanceApi(targetUrl).getPayments(targetOccurrenceId) }
				.onSuccess { if (backendUrl == targetUrl && occurrenceId == targetOccurrenceId) payments = it }
				.onFailure { if (backendUrl == targetUrl && occurrenceId == targetOccurrenceId) message = it.message ?: "Could not load payments." }
		}
	}

	fun loadLinkCandidates() {
		viewModelScope.launch {
			runCatching { FinanceApi(backendUrl).listTransactions(from = null, to = null, categoryId = null, limit = 100) }
				.onSuccess { entries ->
					// only a posting that takes money out of a real account can back a payment
					candidates = entries.filter { it.direction == "OUT" && it.type != "INCOME" && it.type != "REFUND" && it.transferId == null }
				}
				.onFailure { message = it.message ?: "Could not load transactions." }
		}
	}

	fun linkPayment(paymentId: String, transactionId: String, idempotencyKey: String, onDone: () -> Unit) {
		viewModelScope.launch {
			loading = true
			message = null
			runCatching { FinanceApi(backendUrl).linkPayment(paymentId, transactionId, idempotencyKey) }
				.onSuccess {
					loadPayments()
					onDone()
				}
				.onFailure { message = it.message ?: "Could not link the payment." }
			loading = false
		}
	}

	fun updateOccurrenceAmount(amountVnd: Long, idempotencyKey: String, onDone: () -> Unit) {
		viewModelScope.launch {
			loading = true
			message = null
			runCatching { FinanceApi(backendUrl).updateOccurrenceAmount(occurrenceId, amountVnd, idempotencyKey) }
				.onSuccess {
					occurrence = it
					onDone()
				}
				.onFailure { message = it.message ?: "Could not change the amount." }
			loading = false
		}
	}

	fun recordPayment(amountVnd: Long, idempotencyKey: String, onSuccess: () -> Unit) {
		val targetUrl = backendUrl
		val targetOccurrenceId = occurrenceId
		pendingKey = idempotencyKey
		pendingAmount = amountVnd
		preferences.edit().putString("pending_key", pendingKey).putLong("pending_amount", pendingAmount).putString("pending_backend_url", targetUrl).putString("pending_occurrence_id", targetOccurrenceId).apply()
		viewModelScope.launch {
			loading = true
			message = null
			runCatching { FinanceApi(targetUrl).recordPayment(targetOccurrenceId, amountVnd, idempotencyKey) }
				.onSuccess {
					if (backendUrl == targetUrl && occurrenceId == targetOccurrenceId) {
						occurrence = it
						loadPayments()
						clearPendingPayment()
						onSuccess()
					}
				}
				.onFailure { if (backendUrl == targetUrl && occurrenceId == targetOccurrenceId) message = it.message ?: "Could not record payment. Retry uses the same key." }
			loading = false
		}
	}

	private fun clearPendingPayment() {
		pendingKey = ""
		pendingAmount = 0
		preferences.edit().remove("pending_key").remove("pending_amount").remove("pending_backend_url").remove("pending_occurrence_id").apply()
	}
}

