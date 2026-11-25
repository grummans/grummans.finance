package finance.grummans.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import finance.grummans.app.ui.Border
import finance.grummans.app.ui.Charcoal
import finance.grummans.app.ui.Cream
import finance.grummans.app.ui.Green
import finance.grummans.app.ui.Muted
import finance.grummans.app.ui.Panel
import finance.grummans.app.ui.Red
import finance.grummans.app.ui.TuiBar
import finance.grummans.app.ui.TuiBottomNav
import finance.grummans.app.ui.TuiButton
import finance.grummans.app.ui.TuiCard
import finance.grummans.app.ui.TuiDialog
import finance.grummans.app.ui.TuiEmpty
import finance.grummans.app.ui.TuiInput
import finance.grummans.app.ui.TuiOption
import finance.grummans.app.ui.TuiPanel
import finance.grummans.app.ui.TuiRow
import finance.grummans.app.ui.TuiStatusBar
import finance.grummans.app.ui.TuiTopBar
import finance.grummans.app.ui.Yellow
import finance.grummans.app.ui.money
import finance.grummans.app.ui.plainAmount
import finance.grummans.app.ui.stamp
import finance.grummans.app.ui.tuiStyle
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TerminalFinanceApp() }
    }
}

@Composable
private fun TerminalFinanceApp(bills: BillsViewModel = viewModel(), txns: TxnsViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf("TXNS") }
    var txnsRoute by rememberSaveable { mutableStateOf("timeline") }
    var billsRoute by rememberSaveable { mutableStateOf("agenda") }
    var showConnection by remember { mutableStateOf(bills.occurrenceId.isBlank()) }
    var linking by remember { mutableStateOf<Payment?>(null) }
    var editingAmount by remember { mutableStateOf(false) }
    var creatingAccount by remember { mutableStateOf(false) }

    val path = when (tab) {
        "HOME" -> "~/finance/home"
        "TXNS" -> when (txnsRoute) {
            "log" -> "~/finance/txns/log"
            "new" -> "~/finance/txns/new"
            else -> "~/finance/txns"
        }
        "BILLS" -> if (billsRoute == "agenda" || bills.occurrence == null) "~/finance/bills/agenda"
        else "~/finance/bills/" + (bills.occurrence?.obligationName?.lowercase()?.replace(" ", "-") ?: "detail")
        else -> "~/finance/budget"
    }

    Row(Modifier.fillMaxSize().background(Charcoal).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            TuiTopBar(path)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (tab) {
                    "TXNS" -> TxnsRoutes(txns, txnsRoute) { txnsRoute = it }
                    "BILLS" -> BillsRoutes(bills, billsRoute, { billsRoute = it }, {
                        bills.loadLinkCandidates()
                        linking = it
                    }, { editingAmount = true }, { showConnection = true })
                    "HOME" -> HomeScreen(txns, { creatingAccount = true }, { tab = "TXNS" })
                    else -> BudgetScreen()
                }
            }
            TuiBottomNav(active = tab, onSelect = { tab = it })
            TuiStatusBar()
        }
    }

    if (showConnection) {
        ConnectionDialog(
            initialUrl = bills.backendUrl,
            initialOccurrenceId = bills.occurrenceId,
            onDismiss = { showConnection = false },
            onConnect = { url, occurrenceId ->
                bills.connect(url, occurrenceId)
                txns.updateBackendUrl(url)
                showConnection = false
            },
        )
    }

    linking?.let { payment ->
        LinkPaymentDialog(
            payment = payment,
            candidates = bills.candidates,
            busy = bills.loading,
            onConfirm = { entry ->
                bills.linkPayment(payment.id, entry.id, UUID.randomUUID().toString()) { linking = null }
            },
            onDismiss = { linking = null },
        )
    }

    if (editingAmount && bills.occurrence != null) {
        EditAmountDialog(
            current = bills.occurrence!!.confirmedAmountVnd,
            busy = bills.loading,
            onSave = { amount, key -> bills.updateOccurrenceAmount(amount, key) { editingAmount = false } },
            onDismiss = { editingAmount = false },
        )
    }

    if (creatingAccount) {
        NewAccountDialog(
            busy = txns.loading,
            onCreate = { name, kind, opening, limit, key ->
                txns.createAccount(name, kind, opening, limit, key) { creatingAccount = false }
            },
            onDismiss = { creatingAccount = false },
        )
    }
}

@Composable
private fun HomeScreen(txns: TxnsViewModel, onNewAccount: () -> Unit, onOpenTxns: () -> Unit) {
    LaunchedEffect(Unit) { txns.refresh() }

    val total = txns.accounts.sumOf { it.openingBalanceVnd }
    TuiPanel("ACCOUNTS") {
        Text(money(total), style = tuiStyle(28, FontWeight.Bold, Cream))
        Text("Tổng số dư đầu kỳ, thẻ tín dụng không tính là tiền", style = tuiStyle(12, color = Muted))
        txns.accounts.forEach { account ->
            TuiRow(
                label = "${account.name} · ${account.kind}",
                value = if (account.kind == "CREDIT") "hạn mức ${money(account.creditLimitVnd ?: 0)}" else money(account.openingBalanceVnd),
                valueColor = if (account.kind == "CREDIT") Yellow else Green,
            )
        }
        if (txns.accounts.isEmpty()) TuiEmpty("Chưa có tài khoản nào.")
    }

    TuiPanel("RECENT_TRANSACTIONS") {
        if (txns.transactions.isEmpty()) {
            TuiEmpty("Chưa có giao dịch.")
        }
        txns.transactions.take(3).forEach { entry ->
            TuiRow(
                label = "${stamp(entry.occurredAt, "short")} · ${entry.categoryName ?: entry.type}",
                value = if (entry.direction == "OUT") "-${money(entry.amountVnd)}" else "+${money(entry.amountVnd)}",
                valueColor = if (entry.direction == "OUT") Red else Green,
            )
        }
        TuiButton("Mở TXNS", primary = false, onClick = onOpenTxns)
    }

    TuiButton("+ NEW ACCOUNT", onClick = onNewAccount)
}

@Composable
private fun BudgetScreen() {
    TuiPanel("BUDGET") {
        TuiEmpty("Ngân sách thuộc Phase 3, chưa có trong slice này.")
    }
}

@Composable
private fun BillsRoutes(
    bills: BillsViewModel,
    route: String,
    onRoute: (String) -> Unit,
    onLinkPayment: (Payment) -> Unit,
    onEditAmount: () -> Unit,
    onConnect: () -> Unit,
) {
    val occurrence = bills.occurrence
    when {
        occurrence == null -> TuiPanel("BILLS") {
            TuiEmpty(bills.message ?: "Chưa nạp kỳ nghĩa vụ nào.")
            TuiButton("CONNECT", onClick = onConnect)
        }

        route == "payment" -> PaymentForm(
            occurrence = occurrence,
            loading = bills.loading,
            pendingKey = bills.pendingKey,
            pendingAmount = bills.pendingAmount,
            message = bills.message,
            onSave = { amount, key -> bills.recordPayment(amount, key) { onRoute("detail") } },
            onCancel = { onRoute("detail") },
        )

        route == "detail" -> BillDetail(
            occurrence = occurrence,
            payments = bills.payments,
            message = bills.message,
            onRecordPayment = { onRoute("payment") },
            onEditAmount = onEditAmount,
            onLinkPayment = onLinkPayment,
            onBack = { onRoute("agenda") },
        )

        else -> BillsAgenda(
            occurrence = occurrence,
            payments = bills.payments,
            message = bills.message,
            loading = bills.loading,
            onOpen = { onRoute("detail") },
            onRecordPayment = { onRoute("payment") },
            onConnect = onConnect,
        )
    }
}

@Composable
private fun BillsAgenda(
    occurrence: Occurrence,
    payments: List<Payment>,
    message: String?,
    loading: Boolean,
    onOpen: () -> Unit,
    onRecordPayment: () -> Unit,
    onConnect: () -> Unit,
) {
    val paid = occurrence.paidAmountVnd
    val paidLabel = if (paid > 0) "Đã trả ${money(paid)}" else "Chưa thanh toán"

    TuiPanel(
        title = "UPCOMING / ${occurrence.status}",
        borderColor = if (occurrence.status == "OVERDUE") Red.copy(alpha = 0.7f) else Border,
    ) {
        TuiCard(borderColor = if (occurrence.status == "OVERDUE") Red else Yellow) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(occurrence.obligationName, style = tuiStyle(15, FontWeight.Bold, Cream))
                Text(money(occurrence.remainingAmountVnd), style = tuiStyle(15, FontWeight.Bold, Cream))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${stamp(occurrence.dueDate, "short")} · ${occurrence.status.lowercase()}",
                    style = tuiStyle(12, color = Muted),
                )
                Text(occurrence.status, style = tuiStyle(11, FontWeight.Medium, Yellow))
            }
        }
        Text("│ ${occurrence.status} · $paidLabel", style = tuiStyle(12, color = Yellow), modifier = Modifier)
        Text("${payments.size} payment · tap để xem chi tiết", style = tuiStyle(11, color = Muted))
        TuiButton("Mở", primary = false, enabled = !loading, onClick = onOpen)
    }

    if (message != null) Text(message, style = tuiStyle(12, color = Red))

    TuiButton("RECORD PAYMENT", enabled = occurrence.remainingAmountVnd > 0, onClick = onRecordPayment)
    TuiButton("CONNECT", primary = false, onClick = onConnect)
}

@Composable
private fun BillDetail(
    occurrence: Occurrence,
    payments: List<Payment>,
    message: String?,
    onRecordPayment: () -> Unit,
    onEditAmount: () -> Unit,
    onLinkPayment: (Payment) -> Unit,
    onBack: () -> Unit,
) {
    val confirmed = occurrence.confirmedAmountVnd.coerceAtLeast(1)
    TuiPanel("${occurrence.obligationName.uppercase()} / ${stamp(occurrence.dueDate, "short")}") {
        Text(money(occurrence.confirmedAmountVnd), style = tuiStyle(28, FontWeight.Bold, Cream))
        TuiRow("Due window", stamp(occurrence.dueDate, "short"))
        TuiRow("Status", occurrence.status, Yellow)
        TuiBar(occurrence.paidAmountVnd.toFloat() / confirmed)
        TuiRow("Paid", money(occurrence.paidAmountVnd), Green)
        TuiRow("Remaining", money(occurrence.remainingAmountVnd), Red)
    }

    TuiPanel("PAYMENT_HISTORY") {
        if (payments.isEmpty()) TuiEmpty("Chưa có payment nào.")
        payments.forEachIndexed { index, payment ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${stamp(payment.paidAt, "short")} · Payment ${"%02d".format(index + 1)}" +
                        if (payment.transactionId != null) " · linked" else "",
                    style = tuiStyle(12, color = Cream),
                )
                Text(money(payment.amountVnd), style = tuiStyle(12, FontWeight.Medium, Green))
            }
            if (payment.transactionId == null && payment.reversedAt == null) {
                TuiButton("LINK ${money(payment.amountVnd)}", primary = false, onClick = { onLinkPayment(payment) })
            }
        }
        if (payments.isNotEmpty()) {
            TuiRow("TOTAL_PAID / ALL TIME", money(payments.filter { it.reversedAt == null }.sumOf { it.amountVnd }), Yellow)
        }
        TuiRow("Period filter", "ALL TIME")
    }

    if (message != null) Text(message, style = tuiStyle(12, color = Red))

    TuiButton("RECORD PAYMENT", enabled = occurrence.remainingAmountVnd > 0, onClick = onRecordPayment)
    TuiButton("EDIT OCCURRENCE", primary = false, onClick = onEditAmount)
    TuiButton("BACK", primary = false, onClick = onBack)
}

@Composable
private fun PaymentForm(
    occurrence: Occurrence,
    loading: Boolean,
    pendingKey: String,
    pendingAmount: Long,
    message: String?,
    onSave: (Long, String) -> Unit,
    onCancel: () -> Unit,
) {
    var amount by rememberSaveable(pendingKey) {
        mutableStateOf(if (pendingAmount > 0) plainAmount(pendingAmount) else "")
    }
    val key = rememberSaveable(pendingKey) { pendingKey.ifBlank { UUID.randomUUID().toString() } }
    val digits = amount.filter(Char::isDigit)
    val value = digits.toLongOrNull()
    val valid = value != null && value > 0 && value <= occurrence.remainingAmountVnd && !loading

    TuiPanel("RECORD_PAYMENT") {
        TuiRow("Remaining", money(occurrence.remainingAmountVnd), Yellow)
        TuiInput(
            label = "Amount / VND",
            value = amount,
            onValueChange = { amount = it },
            helper = "Số tiền trả cho kỳ này",
            numeric = true,
        )
        if (message != null) Text(message, style = tuiStyle(12, color = Red))
        Text(
            "Retry dùng lại đúng Idempotency-Key nên không ghi trùng.",
            style = tuiStyle(11, color = Muted),
        )
    }

    TuiButton("SAVE PAYMENT", enabled = valid, onClick = { value?.let { onSave(it, key) } })
    TuiButton("CANCEL", primary = false, onClick = onCancel)
}

@Composable
private fun ConnectionDialog(
    initialUrl: String,
    initialOccurrenceId: String,
    onDismiss: () -> Unit,
    onConnect: (String, String) -> Unit,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var occurrenceId by remember { mutableStateOf(initialOccurrenceId) }

    TuiDialog(title = "CONNECTION", onDismiss = onDismiss) {
        Text(
            "USB debugging: adb reverse tcp:8080 tcp:8080 rồi dùng http://127.0.0.1:8080.",
            style = tuiStyle(11, color = Muted),
        )
        TuiInput("Server URL", url, { url = it }, helper = "http://127.0.0.1:8080")
        TuiInput("Occurrence ID", occurrenceId, { occurrenceId = it }, helper = "UUID của kỳ nghĩa vụ")
        TuiButton(
            "CONNECT",
            enabled = url.isNotBlank() && occurrenceId.isNotBlank(),
            onClick = { onConnect(url, occurrenceId) },
        )
        TuiButton("CANCEL", primary = false, onClick = onDismiss)
    }
}

@Composable
private fun LinkPaymentDialog(
    payment: Payment,
    candidates: List<TransactionEntry>,
    busy: Boolean,
    onConfirm: (TransactionEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    TuiDialog(title = "LINK_TRANSACTION", onDismiss = onDismiss) {
        Text(
            "Payment ${money(payment.amountVnd)} · chọn giao dịch ngân hàng tương ứng. Số tiền và trạng thái không đổi.",
            style = tuiStyle(11, color = Muted),
        )
        if (candidates.isEmpty()) {
            TuiEmpty("Không có giao dịch OUT chưa liên kết. Ghi chi tiêu ở TXNS trước.")
        }
        candidates.take(8).forEach { entry ->
            TuiOption(
                label = "${money(entry.amountVnd)} · ${entry.categoryName ?: entry.type}",
                value = stamp(entry.occurredAt, "short"),
                onClick = { if (!busy) onConfirm(entry) },
            )
        }
        TuiButton("CANCEL", primary = false, onClick = onDismiss)
    }
}

@Composable
private fun EditAmountDialog(
    current: Long,
    busy: Boolean,
    onSave: (Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf(plainAmount(current)) }
    val key = rememberSaveable { UUID.randomUUID().toString() }
    val value = amount.filter(Char::isDigit).toLongOrNull()

    TuiDialog(title = "EDIT_OCCURRENCE", onDismiss = onDismiss) {
        TuiInput("Confirmed / VND", amount, { amount = it }, helper = "Số tiền dự kiến của kỳ này", numeric = true)
        TuiButton("SAVE", enabled = value != null && value > 0 && !busy, onClick = { value?.let { onSave(it, key) } })
        TuiButton("CANCEL", primary = false, onClick = onDismiss)
    }
}

@Composable
private fun NewAccountDialog(
    busy: Boolean,
    onCreate: (String, String, Long, Long?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("CASH") }
    var opening by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableStateOf("") }
    val key = rememberSaveable { UUID.randomUUID().toString() }
    val openingValue = opening.filter(Char::isDigit).toLongOrNull() ?: 0
    val limitValue = limit.filter(Char::isDigit).toLongOrNull()
    val credit = kind == "CREDIT"

    TuiDialog(title = "NEW_ACCOUNT", onDismiss = onDismiss) {
        TuiInput("Name", name, { name = it }, helper = "Tên tài khoản")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("CASH", "BANK", "CREDIT").forEach { option ->
                Box(Modifier.weight(1f)) {
                    TuiOption(label = option, selected = kind == option, onClick = { kind = option })
                }
            }
        }
        if (credit) {
            TuiInput("Credit limit / VND", limit, { limit = it }, helper = "Hạn mức, không phải tiền", numeric = true)
        } else {
            TuiInput("Opening balance / VND", opening, { opening = it }, helper = "Số dư hiện tại", numeric = true)
        }
        TuiButton(
            "SAVE ACCOUNT",
            enabled = name.isNotBlank() && (!credit || (limitValue ?: 0) > 0) && !busy,
            onClick = {
                onCreate(name.trim(), kind, if (credit) 0 else openingValue, if (credit) limitValue else null, key)
            },
        )
        TuiButton("CANCEL", primary = false, onClick = onDismiss)
    }
}

@Suppress("unused")
private val panelBackground = Panel
