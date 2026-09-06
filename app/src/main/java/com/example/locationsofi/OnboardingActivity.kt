package com.example.locationsofi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

data class OnboardingSlideItem(
    val icon: String,
    val title: String,
    val description: String
)

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: Button
    private lateinit var btnSkip: Button
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View

    private val slides = listOf(
        OnboardingSlideItem(
            "📍",
            "Live Location",
            "View your current GPS coordinates, accuracy and address in real time."
        ),
        OnboardingSlideItem(
            "🎛️",
            "Developer Test Mode",
            "Select custom coordinates for authorised app development and location testing."
        ),
        OnboardingSlideItem(
            "⭐",
            "Save and Share",
            "Save favourite places, view location history and share map links easily."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)
        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)

        viewPager.adapter = OnboardingAdapter(slides)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                if (position == slides.size - 1) {
                    btnNext.text = "Get Started 🚀"
                } else {
                    btnNext.text = "Next ➔"
                }
            }
        })

        btnNext.setOnClickListener {
            if (viewPager.currentItem < slides.size - 1) {
                viewPager.currentItem += 1
            } else {
                finishOnboarding()
            }
        }

        btnSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun updateDots(position: Int) {
        val activeWidth = (24 * resources.displayMetrics.density).toInt()
        val inactiveWidth = (8 * resources.displayMetrics.density).toInt()

        dot1.layoutParams = dot1.layoutParams.apply { width = if (position == 0) activeWidth else inactiveWidth }
        dot2.layoutParams = dot2.layoutParams.apply { width = if (position == 1) activeWidth else inactiveWidth }
        dot3.layoutParams = dot3.layoutParams.apply { width = if (position == 2) activeWidth else inactiveWidth }

        dot1.requestLayout()
        dot2.requestLayout()
        dot3.requestLayout()
    }

    private fun finishOnboarding() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("key_onboarding_completed", true).apply()

        val intent = if (ApiClient.isLoggedIn(this)) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    class OnboardingAdapter(private val items: List<OnboardingSlideItem>) :
        RecyclerView.Adapter<OnboardingAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val txtIcon: TextView = view.findViewById(R.id.txtIcon)
            val txtTitle: TextView = view.findViewById(R.id.txtTitle)
            val txtDescription: TextView = view.findViewById(R.id.txtDescription)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.txtIcon.text = item.icon
            holder.txtTitle.text = item.title
            holder.txtDescription.text = item.description
        }

        override fun getItemCount(): Int = items.size
    }
}
