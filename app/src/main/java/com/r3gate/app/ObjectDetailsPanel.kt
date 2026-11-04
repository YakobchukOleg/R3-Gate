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
    private var selectedTab = ObjectDetailsTabs.Tab.OVERVIEW

    init {
        orientation = VERTICAL
        val state = ObjectDetailsUiModel.display(content)
        setBackgroundColor(backgroundColor)
        setPadding(dp(16), dp(12), dp(16), dp(16))
        val top = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(text(state.heading, green, 12f).apply {
            setTypeface(null, Typeface.BOLD)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        top.addView(action("Edytuj obiekt", onEdit),
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(top)
        addView(text(state.title, ink, 24f).apply {
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        })
        state.location?.let { addView(text("⌖ $it", muted, 13f)) }
        state.organization?.let { addView(text("◈ $it", muted, 13f)) }
        addView(action("Otwórz w Google Maps", onMaps))
        addView(text(state.archiveStatus, if (content.archived) muted else green, 12f).apply {
            setPadding(0, dp(8), 0, dp(8))
        })

        val shortcuts = LinearLayout(context).apply { orientation = HORIZONTAL }
        listOf(
            "Materiały" to onMaterials,
            "Zdjęcia" to onPhotos,
            "Notatki obiektu" to onNotes
        ).forEach { (title, callback) ->
            shortcuts.addView(action(title, callback).apply {
                minHeight = dp(52)
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(5)
            })
        }
        addView(shortcuts, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })
        val tabs = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val tabRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        tabs.addView(tabRow)
        for (tab in ObjectDetailsTabs.Tab.values()) {
            tabRow.addView(action(tab.label) {
                selectedTab = tab
                for (i in 0 until tabRow.childCount) {
                    val button = tabRow.getChildAt(i) as Button
                    val active = i == tab.ordinal
                    button.setTextColor(if (active) green else muted)
                    button.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
                }
                renderTab(tab, state)
            }.apply { contentDescription = "Zakładka: " + tab.label }, LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)))
        }
        addView(tabs, LayoutParams(LayoutParams.MATCH_PARENT, dp(54)))
        addView(tabBody, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        renderTab(ObjectDetailsTabs.Tab.OVERVIEW, state)
        addView(text(state.archiveHint, muted, 12f).apply {
            setPadding(0, dp(12), 0, dp(12))
        })
    }

    private fun renderTab(tab: ObjectDetailsTabs.Tab, state: ObjectDetailsUiModel.ViewState) {
        tabBody.removeAllViews()
        val tabSection = ObjectDetailsTabs.section(tab, counts)
        if (tab == ObjectDetailsTabs.Tab.OVERVIEW) {
            tabBody.addView(section("Przegląd"))
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
            tabBody.addView(section("Ważne informacje"))
            tabBody.addView(card().apply {
                addView(text(state.notes ?: "Brak notatek. Dodaj dane kontaktowe lub ważną informację.", muted, 13f))
            })
        } else {
            tabBody.addView(section(tabSection.heading))
            tabBody.addView(card().apply {
                addView(text(tabSection.description, muted, 14f))
                if (tab == ObjectDetailsTabs.Tab.TECHNICAL) {
                    addView(text("TAG: " + state.heading, ink, 13f))
                    state.location?.let { addView(text("⌖ " + it, ink, 13f)) }
                    state.organization?.let { addView(text("◈ " + it, ink, 13f)) }
                }
            })
            val callback: (() -> Unit)? = when (tab) {
                ObjectDetailsTabs.Tab.PHOTOS -> onPhotos
                ObjectDetailsTabs.Tab.MATERIALS -> onMaterials
                ObjectDetailsTabs.Tab.SERVICE -> onNotes
                ObjectDetailsTabs.Tab.TECHNICAL -> onMaps
                ObjectDetailsTabs.Tab.OVERVIEW -> null
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
