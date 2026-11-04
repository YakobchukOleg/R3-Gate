package com.r3gate.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Standalone Obiekty list view based on the supplied HTML prototype (v2).
 * Presentation only: callbacks delegate selection and archive state to the owner.
 * No persistence, GPS or object lifecycle decisions occur in this view.
 */
class ObjectListPanel(
    context: Context,
    private val onOpenObject: (String) -> Unit,
    private val onAddObject: () -> Unit,
    private val onArchiveChanged: (Boolean) -> Unit = {}
) : LinearLayout(context) {
    private val palette = WorkMeterAppearance.theme(context).palette
    private val ink = palette.textPrimary
    private val muted = palette.secondaryText
    private val bg = palette.background
    private val green = palette.accentText
    private var items: List<ObjectListUiModel.Item> = emptyList()
    private var filterState = ObjectFilterUiState.State()
    private var query = ""
    private val cityChips = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val results = LinearLayout(context).apply { orientation = VERTICAL }
    private val archiveToggle = TextView(context)
    private val groupToggle = TextView(context)
    private val count = TextView(context)

    init {
        orientation = VERTICAL
        setBackgroundColor(bg)
        val pad = dp(16)
        setPadding(pad, pad, pad, pad)

        val search = EditText(context).apply {
            hint = "Szukaj nazwy, miasta, firmy…"
            setSingleLine(true)
            textSize = 14f
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rounded(palette.surface, 16)
            contentDescription = "Szukaj obiektu"
        }
        addView(search, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty()
                render()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val options = LinearLayout(context).apply { orientation = HORIZONTAL }
        archiveToggle.apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(ink)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(palette.surface, 20)
            setOnClickListener { filterState = ObjectFilterUiState.toggleArchive(filterState); onArchiveChanged(filterState.archived); render() }
        }
        groupToggle.apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(ink)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(palette.surface, 20)
            setOnClickListener { filterState = ObjectFilterUiState.toggleGrouping(filterState); render() }
        }
        options.addView(archiveToggle, LayoutParams(0, dp(44), 1f))
        options.addView(groupToggle, LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) })
        addView(options, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })
        val chipsScroll = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(cityChips)
        }
        addView(chipsScroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = dp(8)
        })
        count.apply { textSize = 12f; setTextColor(muted) }
        addView(count, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12); bottomMargin = dp(8)
        })

        // The parent screen already scrolls; avoid a nested weighted ScrollView.
        addView(results, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        val add = workMeterButton(context, "＋ Dodaj obiekt", WorkMeterButtonRole.PRIMARY, onAddObject).apply {
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            contentDescription = "Dodaj obiekt"
        }
        addView(add, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        render()
    }

    fun submitObjects(value: List<ObjectListUiModel.Item>) {
        items = value.toList()
        filterState = ObjectFilterUiState.restore(filterState, ObjectListUiModel.activeCities(items))
        render()
    }

    fun showArchivedObjects(value: Boolean) {
        filterState = filterState.copy(archived = value, city = null)
        render()
    }

    fun setCityFilter(city: String?) {
        filterState = ObjectFilterUiState.city(filterState, city)
        render()
    }

    private fun render() {
        val archived = filterState.archived
        val grouped = filterState.grouped
        archiveToggle.text = if (archived) "Archiwum ✓" else "Wszystkie"
        groupToggle.text = if (grouped) "▤ Grupuj: miasta" else "☷ Lista"
        archiveToggle.contentDescription = if (archived) "Pokaż aktywne obiekty" else "Pokaż archiwum obiektów"
        groupToggle.contentDescription = if (grouped) "Wyłącz grupowanie" else "Grupuj według miast"
        cityChips.removeAllViews()
        val cities = if (archived) emptyList() else ObjectListUiModel.activeCities(items)
        val choices: List<String?> = listOf(null) + cities
        for (city in choices) {
            val active = filterState.city == city
            val chip = label(city ?: "Wszystkie miasta", if (active) palette.onPrimary else ink, 12f).apply {
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(7), dp(12), dp(7))
                background = rounded(if (active) palette.primary else palette.surface, 18)
                isClickable = true
                isFocusable = true
                contentDescription = if (city == null) "Wszystkie miasta" else "Filtr miasta: " + city
                isSelected = active
                setOnClickListener { filterState = ObjectFilterUiState.city(filterState, city); render() }
            }
            cityChips.addView(chip, LayoutParams(LayoutParams.WRAP_CONTENT, dp(38)).apply {
                rightMargin = dp(8)
            })
        }
        val display = ObjectListUiModel.display(items, archived, query, filterState.city)
        count.text = ObjectCountLabel.format(display.rows.size, archived)
        results.removeAllViews()
        if (display.emptyMessage != null) {
            results.addView(label(display.emptyMessage, muted, 14f).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(36), 0, dp(36))
            })
        }
        for (group in ObjectListUiModel.groups(display, grouped, archived)) {
            results.addView(label("${group.heading}  ·  ${group.rows.size}", ink, 14f).apply {
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(4), dp(14), 0, dp(6))
            })
            for (row in group.rows) {
                val card = LinearLayout(context).apply {
                    orientation = VERTICAL
                    background = rounded(palette.surface, 17)
                    setPadding(dp(15), dp(12), dp(15), dp(12))
                    contentDescription = row.accessibilityLabel
                    isFocusable = true
                    isClickable = true
                    setOnClickListener { onOpenObject(row.id) }
                }
                val badge = label(row.tag.ifBlank { "Obiekt" }, muted, 11f).apply {
                    setTypeface(null, Typeface.BOLD)
                    background = rounded(bg, 8)
                    setPadding(dp(8), dp(5), dp(8), dp(5))
                }
                val header = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                header.addView(badge, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                header.addView(label(row.status, if (row.status == "Aktywny") green else muted, 11f))
                card.addView(header)
                card.addView(label(row.name, ink, 16f).apply {
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, dp(9), 0, dp(5))
                })
                if (row.addressLine.isNotBlank()) {
                    card.addView(label("⌖ " + row.addressLine, muted, 13f))
                }
                if (row.organization.isNotBlank() && row.organization != row.name) {
                    card.addView(label("◈ " + row.organization, muted, 13f).apply {
                        setPadding(0, dp(3), 0, 0)
                    })
                }
                card.addView(label("→ Szczegóły", muted, 12f).apply {
                    gravity = Gravity.END
                    setPadding(0, dp(12), 0, 0)
                })
                results.addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(9)
                })
            }
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun label(value: String, color: Int, size: Float) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
    }
}
