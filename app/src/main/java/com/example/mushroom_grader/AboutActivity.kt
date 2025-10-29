package com.example.mushroom_grader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.example.mushroom_grader.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    companion object {
        private const val GITHUB_URL = "https://github.com/yourusername/mushroom-grader"
        private const val EMAIL_ADDRESS = "mushroom.grader@wmsu.edu.ph"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.about_title)

        setupContent()
        setupClickListeners()
    }

    private fun setupContent() {
        // App version
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            binding.tvVersion.text = getString(R.string.version_format, packageInfo.versionName)
        } catch (e: Exception) {
            binding.tvVersion.text = getString(R.string.version_default)
        }

        // App description
        binding.tvDescription.text = getString(R.string.app_description)

        // Technical details
        binding.tvTechnicalDetails.text = getString(R.string.technical_details)

        // Credits
        binding.tvCredits.text = getString(R.string.app_credits)

        // Disclaimer
        binding.tvDisclaimer.text = getString(R.string.app_disclaimer)
    }

    private fun setupClickListeners() {
        // GitHub repository
        binding.btnGithub.setOnClickListener {
            openUrl(GITHUB_URL)
        }

        // Contact/Email
        binding.btnContact.setOnClickListener {
            sendEmail()
        }

        // Share app
        binding.btnShare.setOnClickListener {
            shareApp()
        }

        // Rate app
        binding.btnRate.setOnClickListener {
            rateApp()
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        } catch (e: Exception) {
            // Handle error - could show toast or dialog
        }
    }

    private fun sendEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = "mailto:$EMAIL_ADDRESS".toUri()
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject))
        }

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.send_email)))
        } catch (e: Exception) {
            // Handle error
        }
    }

    private fun shareApp() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text))
        }

        startActivity(Intent.createChooser(intent, getString(R.string.share_app)))
    }

    private fun rateApp() {
        val packageName = packageName
        try {
            val intent = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
            startActivity(intent)
        } catch (e: Exception) {
            // If Play Store not available, open in browser
            val intent = Intent(Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$packageName".toUri())
            startActivity(intent)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
