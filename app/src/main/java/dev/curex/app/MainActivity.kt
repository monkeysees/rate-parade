package dev.curex.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.MotionEvent
import android.widget.*
import java.text.DateFormat
import java.text.DecimalFormatSymbols
import java.util.Date

class MainActivity : Activity() {
    private val app get() = application as RateParadeApplication
    private var state = ConversionState()
    private var data = RateData(null, null)
    private var loaded = false
    private var refreshing = false
    private var changingText = false
    private lateinit var rows: LinearLayout
    private lateinit var rowScroll: ScrollView
    private lateinit var status: TextView
    private lateinit var empty: TextView
    private lateinit var refresh: Button
    private lateinit var add: Button
    private var rateDetails = ""
    private val rowViews = linkedMapOf<String, CurrencyRow>()
    private val handler = Handler(Looper.getMainLooper())
    private val statusTick = object : Runnable {
        override fun run() { if (loaded) updateStatus(); handler.postDelayed(this, 60_000) }
    }
    private val locale get() = resources.configuration.locales[0]
    private val separator get() = DecimalFormatSymbols.getInstance(locale).decimalSeparator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        app.storageExecutor.execute {
            val saved = app.store.readState()
            val cached = app.repository.load()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                state = saved
                // Android's instance state covers a rotation before a pending disk write completes.
                savedInstanceState?.takeIf { it.containsKey("order") }?.let { bundle ->
                    state = ConversionState(bundle.getStringArrayList("order") ?: ArrayList(saved.selected),
                        bundle.getString("source"), bundle.getString("input", saved.input),
                        bundle.getChar("separator", saved.decimalSeparator), bundle.getBoolean("initialized", saved.initialized))
                }
                if (state.decimalSeparator != separator) {
                    val localized = state.input.map { char ->
                        when {
                            char == state.decimalSeparator -> separator
                            char.digitToIntOrNull() != null -> '0' + char.digitToInt()
                            else -> char
                        }
                    }.joinToString("")
                    state = state.copy(input = localized, decimalSeparator = separator)
                }
                loaded = true
                acceptData(cached)
                requestRefresh(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(statusTick)
        if (loaded) requestRefresh(false)
    }

    override fun onPause() {
        handler.removeCallbacks(statusTick)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (loaded) {
            outState.putStringArrayList("order", ArrayList(state.selected))
            outState.putString("source", state.source)
            outState.putString("input", state.input)
            outState.putChar("separator", state.decimalSeparator)
            outState.putBoolean("initialized", state.initialized)
        }
        super.onSaveInstanceState(outState)
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(8))
            setBackgroundColor(getColor(R.color.paper))
            isFocusableInTouchMode = true
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(dp(24) + bars.left, dp(12) + bars.top, dp(24) + bars.right, dp(8) + bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(dp(24) + insets.systemWindowInsetLeft, dp(12) + insets.systemWindowInsetTop,
                    dp(24) + insets.systemWindowInsetRight, dp(8) + insets.systemWindowInsetBottom)
            }
            insets
        }
        val scrollContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollContent.addView(label(getString(R.string.subtitle).uppercase(locale), 11f).apply {
            letterSpacing = 0.18f
            setTextColor(getColor(R.color.accent))
            setPadding(0, dp(16), 0, dp(8))
        })
        scrollContent.addView(label(getString(R.string.app_name), 36f).apply {
            typeface = Typeface.create("serif", Typeface.NORMAL)
        })
        scrollContent.addView(label(getString(R.string.edit_hint), 14f).apply {
            setTextColor(getColor(R.color.muted))
            setPadding(0, dp(8), 0, dp(24))
        })
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        empty = label(getString(R.string.empty), 18f).apply { setPadding(0, dp(24), 0, dp(24)) }
        scrollContent.addView(empty)
        scrollContent.addView(rows)
        rowScroll = ScrollView(this).apply { addView(scrollContent); isVerticalScrollBarEnabled = false }
        root.addView(rowScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        add = quietButton(getString(R.string.add_currency), filled = true).apply {
            isEnabled = false
            setOnClickListener { showPicker() }
        }
        scrollContent.addView(add, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(20); bottomMargin = dp(24)
        })
        scrollContent.addView(divider())
        val rateBar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        status = label(getString(R.string.loading), 12f).apply {
            setTextColor(getColor(R.color.muted))
            setPadding(0, dp(16), dp(8), dp(8))
        }
        rateBar.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        refresh = quietButton(getString(R.string.refresh)).apply {
            isEnabled = false
            setOnClickListener { requestRefresh(true) }
        }
        rateBar.addView(refresh)
        scrollContent.addView(rateBar)
        scrollContent.addView(label(getString(R.string.provider), 12f).apply { setTextColor(getColor(R.color.muted)) })
        scrollContent.addView(quietButton(getString(R.string.rate_details)).apply {
            setOnClickListener {
                val notice = label(rateDetails + "\n\n" + getString(R.string.attribution) + "\n\n" + getString(R.string.privacy_notice), 15f).apply {
                    setPadding(dp(24), dp(12), dp(24), dp(12)); setTextIsSelectable(true)
                }
                AlertDialog.Builder(this@MainActivity).setTitle(R.string.rate_details)
                    .setView(ScrollView(this@MainActivity).apply { addView(notice) })
                    .setPositiveButton(R.string.close, null).show()
            }
        })
        setContentView(root)
        root.requestFocus()
    }

    private fun acceptData(fresh: RateData) {
        data = fresh
        if (!state.initialized && fresh.catalog != null && fresh.snapshot != null) {
            val available = fresh.catalog.currencies.filter { it.code in fresh.snapshot.rates }.map { it.code }
            val defaults = listOf("USD", "EUR", "GBP").filter { it in available }.ifEmpty { available.take(3) }
            if (defaults.isNotEmpty()) {
                state = ConversionState(defaults, defaults.first(), "1", separator, true)
                save()
            }
        }
        syncRows()
        updateAmounts()
        updateStatus()
        add.isEnabled = fresh.catalog != null && fresh.snapshot != null
    }

    private fun requestRefresh(force: Boolean) {
        if (refreshing) return
        refreshing = true
        updateStatus()
        app.storageExecutor.execute { app.repository.refresh(force).thenAccept { fresh -> runOnUiThread {
            if (!isDestroyed) {
                refreshing = false
                acceptData(fresh)
            }
        } } }
    }

    private fun save() {
        val saved = state
        app.storageExecutor.execute {
            try { app.store.writeState(saved) } catch (_: Exception) {
                runOnUiThread { if (!isDestroyed) Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun syncRows() {
        (rowViews.keys - state.selected.toSet()).forEach { code -> rows.removeView(rowViews.remove(code)?.container) }
        state.selected.forEachIndexed { index, code ->
            val row = rowViews.getOrPut(code) { createRow(code) }
            if (rows.indexOfChild(row.container) != index) {
                rows.removeView(row.container)
                rows.addView(row.container, index)
            }
            row.name.text = data.catalog?.currencies?.find { it.code == code }?.name ?: code
        }
        empty.visibility = if (state.selected.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun createRow(code: String): CurrencyRow {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, dp(12)) }
        val topMarker = dropMarker()
        val bottomMarker = dropMarker()
        container.addView(topMarker)
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val handle = quietButton("≡").apply {
            textSize = 24f
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER
            contentDescription = getString(R.string.drag_currency, code)
            setOnClickListener { showActions(this, code) }
            var downX = 0f
            var downY = 0f
            var dragging = false
            val slop = ViewConfiguration.get(context).scaledTouchSlop
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.x
                        downY = event.y
                        dragging = false
                        parent.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!dragging && (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop)) {
                            dragging = startDragAndDrop(ClipData.newPlainText("", ""), View.DragShadowBuilder(container), code, 0)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        parent.requestDisallowInterceptTouchEvent(false)
                        if (!dragging) performClick()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        parent.requestDisallowInterceptTouchEvent(false)
                        true
                    }
                    else -> true
                }
            }
        }
        val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(label(code, 15f).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = 0.1f
            setTextColor(getColor(R.color.accent))
        })
        val name = label(code, 12f).apply { setTextColor(getColor(R.color.muted)) }
        labels.addView(name)
        heading.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        heading.addView(handle, LinearLayout.LayoutParams(dp(48), dp(48)))
        container.addView(heading)
        val input = EditText(this).apply {
            textSize = 32f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(getColor(R.color.ink))
            setHintTextColor(getColor(R.color.muted))
            background = null
            setPadding(0, dp(4), 0, dp(8))
            setOnFocusChangeListener { _, focused ->
                setTextColor(getColor(if (focused) R.color.accent else R.color.ink))
            }
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            val zero = DecimalFormatSymbols.getInstance(locale).zeroDigit
            val digits = (0..9).map { zero + it }.joinToString("")
            keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789$digits-$separator")
            setRawInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
            filters = arrayOf(InputFilter.LengthFilter(ConversionState.MAX_INPUT))
            setSingleLine(true)
            minHeight = dp(52)
            contentDescription = getString(R.string.amount_description, code)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            isSaveEnabled = false
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (changingText) return
                    state = state.edit(code, s.toString(), separator)
                    save()
                    updateAmounts()
                }
            })
        }
        container.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        container.addView(divider())
        container.addView(bottomMarker)
        container.setOnDragListener { _, event ->
            val dragged = event.localState as? String
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> dragged in state.selected
                DragEvent.ACTION_DRAG_LOCATION -> {
                    topMarker.visibility = if (dragged != code && event.y < container.height / 2f) View.VISIBLE else View.GONE
                    bottomMarker.visibility = if (dragged != code && event.y >= container.height / 2f) View.VISIBLE else View.GONE
                    val rowLocation = IntArray(2)
                    val scrollLocation = IntArray(2)
                    container.getLocationOnScreen(rowLocation)
                    rowScroll.getLocationOnScreen(scrollLocation)
                    val pointerY = rowLocation[1] + event.y - scrollLocation[1]
                    when {
                        pointerY < dp(48) -> rowScroll.scrollBy(0, -dp(16))
                        pointerY > rowScroll.height - dp(48) -> rowScroll.scrollBy(0, dp(16))
                    }
                    true
                }
                DragEvent.ACTION_DROP -> {
                    val after = event.y >= container.height / 2f
                    topMarker.visibility = View.GONE
                    bottomMarker.visibility = View.GONE
                    if (dragged != null) applyMove(dragged, state.drop(dragged, code, after))
                    true
                }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                    topMarker.visibility = View.GONE
                    bottomMarker.visibility = View.GONE
                    true
                }
                else -> true
            }
        }
        return CurrencyRow(container, name, input)
    }

    private fun updateAmounts() {
        changingText = true
        try {
            rowViews.forEach { (code, row) ->
                val text = if (code == state.source) state.input else state.amount(code, data.snapshot)?.let { displayAmount(it, code, locale) }.orEmpty()
                // Never replace an unchanged Editable: this preserves selection and IME composing spans.
                if (row.input.text.toString() != text) row.input.setText(text)
                row.input.hint = when {
                    data.snapshot?.rates?.containsKey(code) != true -> getString(R.string.missing_rate)
                    state.source !in data.snapshot?.rates.orEmpty() -> getString(R.string.conversion_unavailable)
                    else -> "—"
                }
                row.input.error = if (code == state.source && state.input.isNotEmpty() && parseInput(state.input, state.decimalSeparator) == null)
                    getString(R.string.invalid_input) else null
            }
        } finally { changingText = false }
    }

    private fun showActions(anchor: View, code: String) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, R.string.move_up).isEnabled = state.selected.indexOf(code) > 0
            menu.add(0, 2, 1, R.string.move_down).isEnabled = state.selected.indexOf(code) < state.selected.lastIndex
            menu.add(0, 3, 2, R.string.remove)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> move(code, state.selected.indexOf(code) - 1)
                    2 -> move(code, state.selected.indexOf(code) + 1)
                    3 -> { state = state.remove(code); save(); syncRows(); updateAmounts() }
                }
                true
            }
            show()
        }
    }

    private fun move(code: String, position: Int) {
        applyMove(code, state.move(code, position))
    }

    private fun applyMove(code: String, moved: ConversionState) {
        if (moved == state) return
        val focus = currentFocus as? EditText
        val start = focus?.selectionStart ?: 0
        val end = focus?.selectionEnd ?: 0
        state = moved
        save(); syncRows()
        focus?.let { it.requestFocus(); it.setSelection(start.coerceIn(0, it.length()), end.coerceIn(0, it.length())) }
        rows.announceForAccessibility(getString(R.string.moved, code, state.selected.indexOf(code) + 1))
    }

    private fun showPicker() {
        val catalog = data.catalog ?: return
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        val search = EditText(this).apply { setHint(R.string.search_hint); setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT }
        content.addView(search)
        content.addView(label(getString(R.string.catalog_explanation), 12f))
        val list = ListView(this)
        content.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)))
        val none = label(getString(R.string.no_matches), 16f)
        content.addView(none)
        list.emptyView = none
        var choices = emptyList<CurrencyInfo>()
        fun filter(query: String) {
            choices = catalog.currencies.filter { currency ->
                currency.code !in state.selected && currency.code in data.snapshot?.rates.orEmpty() &&
                    (currency.code.contains(query, true) || currency.name.contains(query, true))
            }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, choices.map { "${it.code} · ${it.name}" })
        }
        filter("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filter(s.toString().trim()) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        val dialog = AlertDialog.Builder(this).setTitle(R.string.add_currency).setView(content).setNegativeButton(R.string.close, null).create()
        list.setOnItemClickListener { _, _, position, _ ->
            state = state.add(choices[position].code)
            save(); syncRows(); updateAmounts()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun updateStatus() {
        if (!::status.isInitialized) return
        refresh.isEnabled = loaded && !refreshing
        val snapshot = data.snapshot
        val messages = mutableListOf<String>()
        if (refreshing) messages += getString(R.string.refreshing)
        if (!online()) messages += getString(R.string.offline)
        if (data.error) messages += getString(R.string.refresh_failed)
        if (snapshot == null) messages += getString(R.string.no_rates) else {
            if (refreshDue(snapshot.fetchedAt, System.currentTimeMillis())) messages += getString(R.string.stale)
            val dates = snapshot.rates.values.map { it.date }
            messages += if (dates.min() == dates.max()) getString(R.string.effective, dates.min().toString())
                else getString(R.string.mixed_dates, dates.min().toString(), dates.max().toString())
        }
        if (data.catalog == null) messages += getString(R.string.no_catalog)
        status.text = messages.joinToString("\n")
        if (snapshot != null) {
            messages += getString(R.string.cached)
            messages += getString(R.string.fetched, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(snapshot.fetchedAt)))
        }
        rateDetails = messages.joinToString("\n")
    }

    private fun online(): Boolean {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    private fun quietButton(value: String, filled: Boolean = false) = Button(this).apply {
        text = value
        textSize = 14f
        isAllCaps = false
        minHeight = dp(48)
        minimumHeight = dp(48)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(16), dp(8), dp(16), dp(8))
        stateListAnimator = null
        setTextColor(ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(getColor(R.color.muted), getColor(R.color.accent))
        ))
        val shape = GradientDrawable().apply {
            setColor(getColor(if (filled) R.color.wash else R.color.paper))
            cornerRadius = dp(8).toFloat()
        }
        background = RippleDrawable(ColorStateList.valueOf(getColor(R.color.rule)), shape, null)
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(getColor(R.color.rule))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value; textSize = size; setTextColor(getColor(R.color.ink))
    }
    private fun dropMarker() = View(this).apply {
        setBackgroundColor(getColor(R.color.accent))
        visibility = View.GONE
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private data class CurrencyRow(val container: LinearLayout, val name: TextView, val input: EditText)
}
