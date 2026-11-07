package com.r3gate.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Native Obiekty details composition matching the supplied HTML prototype v2.
 * All callbacks delegate to existing MainActivity actions.
 */
class ObjectDetailsPanel(
    context: Context,
    content: ObjectDetailsUiModel.Content,
    private val onEdit: () -> Unit,
    private val onMaps: () -> Unit,
    private val onPhotos: () -> Unit,
    private val onMaterials: () -> Unit,
    private val onNotes: () -> Unit,
    private val counts: ObjectDetailsTabs.Counts = ObjectDetailsTabs.Counts()
) : LinearLayout(context) {
    private val palette = WorkMeterAppearance.theme(context).palette
    private val ink = palette.textPrimary
    private val muted = palette.secondaryText
    private val green = palette.accentText
    private val backgroundColor = palette.background
    private val tabBody = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        val state = ObjectDetailsUiModel.display(content)
        setBackgroundColor(backgroundColor)
        setPadding(dp(16), dp(12), dp(16), dp(16))
        addView(text(state.title, ink, 24f).apply {
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        })
        state.location?.let { addView(text("⌖ $it", muted, 13f)) }
        state.organization?.let { addView(text("◈ $it", muted, 13f)) }
        val actions = LinearLayout(context).apply {
            orientation = HORIZONTAL
            isBaselineAligned = false
        }
        actions.addView(action("Edytuj obiekt", onEdit), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
            marginEnd = dp(4)
        })
        actions.addView(action("Otwórz w Google Maps", onMaps), LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(actions, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })
        addView(text(state.archiveStatus, if (content.archived) muted else green, 12f).apply {
            setPadding(0, dp(8), 0, dp(8))
        })

        val tabButtons = mutableListOf<Button>()
        for (group in ObjectDetailsTabs.Tab.entries.chunked(3)) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                isBaselineAligned = false
            }
            group.forEach { tab ->
                val button = action(tab.label) {
                    tabButtons.forEachIndexed { index, view ->
                        styleTab(view, index == tab.ordinal)
                    }
                    renderTab(tab, state)
                }.apply {
                    contentDescription = "Zakładka: " + tab.label
                    styleTab(this, tab == ObjectDetailsTabs.Tab.TODAY)
                }
                tabButtons.add(button)
                row.addView(button, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                    marginEnd = dp(4)
                })
            }
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            })
        }
        addView(tabBody, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        renderTab(ObjectDetailsTabs.Tab.TODAY, state)
        addView(text(state.archiveHint, muted, 12f).apply {
            setPadding(0, dp(12), 0, dp(12))
        })
    }

    private fun styleTab(button: Button, selected: Boolean) {
        button.isSelected = selected
        button.setTextColor(if (selected) green else ink)
        button.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun renderTab(tab: ObjectDetailsTabs.Tab, state: ObjectDetailsUiModel.ViewState) {
        tabBody.removeAllViews()
        val tabSection = ObjectDetailsTabs.section(tab, counts)
        tabBody.addView(section(tabSection.heading))
        if (tab == ObjectDetailsTabs.Tab.HISTORY) {
            tabBody.addView(card().apply {
                addView(text(state.historyHeading, ink, 16f).apply {
                    setTypeface(null, Typeface.BOLD)
                })
                addView(text(state.totalTime, green, 19f).apply {
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, dp(10), 0, dp(8))
                })
                state.emptyHistory?.let { addView(text(it, muted, 13f)) }
                for (entry in state.days) addView(text("${entry.date} · ${entry.duration}", ink, 13f).apply {
                    setPadding(0, dp(7), 0, dp(7))
                })
                state.legacyNote?.let { addView(text(it, muted, 12f)) }
            })
        } else if (tab == ObjectDetailsTabs.Tab.TODAY) {
            tabBody.addView(card().apply {
                addView(text(state.historyHeading, ink, 16f))
                addView(text(state.totalTime, green, 19f))
                state.emptyHistory?.let { addView(text(it, muted, 13f)) }
            })
            tabBody.addView(section("Ważne informacje"))
            tabBody.addView(card().apply {
                addView(text("Dane techniczne", ink, 16f))
                addView(text("TAG: " + state.heading, ink, 13f))
                state.location?.let { addView(text("⌖ " + it, ink, 13f)) }
                state.organization?.let { addView(text("◈ " + it, ink, 13f)) }
                addView(text(state.notes ?: "Brak ważnych informacji.", muted, 13f))
            })
        } else {
            tabBody.addView(card().apply { addView(text(tabSection.description, muted, 14f)) })
            val callback: (() -> Unit)? = when (tab) {
                ObjectDetailsTabs.Tab.PHOTOS -> onPhotos
                ObjectDetailsTabs.Tab.MATERIALS -> onMaterials
                ObjectDetailsTabs.Tab.TASKS -> onNotes
                ObjectDetailsTabs.Tab.TODAY, ObjectDetailsTabs.Tab.HISTORY -> null
            }
            if (tabSection.action != null && callback != null)
                tabBody.addView(action(tabSection.action, callback))
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()

    private fun text(value: String, color: Int, size: Float): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
        }

    private fun section(name: String): TextView = text(name, ink, 16f).apply {
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(19), 0, dp(9))
    }

    private fun card(): LinearLayout = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(dp(15), dp(14), dp(15), dp(14))
        background = GradientDrawable().apply {
            setColor(palette.surface)
            cornerRadius = dp(17).toFloat()
        }
    }

    private fun action(label: String, callback: () -> Unit): Button =
        workMeterButton(context, label, WorkMeterButtonRole.SECONDARY, callback)
}
