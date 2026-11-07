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
    initialFilter: ObjectFilterUiState.State = ObjectFilterUiState.State(),
    initialQuery: String = "",
    private val onFilterChanged: (ObjectFilterUiState.State, String) -> Unit = { _, _ -> }
) : LinearLayout(context) {
    private val palette = WorkMeterAppearance.theme(context).palette
    private val ink = palette.textPrimary
    private val muted = palette.secondaryText
    private val bg = palette.background
    private val green = palette.accentText
    private var items: List<ObjectListUiModel.Item> = emptyList()
    private var filterState = initialFilter
    private var query = initialQuery
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

        val add = workMeterButton(context, context.getString(R.string.add_object_button), WorkMeterButtonRole.PRIMARY, onAddObject).apply {
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            contentDescription = context.getString(R.string.add_object)
        }
        addView(add, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })

        val search = EditText(context).apply {
            hint = context.getString(R.string.search)
            setSingleLine(true)
            textSize = 14f
            setTextColor(ink)
            setHintTextColor(muted)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rounded(palette.surface, 16)
            contentDescription = context.getString(R.string.search_objects_description)
            setText(initialQuery)
        }
        addView(search, LayoutParams(LayoutParams.MATCH_PARENT, dp(52)))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty()
                filtersChanged()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val options = LinearLayout(context).apply { orientation = HORIZONTAL }
        archiveToggle.apply {
            textSize = 13f
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            isFocusable = true
            setTextColor(ink)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(palette.surface, 20)
            setOnClickListener { filterState = ObjectFilterUiState.toggleArchive(filterState); filtersChanged() }
        }
        groupToggle.apply {
            visibility=View.GONE
            textSize = 13f
            gravity = Gravity.CENTER
            minimumHeight = dp(48)
            isFocusable = true
            setTextColor(ink)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = rounded(palette.surface, 20)
            setOnClickListener { filterState = ObjectFilterUiState.toggleGrouping(filterState); filtersChanged() }
        }
        options.addView(archiveToggle, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        options.addView(groupToggle, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8) })
        addView(options, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })
        val chipsScroll = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(cityChips)
        }
        addView(chipsScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
        count.apply { textSize = 12f; setTextColor(muted) }
        addView(count, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12); bottomMargin = dp(8)
        })

        // The parent screen already scrolls; avoid a nested weighted ScrollView.
        addView(results, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        render()
    }

    fun submitObjects(value: List<ObjectListUiModel.Item>) {
        items = value.toList()
        filterState = ObjectFilterUiState.restore(filterState, ObjectListUiModel.activeCities(items))
        filtersChanged()
    }

    private fun filtersChanged() {
        onFilterChanged(filterState, query)
        render()
    }

    private fun render() {
        val archived = filterState.archived
        val grouped = filterState.grouped
        archiveToggle.isSelected = archived
        groupToggle.isSelected = grouped
        archiveToggle.text = if (archived) context.getString(R.string.archive_checked) else context.getString(R.string.active_objects)
        groupToggle.text = if (grouped) context.getString(R.string.group_cities) else context.getString(R.string.list_ungrouped)
        archiveToggle.contentDescription = if (archived) context.getString(R.string.show_active_objects) else context.getString(R.string.show_archive_objects)
        groupToggle.contentDescription = if (grouped) context.getString(R.string.disable_grouping) else context.getString(R.string.group_by_city)
        cityChips.removeAllViews()
        val cities = if (archived) emptyList() else ObjectListUiModel.activeCities(items)
        val choices: List<String?> = listOf(null) + cities
        for (city in choices) {
            val active = filterState.city == city
            val chip = label(city ?: context.getString(R.string.all_cities), if (active) palette.onPrimary else ink, 12f).apply {
                gravity = Gravity.CENTER
                minimumHeight = dp(48)
                setPadding(dp(12), dp(7), dp(12), dp(7))
                background = rounded(if (active) palette.primary else palette.surface, 18)
                isClickable = true
                isFocusable = true
                contentDescription = if (city == null) context.getString(R.string.all_cities) else context.getString(R.string.city_filter_prefix) + city
                isSelected = active
                setOnClickListener { filterState = ObjectFilterUiState.city(filterState, city); filtersChanged() }
            }
            cityChips.addView(chip, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                rightMargin = dp(8)
            })
        }
        val display = ObjectListUiModel.display(items, archived, query, filterState.city)
        val total = display.rows.size
        count.text = if (context.resources.configuration.locales[0].language == AppLanguage.RUSSIAN.tag) {
            resources.getQuantityString(R.plurals.object_count, total, total) + " · " +
                context.getString(if (archived) R.string.archive else R.string.active_list)
        } else {
            ObjectCountLabel.format(total, archived)
        }
        results.removeAllViews()
        if (display.emptyMessage != null) {
            val emptyMessage = when {
                query.isNotBlank() || filterState.city != null -> R.string.no_matching_objects
                archived -> R.string.no_archived_objects
                else -> R.string.no_active_objects
            }
            results.addView(label(context.getString(emptyMessage), muted, 14f).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(36), 0, dp(36))
            })
        }
        for (group in ObjectListUiModel.groups(display, grouped, archived)) {
            val heading = when {
                !grouped || archived -> context.getString(R.string.objects)
                group.rows.all { it.city.isBlank() } -> context.getString(R.string.no_city)
                else -> group.heading
            }
            results.addView(label("$heading  ·  ${group.rows.size}", ink, 14f).apply {
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(4), dp(14), 0, dp(6))
            })
            for (row in group.rows) {
                val card = LinearLayout(context).apply {
                    orientation = VERTICAL
                    background = rounded(palette.surface, 17)
                    setPadding(dp(15), dp(12), dp(15), dp(12))
                    contentDescription = listOf(row.title.trim(),
                        context.getString(if (archived) R.string.archived_status else R.string.active_status))
                        .filter(String::isNotEmpty).joinToString(", ")
                    isFocusable = true
                    isClickable = true
                    setOnClickListener { onOpenObject(row.id) }
                }
                val badge = label(row.tag.ifBlank { context.getString(R.string.work_object) }, muted, 11f).apply {
                    setTypeface(null, Typeface.BOLD)
                    background = rounded(bg, 8)
                    setPadding(dp(8), dp(5), dp(8), dp(5))
                }
                val header = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                header.addView(badge, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                val status = context.getString(if (archived) R.string.archived_status else R.string.active_status)
                header.addView(label(status, if (archived) muted else green, 11f))
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
