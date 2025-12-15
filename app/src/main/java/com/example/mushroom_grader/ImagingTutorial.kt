package com.example.mushroom_grader

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import com.example.mushroom_grader.databinding.ActivityImagingTutorialBinding

class ImagingTutorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImagingTutorialBinding
    private var currentStep = 0

    private val tutorialSteps = listOf(
        TutorialStep(
            titleRes = R.string.tutorial_intro_title,
            descriptionRes = R.string.tutorial_intro_description,
            visualRes = null, // No visual for intro
            tips = listOf(
                "• This quick tutorial teaches proper mushroom photography",
                "• Follow these 6 steps for accurate AI classification",
                "• Same technique every time = consistent results",
                "• Takes only 2 minutes to learn"
            )
        ),
        TutorialStep(
            titleRes = R.string.tutorial_background_title,
            descriptionRes = R.string.tutorial_background_description,
            visualRes = R.drawable.tutorial_visual_background,
            tips = listOf(
                "✅ GOOD: White paper, light cutting board, plain plate",
                "✅ GOOD: Clean surface with no clutter",
                "✅ GOOD: Contrasting color (dark mushroom on light background)",
                "",
                "❌ BAD: Patterned tablecloth or busy background",
                "❌ BAD: Similar colors (brown mushroom on brown table)",
                "",
                "💡 TIP: A sheet of white printer paper works perfectly!"
            )
        ),
        TutorialStep(
            titleRes = R.string.tutorial_lighting_title,
            descriptionRes = R.string.tutorial_lighting_description,
            visualRes = R.drawable.tutorial_visual_lighting,
            tips = listOf(
                "✅ GOOD: Near window with natural daylight",
                "✅ GOOD: Overcast day (soft, even lighting)",
                "✅ GOOD: Indirect sunlight (not directly on mushroom)",
                "",
                "❌ BAD: Direct sunlight (creates harsh shadows)",
                "❌ BAD: Phone flash (too bright, unnatural)",
                "❌ BAD: Dim room or artificial overhead lights",
                "",
                "💡 TIP: Morning or afternoon near a window is perfect!"
            )
        ),
        TutorialStep(
            titleRes = R.string.tutorial_distance_title,
            descriptionRes = R.string.tutorial_distance_description,
            visualRes = R.drawable.tutorial_visual_distance,
            tips = listOf(
                "✅ CORRECT: 13-17 cm (about hand-width away)",
                "✅ CORRECT: Mushroom fills 60-80% of guide frame",
                "✅ CORRECT: Can see cap shape, gills, and stem clearly",
                "",
                "❌ TOO CLOSE: 5-10 cm",
                "   Problem: Only see texture, lose overall shape",
                "",
                "❌ TOO FAR: 25+ cm",
                "   Problem: Mushroom too small, details lost",
                "",
                "💡 TIP: Use the on-screen guide frame to check!"
            )
        ),
        TutorialStep(
            titleRes = R.string.tutorial_angle_title,
            descriptionRes = R.string.tutorial_angle_description,
            visualRes = R.drawable.tutorial_visual_angle,
            tips = listOf(
                "✅ CORRECT: 45° angle (tilted view)",
                "   • Shows cap top AND gills/stem",
                "   • Best view for most mushroom types",
                "   • AI can see all important features",
                "",
                "❌ WRONG: Straight top-down (90°)",
                "   Problem: Can't see gills or stem",
                "",
                "❌ WRONG: Side view only (0-15°)",
                "   Problem: Can't see cap pattern",
                "",
                "💡 TIP: Imagine looking at mushroom from your eye level, then tilt phone slightly down!"
            )
        ),
        TutorialStep(
            titleRes = R.string.tutorial_focus_title,
            descriptionRes = R.string.tutorial_focus_description,
            visualRes = R.drawable.tutorial_visual_focus,
            tips = listOf(
                "✅ HOW TO FOCUS:",
                "   1. Tap directly on mushroom cap",
                "   2. Wait for green focus corners",
                "   3. See '✓ Focused' confirmation",
                "   4. Hold phone steady",
                "   5. Press capture button",
                "",
                "❌ DON'T:",
                "   • Don't capture without focusing first",
                "   • Don't move phone while capturing",
                "   • Don't tap background (focus on mushroom!)",
                "",
                "💡 TIP: If focus fails, try tapping again on a different part of the mushroom"
            )
        ),
        TutorialStep(
            titleRes = R.string.tutorial_framing_title,
            descriptionRes = R.string.tutorial_framing_description,
            visualRes = R.drawable.tutorial_visual_framing,
            tips = listOf(
                "✅ PERFECT FRAMING:",
                "   • Mushroom centered in guide frame",
                "   • Entire mushroom visible (don't crop)",
                "   • Small margin around edges",
                "   • Fills 60-80% of frame",
                "   • One mushroom only",
                "",
                "❌ WRONG:",
                "   • Mushroom cut off at edges",
                "   • Too much empty space",
                "   • Multiple mushrooms in frame",
                "   • Off-center positioning",
                "",
                "💡 TIP: Use the white dashed guide frame on camera screen!"
            )
        ),
        TutorialStep(
            titleRes = R.string.tutorial_ready_title,
            descriptionRes = R.string.tutorial_ready_description,
            visualRes = null, // No visual for ready screen
            tips = listOf(
                "🎉 You're ready to take perfect mushroom photos!",
                "",
                "📋 QUICK CHECKLIST:",
                "   □ Plain white/light background",
                "   □ Natural daylight near window",
                "   □ 13-17 cm distance (hand-width)",
                "   □ 45° angle to mushroom",
                "   □ Tap mushroom to focus",
                "   □ Center in guide frame",
                "",
                "✅ REMEMBER: Same setup = Same results!",
                "",
                "The app will guide you with on-screen tips as you capture."
            )
        )
    )

    @OptIn(ExperimentalCamera2Interop::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImagingTutorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showStep(currentStep)

        binding.btnNext.setOnClickListener {
            if (currentStep < tutorialSteps.size - 1) {
                currentStep++
                showStep(currentStep)
            } else {
                // Tutorial complete, open camera
                val intent = Intent(this, CameraActivity::class.java)
                startActivity(intent)
                finish()
            }
        }

        binding.btnSkip.setOnClickListener {
            // Skip tutorial
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding.btnPrevious.setOnClickListener {
            if (currentStep > 0) {
                currentStep--
                showStep(currentStep)
            }
        }
    }

    private fun showStep(step: Int) {
        val tutorialStep = tutorialSteps[step]

        binding.tvStepTitle.setText(tutorialStep.titleRes)
        binding.tvStepDescription.setText(tutorialStep.descriptionRes)

        // Show or hide visual based on step
        if (tutorialStep.visualRes != null) {
            binding.ivStepVisual.setImageResource(tutorialStep.visualRes)
            binding.ivStepVisual.visibility = android.view.View.VISIBLE
        } else {
            binding.ivStepVisual.visibility = android.view.View.GONE
        }

        // Build tips text
        val tipsText = tutorialStep.tips.joinToString("\n")
        binding.tvStepTips.text = tipsText

        // Update progress
        binding.tvProgress.text = getString(R.string.tutorial_step_progress, step + 1, tutorialSteps.size)

        // Update button states
        binding.btnPrevious.isEnabled = step > 0
        binding.btnNext.setText(
            if (step == tutorialSteps.size - 1) R.string.button_start_camera
            else R.string.button_next
        )
        binding.btnSkip.visibility = if (step == tutorialSteps.size - 1)
            android.view.View.GONE else android.view.View.VISIBLE
    }

    data class TutorialStep(
        val titleRes: Int,
        val descriptionRes: Int,
        val visualRes: Int?,
        val tips: List<String>
    )
}