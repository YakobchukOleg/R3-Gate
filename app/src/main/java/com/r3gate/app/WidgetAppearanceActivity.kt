package com.r3gate.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class WidgetAppearanceActivity : Activity() {
    private var appearance = WidgetAppearance()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("theme", appearance.theme?.key)
        outState.putInt("transparency", appearance.transparency ?: -1)
        outState.putBoolean("showObject", appearance.showObject)
        outState.putBoolean("showActions", appearance.showActions)
        outState.putBoolean("showGps", appearance.showGps)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        setResult(RESULT_CANCELED, result)
        val manager = AppWidgetManager.getInstance(this)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID ||
            manager.getAppWidgetInfo(id)?.provider != ComponentName(this, GateWidget::class.java)
        ) {
            finish()
            return
        }

        val store = WidgetAppearanceStore(this)
        appearance = savedInstanceState?.let { state ->
            WidgetAppearance(
                theme = state.getString("theme")?.let(WorkMeterTheme::fromKey),
                transparency = state.getInt("transparency", -1).takeIf { it >= 0 },
                showObject = state.getBoolean("showObject", true),
                showActions = state.getBoolean("showActions", true),
                showGps = state.getBoolean("showGps", true)
            )
        } ?: store.read(id)
        val appTheme = WorkMeterAppearance.theme(this)
        val palette = appTheme.palette
        val defaultTransparency = getSharedPreferences("r3_gate", MODE_PRIVATE)
            .getInt("widgetTransparency", 0).coerceIn(0, 50)
        fun dp(size: Int) = (size * resources.displayMetrics.density + 0.5f).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
            setBackgroundColor(palette.background)
        }
        val preview = FrameLayout(this)
        val sizes=listOf(144 to 68, 180 to 170, 300 to 90, 300 to 180, 300 to 260)
        var previewSize=sizes[3]
        val transparencyLabel = TextView(this).apply { setTextColor(palette.textPrimary) }
        val slider = SeekBar(this).apply {
            max = 50
            progress = appearance.transparency ?: defaultTransparency
            contentDescription = getString(R.string.widget_transparency)
        }
        fun previewAppearance() {
            transparencyLabel.text = "Przezroczystość tła: ${(appearance.transparency ?: defaultTransparency).coerceIn(0,50)}% · maks. 50%"
            preview.removeAllViews()
            preview.layoutParams=LinearLayout.LayoutParams(dp(previewSize.first),dp(previewSize.second))
            preview.addView(GateWidget.render(this,WidgetLayoutProfile.forBounds(previewSize.first,previewSize.second),appearance,interactive=false,widthDp=previewSize.first,heightDp=previewSize.second).apply(this,preview))
        }

        content.addView(TextView(this).apply {
            text = getString(R.string.widget_configure)
            textSize = 22f
            setTextColor(palette.textPrimary)
        })
        val themes = listOf(null) + WorkMeterTheme.entries
        lateinit var themeButton: android.widget.Button
        themeButton = workMeterButton(this, "", WorkMeterButtonRole.SECONDARY) {
            android.app.AlertDialog.Builder(this, WorkMeterAppearance.dialogStyle(appTheme))
                .setTitle(R.string.widget_theme)
                .setSingleChoiceItems(themes.map { it?.title ?: getString(R.string.widget_follow_app) }.toTypedArray(),
                    themes.indexOf(appearance.theme)) { dialog, position ->
                    appearance = appearance.copy(theme = themes[position])
                    dialog.dismiss()
                    themeButton.text = getString(R.string.widget_theme_value, appearance.theme?.title ?: getString(R.string.widget_follow_app))
                    previewAppearance()
                }.setNegativeButton(android.R.string.cancel, null).show()
        }.apply {
            text = getString(R.string.widget_theme_value, appearance.theme?.title ?: getString(R.string.widget_follow_app))
        }
        content.addView(themeButton)
        content.addView(transparencyLabel)
        content.addView(slider)
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                appearance = appearance.copy(transparency = progress)
                previewAppearance()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        val objectToggle = CheckBox(this).apply {
            setText(R.string.widget_show_object)
            setTextColor(palette.textPrimary)
            isChecked = appearance.showObject
            setOnCheckedChangeListener { _, checked ->
                appearance = appearance.copy(showObject = checked)
                previewAppearance()
            }
        }
        val actionsToggle = CheckBox(this).apply {
            setText(R.string.widget_show_actions)
            setTextColor(palette.textPrimary)
            isChecked = appearance.showActions
            setOnCheckedChangeListener { _, checked ->
                appearance = appearance.copy(showActions = checked)
                previewAppearance()
            }
        }
        val gpsToggle = CheckBox(this).apply {
            setText(R.string.widget_show_gps)
            setTextColor(palette.textPrimary)
            isChecked = appearance.showGps
            setOnCheckedChangeListener { _, checked ->
                appearance = appearance.copy(showGps = checked)
                previewAppearance()
            }
        }
        content.addView(gpsToggle)
        content.addView(objectToggle)
        content.addView(actionsToggle)
        sizes.forEach { size ->
            content.addView(workMeterButton(this,"Podgląd ${size.first} × ${size.second} dp",WorkMeterButtonRole.SECONDARY) {
                previewSize=size; previewAppearance()
            })
        }
        content.addView(preview,LinearLayout.LayoutParams(dp(previewSize.first),dp(previewSize.second)))
        content.addView(workMeterButton(this, getString(R.string.widget_reset), WorkMeterButtonRole.SECONDARY) {
            objectToggle.isChecked = true
            actionsToggle.isChecked = true
            gpsToggle.isChecked = true
            appearance = WidgetAppearance()
            slider.progress = defaultTransparency
            themeButton.text = getString(R.string.widget_theme_value, getString(R.string.widget_follow_app))
            previewAppearance()
        })
        content.addView(workMeterButton(this, getString(R.string.widget_save), WorkMeterButtonRole.PRIMARY) {
            store.save(id, appearance)
            GateWidget.update(this, manager, id)
            setResult(RESULT_OK, result)
            finish()
        })
        previewAppearance()
        setContentView(ScrollView(this).apply { addView(content) })
    }
}
