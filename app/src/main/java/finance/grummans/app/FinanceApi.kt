package finance.grummans.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

internal data class Occurrence(
    val id: String,
    val obligationName: String,
    val dueDate: String,
    val confirmedAmountVnd: Long,
    val paidAmountVnd: Long,
    val remainingAmountVnd: Long,
    val status: String,
    val isEstimate: Boolean,
)

internal data class Account(
    val id: String,
    val name: String,
    val kind: String,
    val openingBalanceVnd: Long,
    val creditLimitVnd: Long?,
    val openedOn: String,
)

internal data class Category(val id: String, val name: String)

internal data class TransactionEntry(
    val id: String,
    val accountId: String,
    val accountName: String,
    val direction: String,
    val type: String,
    val amountVnd: Long,
    val occurredAt: String,
    val categoryId: String?,
    val categoryName: String?,
    val note: String?,
    val transferId: String?,
)

internal data class Payment(
    val id: String,
    val amountVnd: Long,
    val paidAt: String,
    val reversedAt: String?,
    val transactionId: String?,
)

internal data class TransactionDraft(
    val accountId: String,
    val toAccountId: String?,
    val type: String,
    val amountVnd: Long,
    val occurredAt: String,
    val categoryId: String?,
    val note: String?,
)

internal data class SalaryReceipt(
    val id: String,
    val amountVnd: Long,
    val receivedOn: String,
)

internal data class BudgetSettings(val globalBufferVnd: Long, val globalBudgetVnd: Long?)

internal data class BudgetCycle(
    val start: String,
    val end: String,
    val daysRemaining: Int,
    val projected: Boolean,
)

internal data class ForecastItem(
    val obligationName: String,
    val dueDate: String,
    val remainingVnd: Long,
    val certainty: String,
    val overdue: Boolean,
)

internal data class BudgetSnapshot(
    val cycle: BudgetCycle,
    val cashOnHandVnd: Long,
    val reservedConfirmedVnd: Long,
    val reservedExpectedVnd: Long,
    val globalBufferVnd: Long,
    val globalBudgetVnd: Long?,
    val spentInCycleVnd: Long,
    val availableVnd: Long,
    val spendableVnd: Long,
    val dailyAllowanceVnd: Long,
    val warnings: List<String>,
    val forecast: List<ForecastItem>,
)

internal class FinanceApi(baseUrl: String) {
    private val baseUrl = baseUrl.trim().removeSuffix("/").also {
        require(it.startsWith("http://") || it.startsWith("https://")) { "Server URL must start with http:// or https://" }
    }

    suspend fun getOccurrence(occurrenceId: String): Occurrence = request("GET", "/api/v1/occurrences/$occurrenceId").toOccurrence()

    suspend fun recordPayment(occurrenceId: String, amountVnd: Long, idempotencyKey: String): Occurrence {
        val body = JSONObject()
            .put("amount_vnd", amountVnd)
            .put("paid_at", Instant.now().toString())
            .toString()
        return request("POST", "/api/v1/occurrences/$occurrenceId/payments", body, idempotencyKey).toOccurrence()
    }

    suspend fun getPayments(occurrenceId: String): List<Payment> =
        parseItems(request("GET", "/api/v1/occurrences/$occurrenceId/payments"), "payments").map(JSONObject::toPayment)

    suspend fun linkPayment(paymentId: String, transactionId: String, idempotencyKey: String): Unit {
        val body = JSONObject().put("transaction_id", transactionId).toString()
        request("PATCH", "/api/v1/payments/$paymentId/transaction", body, idempotencyKey)
    }

    suspend fun updateOccurrenceAmount(occurrenceId: String, amountVnd: Long, idempotencyKey: String): Occurrence {
        val body = JSONObject().put("amount_vnd", amountVnd).toString()
        return request("PATCH", "/api/v1/occurrences/$occurrenceId", body, idempotencyKey).toOccurrence()
    }

    suspend fun getAccounts(): List<Account> = parseItems(request("GET", "/api/v1/accounts"), "accounts").map(JSONObject::toAccount)

    suspend fun createAccount(name: String, kind: String, openingBalanceVnd: Long, creditLimitVnd: Long?, idempotencyKey: String): Account {
        val body = JSONObject()
            .put("name", name)
            .put("kind", kind)
            .put("opening_balance_vnd", openingBalanceVnd)
        if (creditLimitVnd != null) body.put("credit_limit_vnd", creditLimitVnd)
        return JSONObject(request("POST", "/api/v1/accounts", body.toString(), idempotencyKey)).toAccount()
    }

    suspend fun getCategories(): List<Category> = parseItems(request("GET", "/api/v1/categories"), "categories").map(JSONObject::toCategory)

    suspend fun createCategory(name: String, idempotencyKey: String): Category =
        JSONObject(request("POST", "/api/v1/categories", JSONObject().put("name", name).toString(), idempotencyKey)).toCategory()

    suspend fun listTransactions(from: String?, to: String?, categoryId: String?, limit: Int = 100): List<TransactionEntry> {
        val query = buildList {
            if (!from.isNullOrBlank()) add("from=" + encode(from))
            if (!to.isNullOrBlank()) add("to=" + encode(to))
            if (!categoryId.isNullOrBlank()) add("category_id=" + encode(categoryId))
            add("limit=$limit")
        }.joinToString("&")
        return parseItems(request("GET", "/api/v1/transactions?$query"), "transactions").map(JSONObject::toTransactionEntry)
    }

    suspend fun createTransaction(draft: TransactionDraft, idempotencyKey: String): List<TransactionEntry> {
        val body = JSONObject()
            .put("account_id", draft.accountId)
            .put("type", draft.type)
            .put("amount_vnd", draft.amountVnd)
            .put("occurred_at", draft.occurredAt)
        // the server rejects a non-empty to_account_id outside a transfer
        if (draft.type == "TRANSFER") draft.toAccountId?.let { body.put("to_account_id", it) }
        draft.categoryId?.let { body.put("category_id", it) }
        draft.note?.takeIf { it.isNotBlank() }?.let { body.put("note", it) }
        return parseItems(request("POST", "/api/v1/transactions", body.toString(), idempotencyKey), "transactions")
            .map(JSONObject::toTransactionEntry)
    }

    suspend fun updateTransactionCategory(transactionId: String, categoryId: String?, idempotencyKey: String): TransactionEntry {
        // an explicit null clears the category, so the field is always sent
        val body = JSONObject().put("category_id", categoryId ?: JSONObject.NULL).toString()
        return JSONObject(request("PATCH", "/api/v1/transactions/$transactionId", body, idempotencyKey)).toTransactionEntry()
    }

    suspend fun getBudget(): BudgetSnapshot = JSONObject(request("GET", "/api/v1/budget/current")).toBudgetSnapshot()

    suspend fun getBudgetSettings(): BudgetSettings =
        JSONObject(request("GET", "/api/v1/budget/settings")).toBudgetSettings()

    suspend fun updateBudgetSettings(globalBufferVnd: Long, globalBudgetVnd: Long?, idempotencyKey: String): BudgetSettings {
        // an explicit null clears the total budget, so the field is always sent
        val body = JSONObject()
            .put("global_buffer_vnd", globalBufferVnd)
            .put("global_budget_vnd", globalBudgetVnd ?: JSONObject.NULL)
            .toString()
        return JSONObject(request("PUT", "/api/v1/budget/settings", body, idempotencyKey)).toBudgetSettings()
    }

    suspend fun listSalaryReceipts(): List<SalaryReceipt> =
        parseItems(request("GET", "/api/v1/salary-receipts"), "receipts").map(JSONObject::toSalaryReceipt)

    suspend fun recordSalaryReceipt(amountVnd: Long, receivedOn: String, idempotencyKey: String): SalaryReceipt {
        val body = JSONObject().put("amount_vnd", amountVnd).put("received_on", receivedOn).toString()
        return JSONObject(request("POST", "/api/v1/salary-receipts", body, idempotencyKey)).toSalaryReceipt()
    }

    private suspend fun request(method: String, path: String, body: String? = null, idempotencyKey: String? = null): String = withContext(Dispatchers.IO) {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            if (idempotencyKey != null) setRequestProperty("Idempotency-Key", idempotencyKey)
        }
        try {
            if (body != null) connection.outputStream.bufferedWriter().use { it.write(body) }
            val source = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = source?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (connection.responseCode !in 200..299) {
                val message = JSONObject(response).optString("error", "Server returned ${connection.responseCode}")
                error(message)
            }
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun parseItems(payload: String, key: String): List<JSONObject> {
        val items = JSONObject(payload).optJSONArray(key) ?: JSONArray()
        return (0 until items.length()).map { items.getJSONObject(it) }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

private fun String.toOccurrence(): Occurrence {
    val value = JSONObject(this)
    return Occurrence(
        id = value.getString("id"),
        obligationName = value.getString("obligation_name"),
        dueDate = value.getString("due_date"),
        confirmedAmountVnd = value.getLong("confirmed_amount_vnd"),
        paidAmountVnd = value.getLong("paid_amount_vnd"),
        remainingAmountVnd = value.getLong("remaining_amount_vnd"),
        status = value.getString("status"),
        isEstimate = value.optBoolean("is_estimate", false),
    )
}

private fun JSONObject.optionalString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun JSONObject.toAccount() = Account(
    id = getString("id"),
    name = getString("name"),
    kind = getString("kind"),
    openingBalanceVnd = getLong("opening_balance_vnd"),
    creditLimitVnd = if (isNull("credit_limit_vnd")) null else getLong("credit_limit_vnd"),
    openedOn = getString("opened_on"),
)

internal fun JSONObject.toPayment() = Payment(
    id = getString("id"),
    amountVnd = getLong("amount_vnd"),
    paidAt = getString("paid_at"),
    reversedAt = optionalString("reversed_at"),
    transactionId = optionalString("transaction_id"),
)

internal fun JSONObject.toCategory() = Category(id = getString("id"), name = getString("name"))

internal fun JSONObject.toTransactionEntry() = TransactionEntry(
    id = getString("id"),
    accountId = getString("account_id"),
    accountName = optString("account_name"),
    direction = getString("direction"),
    type = getString("type"),
    amountVnd = getLong("amount_vnd"),
    occurredAt = getString("occurred_at"),
    categoryId = optionalString("category_id"),
    categoryName = optionalString("category_name"),
    note = optionalString("note"),
    transferId = optionalString("transfer_id"),
)

internal fun JSONObject.toSalaryReceipt() = SalaryReceipt(
    id = getString("id"),
    amountVnd = getLong("amount_vnd"),
    receivedOn = getString("received_on"),
)

internal fun JSONObject.toBudgetSettings() = BudgetSettings(
    globalBufferVnd = getLong("global_buffer_vnd"),
    globalBudgetVnd = if (isNull("global_budget_vnd")) null else getLong("global_budget_vnd"),
)

internal fun JSONObject.toBudgetSnapshot(): BudgetSnapshot {
    val cycle = getJSONObject("cycle")
    val warnings = optJSONArray("warnings") ?: JSONArray()
    val forecast = optJSONArray("forecast") ?: JSONArray()
    return BudgetSnapshot(
        cycle = BudgetCycle(
            start = cycle.getString("start"),
            end = cycle.getString("end"),
            daysRemaining = cycle.getInt("days_remaining"),
            projected = cycle.getBoolean("projected"),
        ),
        cashOnHandVnd = getLong("cash_on_hand_vnd"),
        reservedConfirmedVnd = getLong("reserved_confirmed_vnd"),
        reservedExpectedVnd = getLong("reserved_expected_vnd"),
        globalBufferVnd = getLong("global_buffer_vnd"),
        globalBudgetVnd = if (isNull("global_budget_vnd")) null else getLong("global_budget_vnd"),
        spentInCycleVnd = getLong("spent_in_cycle_vnd"),
        availableVnd = getLong("available_vnd"),
        spendableVnd = getLong("spendable_vnd"),
        dailyAllowanceVnd = getLong("daily_allowance_vnd"),
        warnings = (0 until warnings.length()).map { warnings.getString(it) },
        forecast = (0 until forecast.length()).map { forecast.getJSONObject(it).toForecastItem() },
    )
}

private fun JSONObject.toForecastItem() = ForecastItem(
    obligationName = getString("obligation_name"),
    dueDate = getString("due_date"),
    remainingVnd = getLong("remaining_vnd"),
    certainty = getString("certainty"),
    overdue = getBoolean("overdue"),
)
