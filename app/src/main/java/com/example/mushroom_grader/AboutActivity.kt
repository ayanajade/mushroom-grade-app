package com.example.mushroom_grader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.mushroom_grader.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    companion object {
        private const val GITHUB_URL = "https://github.com/yourusername/mushroom-grader"
        private const val EMAIL_ADDRESS = "mushroom.grader@wmsu.edu.ph"
        private const val APP_DOWNLOAD_URL = "https://www.mediafire.com/file/24g0fa6slm2mdms/MushroomGrader-debug.apk/file"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "About"

        setupVersion()
        setupClickListeners()
    }

    private fun setupVersion() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            binding.tvVersion.text = "Version ${packageInfo.versionName}"
        } catch (_: Exception) {
            binding.tvVersion.text = "Version 1.0.0"
        }
    }

    private fun setupClickListeners() {
        // GitHub button
        binding.btnGithub.setOnClickListener {
            openUrl(GITHUB_URL)
        }

        // Email button
        binding.btnEmail.setOnClickListener {
            sendEmail()
        }

        // Share App button (NEW!)
        binding.btnShare.setOnClickListener {
            shareApp()
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Cannot open URL",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun sendEmail() {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL_ADDRESS))
                putExtra(Intent.EXTRA_SUBJECT, "Mushroom Grader Feedback")
            }
            startActivity(Intent.createChooser(intent, "Send Email"))
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Cannot send email",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ✅ NEW: Share App Function
    private fun shareApp() {
        try {
            val shareText = buildString {
                append("🍄 Check out Mushroom Grader App!\n\n")
                append("An AI-powered mobile application to classify mushroom species ")
                append("using advanced machine learning technology.\n\n")
                append("📥 Download here:\n")
                append(APP_DOWNLOAD_URL)
                append("\n\n")
                append("Features:\n")
                append("• Real-time mushroom classification\n")
                append("• Detailed species information\n")
                append("• Safety warnings for poisonous species\n")
                append("• Classification history\n")
                append("• Camera and gallery support\n")
                append("• Offline processing")
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, "Mushroom Grader - AI Mushroom Classification App")
            }

            startActivity(Intent.createChooser(shareIntent, "Share Mushroom Grader App"))
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Cannot share app",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
