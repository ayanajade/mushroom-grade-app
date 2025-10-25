package com.example.mushroom_grader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.example.mushroom_grader.databinding.ActivityResultBinding
import kotlin.random.Random

class ResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        displayResults()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            navigateToMain()
        }

        binding.retakeButton.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
            finish()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    private fun displayResults() {
        val isPoisonous = intent.getBooleanExtra("is_poisonous", Random.nextBoolean())
        val imageUriString = intent.getStringExtra("image_uri")

        // Load image if provided
        imageUriString?.let { uriString ->
            val imageUri = Uri.parse(uriString)
            binding.detectedImageView.setImageURI(imageUri)
            binding.previewImageView.setImageURI(imageUri)
        } ?: run {
            // Set a placeholder image for camera captures
            binding.detectedImageView.setImageResource(R.drawable.mushroom_logo)
            binding.previewImageView.setImageResource(R.drawable.mushroom_logo)
        }

        if (isPoisonous) {
            displayPoisonousResults()
        } else {
            displayEdibleResults()
        }
    }

    private fun displayPoisonousResults() {
        // Sample poisonous mushroom data
        val poisonousSpecies = listOf(
            "Death Cap", "Destroying Angel", "False Morel",
            "Jack O'Lantern", "Deadly Galerina"
        )
        val habitats = listOf(
            "Oak forests, near roots",
            "Coniferous and deciduous forests",
            "Sandy soil, disturbed areas",
            "Decaying hardwood stumps",
            "Decaying conifer wood"
        )

        val randomSpecies = poisonousSpecies.random()
        val randomHabitat = habitats.random()

        // Update UI for poisonous mushroom
        binding.speciesTextView.text = randomSpecies
        binding.speciesTextView.setTextColor(ContextCompat.getColor(this, R.color.danger_red))

        binding.typeTextView.text = getString(R.string.poisonous)
        binding.typeTextView.setTextColor(ContextCompat.getColor(this, R.color.danger_red))

        binding.habitatTextView.text = randomHabitat
        binding.habitatTextView.setTextColor(ContextCompat.getColor(this, R.color.danger_red))

        // Hide grade and nutrients for poisonous mushrooms
        binding.gradeLayout.visibility = View.GONE
        binding.nutrientsLayout.visibility = View.GONE

        // Change card background to danger
        val infoCard = binding.infoCard
        infoCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.danger_red))

        // Update text colors for visibility on red background
        binding.speciesTextView.setTextColor(ContextCompat.getColor(this, R.color.white))
        binding.typeTextView.setTextColor(ContextCompat.getColor(this, R.color.white))
        binding.habitatTextView.setTextColor(ContextCompat.getColor(this, R.color.white))

        // Update label colors
        val labels = listOf(
            binding.root.findViewById<android.widget.TextView>(R.id.speciesTextView).parent as android.view.ViewGroup
        )
    }

    private fun displayEdibleResults() {
        // Sample edible mushroom data
        val edibleSpecies = listOf(
            "Oyster Mushroom", "Shiitake", "Button Mushroom",
            "Portobello", "Cremini", "Chanterelle"
        )
        val grades = listOf("A", "B", "C")
        val habitats = listOf(
            "Deciduous trees, logs",
            "Hardwood logs and stumps",
            "Compost, grasslands",
            "Forest floors, near oaks",
            "Coniferous forests"
        )
        val nutrients = listOf(
            "Protein, Vitamin B, Potassium, Fiber",
            "Vitamin D, Selenium, Copper, Pantothenic acid",
            "Riboflavin, Niacin, Selenium, Potassium",
            "Antioxidants, Beta-carotene, Vitamin C",
            "Iron, Zinc, Magnesium, Folate"
        )

        val randomSpecies = edibleSpecies.random()
        val randomGrade = "Grade ${grades.random()}"
        val randomHabitat = habitats.random()
        val randomNutrients = nutrients.random()

        // Update UI for edible mushroom
        binding.speciesTextView.text = randomSpecies
        binding.speciesTextView.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

        binding.typeTextView.text = getString(R.string.edible)
        binding.typeTextView.setTextColor(ContextCompat.getColor(this, R.color.primary_green))

        binding.gradeTextView.text = randomGrade
        binding.habitatTextView.text = randomHabitat
        binding.nutrientsTextView.text = randomNutrients

        // Show all fields for edible mushrooms
        binding.gradeLayout.visibility = View.VISIBLE
        binding.nutrientsLayout.visibility = View.VISIBLE

        // Use normal card background
        binding.infoCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.background_white))
    }
}