package com.example.localmusicplayer.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.localmusicplayer.R
import com.example.localmusicplayer.service.SleepTimerManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Dialog for setting and managing the sleep timer.
 * Shows preset time options, a custom input, and cancel option when active.
 */
class SleepTimerDialog : DialogFragment() {

    companion object {
        private val PRESET_MINUTES = intArrayOf(15, 30, 45, 60, 90)
        const val TAG = "SleepTimerDialog"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val context = requireContext()
        val dp = context.resources.displayMetrics.density

        // Root container
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt())
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
            // Rounded corners via a shape background
            background = createRoundedBackground(context)
        }

        // Title
        val title = TextView(context).apply {
            text = getString(R.string.sleep_timer_title)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (16 * dp).toInt())
        }
        root.addView(title)

        // If timer is active, show remaining time and cancel button
        if (SleepTimerManager.isActive.value) {
            val remainingText = TextView(context).apply {
                setTextColor(ContextCompat.getColor(context, R.color.accent))
                textSize = 32f
                gravity = Gravity.CENTER
                setPadding(0, (8 * dp).toInt(), 0, (16 * dp).toInt())
            }
            root.addView(remainingText)

            // Observe remaining time
            lifecycleScope.launch {
                SleepTimerManager.remainingMillis.collectLatest {
                    if (isAdded) {
                        remainingText.text = SleepTimerManager.getRemainingFormatted()
                    }
                }
            }

            val cancelButton = createStyledButton(context, dp, getString(R.string.sleep_timer_cancel), true)
            cancelButton.setOnClickListener {
                SleepTimerManager.cancel()
                Toast.makeText(context, R.string.sleep_timer_cancelled, Toast.LENGTH_SHORT).show()
                dismiss()
            }
            root.addView(cancelButton, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8 * dp).toInt()
            })

            // Divider
            val divider = android.view.View(context).apply {
                setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
            }
            root.addView(divider, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1 * dp).toInt()
            ).apply {
                topMargin = (16 * dp).toInt()
                bottomMargin = (8 * dp).toInt()
            })

            // Label for setting a new timer
            val newTimerLabel = TextView(context).apply {
                text = "O establecer nuevo:"
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, (4 * dp).toInt(), 0, (8 * dp).toInt())
            }
            root.addView(newTimerLabel)
        }

        // Preset buttons grid (2 columns)
        val gridContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Create rows of 2 buttons each
        for (i in PRESET_MINUTES.indices step 2) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            // First button in row
            val btn1 = createStyledButton(context, dp, getString(R.string.sleep_timer_minutes, PRESET_MINUTES[i]), false)
            val minutes1 = PRESET_MINUTES[i]
            btn1.setOnClickListener { startTimer(minutes1) }
            row.addView(btn1, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginEnd = (4 * dp).toInt()
            })

            // Second button in row (if exists)
            if (i + 1 < PRESET_MINUTES.size) {
                val btn2 = createStyledButton(context, dp, getString(R.string.sleep_timer_minutes, PRESET_MINUTES[i + 1]), false)
                val minutes2 = PRESET_MINUTES[i + 1]
                btn2.setOnClickListener { startTimer(minutes2) }
                row.addView(btn2, LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = (4 * dp).toInt()
                })
            } else {
                // Empty spacer
                val spacer = android.view.View(context)
                row.addView(spacer, LinearLayout.LayoutParams(0, 0, 1f))
            }

            gridContainer.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * dp).toInt()
            })
        }

        root.addView(gridContainer)

        // Custom input section
        val customContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, (4 * dp).toInt())
        }

        val customInput = EditText(context).apply {
            hint = getString(R.string.sleep_timer_custom_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_disabled))
            textSize = 16f
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_elevated))
        }

        customContainer.addView(customInput, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginEnd = (8 * dp).toInt()
        })

        val customButton = createStyledButton(context, dp, getString(R.string.sleep_timer_set), false)
        customButton.setOnClickListener {
            val text = customInput.text.toString().trim()
            val minutes = text.toIntOrNull()
            if (minutes != null && minutes > 0) {
                startTimer(minutes)
            } else {
                Toast.makeText(context, R.string.sleep_timer_invalid, Toast.LENGTH_SHORT).show()
            }
        }
        customContainer.addView(customButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        root.addView(customContainer)

        dialog.setContentView(root)
        dialog.window?.setLayout(
            (300 * dp).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        return dialog
    }

    private fun startTimer(minutes: Int) {
        SleepTimerManager.start(minutes)
        Toast.makeText(requireContext(), getString(R.string.sleep_timer_started, minutes), Toast.LENGTH_SHORT).show()
        dismiss()
    }

    private fun createStyledButton(context: android.content.Context, dp: Float, text: String, isAccent: Boolean): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            if (isAccent) {
                setTextColor(ContextCompat.getColor(context, R.color.background))
                setBackgroundColor(ContextCompat.getColor(context, R.color.accent))
            } else {
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setBackgroundColor(ContextCompat.getColor(context, R.color.surface_elevated))
            }
            isClickable = true
            isFocusable = true
        }
    }

    private fun createRoundedBackground(context: android.content.Context): android.graphics.drawable.Drawable {
        val dp = context.resources.displayMetrics.density
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, R.color.surface))
            cornerRadius = 16 * dp
        }
    }
}
