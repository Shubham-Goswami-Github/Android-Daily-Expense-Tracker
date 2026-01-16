package com.cscorner.financetracker;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private LottieAnimationView lottieAnimation;
    private TextView tvLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        // ✅ Agar user logged-in hai → DIRECT DASHBOARD (NO SPLASH)
        if (user != null) {
            Intent intent = new Intent(SplashActivity.this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        // ❌ Agar user logged-in nahi → SPLASH screen show karo
        setContentView(R.layout.activity_splash);

        // Initialize views
        lottieAnimation = findViewById(R.id.lottieAnimation);
        tvLoading = findViewById(R.id.tvLoading);

        // Start Lottie animation
        lottieAnimation.playAnimation();

        // Animate loading text
        animateLoadingText();

        // Navigate after delay
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, WelcomeActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 4000); // 4 seconds delay
    }

    private void animateLoadingText() {
        String[] loadingTexts = {"Loading...", "Setting up...", "Almost ready..."};
        Handler handler = new Handler(Looper.getMainLooper());

        for (int i = 0; i < loadingTexts.length; i++) {
            final int index = i;
            handler.postDelayed(() -> {
                if (tvLoading != null) {
                    // Fade out
                    AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
                    fadeOut.setDuration(200);
                    fadeOut.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {}

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            tvLoading.setText(loadingTexts[index]);
                            // Fade in
                            AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
                            fadeIn.setDuration(200);
                            tvLoading.startAnimation(fadeIn);
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {}
                    });
                    tvLoading.startAnimation(fadeOut);
                }
            }, i * 1200L); // Change text every 1.2 seconds
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lottieAnimation != null) {
            lottieAnimation.cancelAnimation();
        }
    }
}