package com.example.mushroom_grader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.mushroom_grader.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayoutMediator
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // --- Carousel auto-scroll ---
    private val carouselHandler = Handler(Looper.getMainLooper())
    private val carouselDelayMs = 5000L

    private val carouselRunnable = object : Runnable {
        override fun run() {
            // Don’t auto-slide while user/VP is still settling (prevents “jittery” feel)
            if (binding.introViewPager.scrollState != ViewPager2.SCROLL_STATE_IDLE) {
                carouselHandler.removeCallbacks(this)
                carouselHandler.postDelayed(this, 250L)
                return
            }

            val adapter = binding.introViewPager.adapter ?: return
            if (adapter.itemCount <= 1) return

            val next = (binding.introViewPager.currentItem + 1) % adapter.itemCount
            binding.introViewPager.setCurrentItem(next, true)

            carouselHandler.removeCallbacks(this)
            carouselHandler.postDelayed(this, carouselDelayMs)
        }
    }

    private fun startAutoScroll() {
        carouselHandler.removeCallbacks(carouselRunnable)
        carouselHandler.postDelayed(carouselRunnable, carouselDelayMs)
    }

    private fun stopAutoScroll() {
        carouselHandler.removeCallbacks(carouselRunnable)
    }

    private fun dp(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    // --- Gallery picker ---
    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            startActivity(Intent(this, GalleryProcessActivity::class.java).apply {
                putExtra("imageUri", uri.toString())
            })
        }

    // --- Camera permission ---
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openCamera()
            else Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupIntroCarousel()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        startAutoScroll()
    }

    override fun onPause() {
        super.onPause()
        stopAutoScroll()
    }

    private fun setupIntroCarousel() {
        val pages = listOf(
            IntroPage(
                iconRes = R.drawable.hero_mushroom_1,
                title = "Scan with Camera",
                description = "Take a photo of a mushroom and let the app analyze it."
            ),
            IntroPage(
                iconRes = R.drawable.hero_mushroom_2,
                title = "Pick from Gallery",
                description = "Select an existing image to grade it quickly."
            ),
            IntroPage(
                iconRes = R.drawable.hero_mushroom_3,
                title = "View History",
                description = "Review your previous results anytime."
            )
        )

        binding.introViewPager.adapter = IntroPagerAdapter(pages)

        // Preload all 3 pages -> smoother auto slide (no first-time decode stutter)
        binding.introViewPager.offscreenPageLimit = pages.size

        // Custom fixed-size dots (force 8dp so it can't stretch into giant ovals)
        TabLayoutMediator(binding.introIndicator, binding.introViewPager) { tab, _ ->
            val dotView = layoutInflater.inflate(R.layout.item_dot, binding.introIndicator, false)

            val size = dp(8)
            val margin = dp(5)
            dotView.layoutParams = ViewGroup.MarginLayoutParams(size, size).apply {
                setMargins(margin, 0, margin, 0)
            }

            tab.customView = dotView
        }.attach()

        setupCarouselBehavior()
    }

    private fun setupCarouselBehavior() {
        // smoother swipe feel
        binding.introViewPager.setPageTransformer { page, position ->
            val a = 1f - abs(position).coerceIn(0f, 1f)
            page.alpha = 0.85f + (a * 0.15f)
            page.scaleX = 0.92f + (a * 0.08f)
            page.scaleY = 0.92f + (a * 0.08f)
        }

        // Chevrons
        binding.btnPrev.setOnClickListener {
            val count = binding.introViewPager.adapter?.itemCount ?: return@setOnClickListener
            val prev = (binding.introViewPager.currentItem - 1 + count) % count
            binding.introViewPager.setCurrentItem(prev, true)
            startAutoScroll()
        }

        binding.btnNext.setOnClickListener {
            val count = binding.introViewPager.adapter?.itemCount ?: return@setOnClickListener
            val next = (binding.introViewPager.currentItem + 1) % count
            binding.introViewPager.setCurrentItem(next, true)
            startAutoScroll()
        }

        // Pause autoplay while user drags; restart only when idle
        binding.introViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> stopAutoScroll()
                    ViewPager2.SCROLL_STATE_IDLE -> startAutoScroll()
                    ViewPager2.SCROLL_STATE_SETTLING -> { /* no-op */ }
                }
            }
        })
    }

    private fun setupClickListeners() {
        binding.btnCamera.setOnClickListener { checkPermissionsAndOpenCamera() }
        binding.btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.btnHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        binding.btnAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
    }

    private fun checkPermissionsAndOpenCamera() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) openCamera()
        else requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun openCamera() {
        startActivity(Intent(this, CameraActivity::class.java))
    }
}
