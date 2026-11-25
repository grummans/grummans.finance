package finance.grummans.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import finance.grummans.app.ui.Cream
import finance.grummans.app.ui.Green
import finance.grummans.app.ui.Muted
import finance.grummans.app.ui.Red
import finance.grummans.app.ui.TuiButton
import finance.grummans.app.ui.TuiCard
import finance.grummans.app.ui.TuiDialog
import finance.grummans.app.ui.TuiEmpty
import finance.grummans.app.ui.TuiGreenButton
import finance.grummans.app.ui.TuiInput
import finance.grummans.app.ui.TuiOption
import finance.grummans.app.ui.TuiPanel
import finance.grummans.app.ui.TuiRow
import finance.grummans.app.ui.TuiTabs
import finance.grummans.app.ui.Yellow
import finance.grummans.app.ui.amountOrNull
import finance.grummans.app.ui.money
import finance.grummans.app.ui.stamp
import finance.grummans.app.ui.tuiStyle
import java.time.Instant
import java.time.ZoneId
import java.time.OffsetDateTime
import java.util.UUID

@Composable
internal fun TxnsRoutes(txns: TxnsViewModel, route: String, onRoute: (String) -> Unit) {
    when (route) {
        "log" -> TerminalLog(txns) { onRoute("timeline") }
        "new" -> NewTransaction(txns) { onRoute("timeline") }
        else -> Timeline(txns, onNewTransaction = { onRoute("new") }, onOpenLog = { onRoute("log") })
    }
}

@Composable
private fun ModeTabs(txns: TxnsViewModel, active: String, onRoute: (String) -> Unit) {
    TuiTabs(
        tabs = listOf("TIMELINE", "LOG"),
        active = active,
        onSelect = { onRoute(if (it == "LOG") "log" else "timeline") },
    )
}

@Composable
private fun Timeline(txns: TxnsViewModel, onNewTransaction: () -> Unit, onOpenLog: () -> Unit) {
    LaunchedEffect(Unit) { txns.refresh() }
    var filtering by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<TransactionEntry?>(null) }

    ModeTabs(txns, active = "TIMELINE") { onOpenLog() }

    val groups = txns.transactions.groupBy { stamp(it.occurredAt, "day") }
    if (groups.isEmpty() && !txns.loading) {
        TuiPanel("TIMELINE") { TuiEmpty("Chưa có giao dịch nào trong khoảng này.") }
    }
    groups.forEach { (day, entries) ->
        TuiPanel(day) {
            entries.forEach { entry ->
                TransactionCard(entry) { picking = entry }
            }
        }
    }

    if (txns.message != null) Text(txns.message!!, style = tuiStyle(12, color = Red))

    TuiPanel("FILTERS") {
        TuiRow("Date range", listOfNotNull(txns.from.ifBlank { null }, txns.to.ifBlank { null }).joinToString(" → ").ifBlank { "ALL TIME" })
        TuiRow("Category", txns.categories.firstOrNull { it.id == txns.categoryFilterId }?.name ?: "ALL")
        TuiButton("FILTER", primary = false, onClick = { filtering = true })
    }

    TuiGreenButton("+ ADD TRANSACTION", onClick = onNewTransaction)

    if (filtering) {
        FilterDialog(txns, onDismiss = { filtering = false })
    }
    picking?.let { entry ->
        CategoryDialog(
            entry = entry,
            categories = txns.categories,
            busy = txns.loading,
            onPick = { categoryId -> txns.changeCategory(entry.id, categoryId, UUID.randomUUID().toString()) { picking = null } },
            onCreate = { name, key, done ->
                txns.createCategory(name, key) { created ->
                    txns.changeCategory(entry.id, created.id, UUID.randomUUID().toString()) { picking = null }
                    done()
                }
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun TransactionCard(entry: TransactionEntry, onClick: () -> Unit) {
    val title = entry.note?.takeIf { it.isNotBlank() } ?: entry.categoryName ?: entry.type
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        TuiCard(modifier = Modifier.clickable(onClick = onClick)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    title,
                    style = tuiStyle(14, FontWeight.Bold, Cream),
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Text(
                    if (entry.direction == "OUT") "-${money(entry.amountVnd)}" else "+${money(entry.amountVnd)}",
                    style = tuiStyle(14, FontWeight.Medium, Cream),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${stamp(entry.occurredAt, "short")} · ${stamp(entry.occurredAt, "clock")}", style = tuiStyle(11, color = Muted))
                Text(entry.categoryName?.uppercase() ?: entry.type, style = tuiStyle(11, color = Yellow))
            }
        }
        Text(
            "│ ${entry.accountName} · ${entry.type.lowercase()}" + if (entry.transferId != null) " · transfer" else "",
            style = tuiStyle(11, color = Muted),
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
private fun TerminalLog(txns: TxnsViewModel, onOpenTimeline: () -> Unit) {
    LaunchedEffect(Unit) { txns.refresh() }
    ModeTabs(txns, active = "LOG", onRoute = { onOpenTimeline() })

    val filter = txns.categories.firstOrNull { it.id == txns.categoryFilterId }?.name?.uppercase() ?: "ALL"
    TuiPanel("TRANSACTION_LOG") {
        if (txns.transactions.isEmpty()) TuiEmpty("Không có bản ghi nào.")
        txns.transactions.forEach { entry ->
            val amount = (if (entry.direction == "OUT") "-" else "+") + money(entry.amountVnd).removeSuffix("₫")
            Text(
                "${stamp(entry.occurredAt, "log")}  ${amount.padStart(12)}  ${(entry.categoryName ?: entry.type).uppercase()}",
                style = tuiStyle(12, color = if (entry.direction == "OUT") Cream else Green),
                maxLines = 1,
            )
        }
        Text("> ${txns.transactions.size} records · filter: $filter", style = tuiStyle(12, color = Muted))
    }

    TuiPanel("QUICK_FILTER") {
        TuiRow("Period", listOfNotNull(txns.from.ifBlank { null }, txns.to.ifBlank { null }).joinToString(" → ").ifBlank { "THIS MONTH" })
        TuiRow("Category", txns.categories.firstOrNull { it.id == txns.categoryFilterId }?.name ?: "ALL")
    }

    if (txns.message != null) Text(txns.message!!, style = tuiStyle(12, color = Red))
}

@Composable
private fun NewTransaction(txns: TxnsViewModel, onDone: () -> Unit) {
    var type by rememberSaveable { mutableStateOf("EXPENSE") }
    var accountId by rememberSaveable { mutableStateOf("") }
    var toAccountId by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    val key = rememberSaveable { UUID.randomUUID().toString() }

    var pickType by remember { mutableStateOf(false) }
    var pickAccount by remember { mutableStateOf(false) }
    var pickTarget by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }
    var editNote by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { txns.refresh() }

    val account = txns.accounts.firstOrNull { it.id == accountId } ?: txns.accounts.firstOrNull()
    val target = txns.accounts.firstOrNull { it.id == toAccountId }
    val category = txns.categories.firstOrNull { it.id == categoryId }
    val isTransfer = type == "TRANSFER"
    val value = amountOrNull(amount)
    val valid = value != null && value > 0 && account != null &&
        (!isTransfer || (target != null && target.id != account.id)) && !txns.loading

    TuiPanel("NEW_TRANSACTION") {
        TuiRow("Type", type)
        TuiOption(label = "Chọn loại", value = type, onClick = { pickType = true })

        TuiInput(
            label = "Amount / VND",
            value = amount,
            onValueChange = { amount = it },
            helper = "Số tiền giao dịch",
            numeric = true,
        )

        TuiOption(
            label = "CATEGORY",
            value = if (isTransfer) "transfer" else category?.name ?: "Chọn danh mục",
            onClick = { if (!isTransfer) pickCategory = true },
        )

        TuiRow("Date", date.ifBlank { "HÔM NAY" })
        TuiInput("Date / YYYY-MM-DD", date, { date = it }, helper = "Để trống là hôm nay")

        TuiRow("Account", account?.let { "${it.name} · ${it.kind}" } ?: "chưa có")
        TuiOption(label = "Chọn tài khoản", value = account?.name ?: "—", onClick = { pickAccount = true })
        if (isTransfer) {
            TuiRow("To account", target?.let { "${it.name} · ${it.kind}" } ?: "chưa chọn")
            TuiOption(label = "Chọn tài khoản nhận", value = target?.name ?: "—", onClick = { pickTarget = true })
        }

        TuiRow("Note", note.ifBlank { "—" })
        TuiOption(label = "Nhập ghi chú", value = "optional", onClick = { editNote = true })

        if (txns.message != null) Text(txns.message!!, style = tuiStyle(12, color = Red))
    }

    TuiButton(
        "SAVE TRANSACTION",
        enabled = valid,
        onClick = {
            val chosen = account ?: return@TuiButton
            val money = value ?: return@TuiButton
            txns.createTransaction(
                TransactionDraft(
                    accountId = chosen.id,
                    toAccountId = if (isTransfer) target?.id else null,
                    type = type,
                    amountVnd = money,
                    occurredAt = occurredAt(date),
                    categoryId = if (isTransfer) null else category?.id,
                    note = note,
                ),
                key,
            ) { onDone() }
        },
    )
    Text(
        "Form ngắn: nhập số tiền trước, ghi chú không bắt buộc. Transfer ghi hai posting, không tính là chi tiêu.",
        style = tuiStyle(11, color = Muted),
    )

    if (pickType) {
        PickerDialog("TRANSACTION_TYPE", listOf("EXPENSE", "INCOME", "TRANSFER", "REPAYMENT", "FEE", "REFUND"), type) {
            type = it
            pickType = false
        }
    }
    if (pickAccount) {
        PickerDialog(
            "ACCOUNT",
            txns.accounts.map { "${it.kind} · ${it.name}" }.ifEmpty { listOf("Chưa có tài khoản") },
            account?.let { "${it.kind} · ${it.name}" } ?: "",
        ) { label ->
            txns.accounts.firstOrNull { "${it.kind} · ${it.name}" == label }?.let { accountId = it.id }
            pickAccount = false
        }
    }
    if (pickTarget) {
        PickerDialog(
            "TO_ACCOUNT",
            txns.accounts.filter { it.id != account?.id }.map { "${it.kind} · ${it.name}" }.ifEmpty { listOf("Không còn tài khoản khác") },
            target?.let { "${it.kind} · ${it.name}" } ?: "",
        ) { label ->
            txns.accounts.firstOrNull { "${it.kind} · ${it.name}" == label }?.let { toAccountId = it.id }
            pickTarget = false
        }
    }
    if (pickCategory) {
        PickerDialog(
            "CATEGORY",
            txns.categories.map { it.name }.ifEmpty { listOf("Chưa có category") },
            category?.name ?: "",
        ) { label ->
            txns.categories.firstOrNull { it.name == label }?.let { categoryId = it.id }
            pickCategory = false
        }
    }
    if (editNote) {
        var draft by remember { mutableStateOf(note) }
        TuiDialog("NOTE", onDismiss = { editNote = false }) {
            TuiInput("Note", draft, { draft = it }, helper = "Ví dụ: Ăn trưa")
            TuiButton("OK", onClick = { note = draft; editNote = false })
        }
    }
}

@Composable
private fun PickerDialog(title: String, options: List<String>, selected: String, onPick: (String) -> Unit) {
    TuiDialog(title, onDismiss = { }) {
        options.forEach { option ->
            TuiOption(label = option, selected = option == selected, onClick = { onPick(option) })
        }
    }
}

@Composable
private fun FilterDialog(txns: TxnsViewModel, onDismiss: () -> Unit) {
    var from by remember { mutableStateOf(txns.from) }
    var to by remember { mutableStateOf(txns.to) }
    var categoryId by remember { mutableStateOf(txns.categoryFilterId) }

    TuiDialog("FILTERS", onDismiss = onDismiss) {
        TuiInput("From / YYYY-MM-DD", from, { from = it }, helper = "Để trống là không giới hạn")
        TuiInput("To / YYYY-MM-DD", to, { to = it }, helper = "Bao gồm ngày này")
        TuiOption(label = "All categories", selected = categoryId == null, onClick = { categoryId = null })
        txns.categories.forEach { category ->
            TuiOption(
                label = category.name,
                selected = category.id == categoryId,
                onClick = { categoryId = category.id },
            )
        }
        TuiButton("APPLY", onClick = {
            txns.updateFrom(from)
            txns.updateTo(to)
            txns.setCategoryFilter(categoryId)
            onDismiss()
        })
        TuiButton("CANCEL", primary = false, onClick = onDismiss)
    }
}

@Composable
private fun CategoryDialog(
    entry: TransactionEntry,
    categories: List<Category>,
    busy: Boolean,
    onPick: (String?) -> Unit,
    onCreate: (String, String, () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    val newKey = remember { UUID.randomUUID().toString() }

    TuiDialog("SET_CATEGORY", onDismiss = onDismiss) {
        Text("Tap để gán category cho giao dịch này.", style = tuiStyle(11, color = Muted))
        categories.forEach { category ->
            TuiOption(
                label = category.name,
                selected = category.id == entry.categoryId,
                onClick = { if (!busy) onPick(category.id) },
            )
        }
        if (entry.categoryId != null) {
            TuiOption(label = "Clear category", onClick = { if (!busy) onPick(null) })
        }
        TuiInput("New category", newName, { newName = it }, helper = "Tạo rồi gán luôn")
        TuiButton("CREATE AND APPLY", enabled = newName.isNotBlank() && !busy, onClick = {
            onCreate(newName.trim(), newKey) { onDismiss() }
        })
        TuiButton("CLOSE", primary = false, onClick = onDismiss)
    }
}

/** Empty/blank date means "now"; otherwise noon of that business day. */
private fun occurredAt(date: String): String {
    if (date.isBlank()) return OffsetDateTime.now().withNano(0).toString()
    return runCatching {
        OffsetDateTime.of(java.time.LocalDate.parse(date), java.time.LocalTime.NOON, ZoneId.of("Asia/Ho_Chi_Minh").rules.getOffset(Instant.now()))
    }.getOrElse { OffsetDateTime.now().withNano(0) }.toString()
}

