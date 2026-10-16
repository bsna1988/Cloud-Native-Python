package com.homebooks.android.util

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    private var rewardedAd: RewardedAd? = null
    private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917" // Test Rewarded Ad ID (~30s ad)

    fun initialize(context: Context) {
        MobileAds.initialize(context) {}
        loadAd(context)
    }

    fun loadAd(context: Context) {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                rewardedAd = null
            }
        })
    }

    fun showAd(activity: Activity, onAdFinished: () -> Unit) {
        val ad = rewardedAd
        if (ad != null) {
            ad.show(activity) { _ ->
                onAdFinished()
                loadAd(activity)
            }
        } else {
            onAdFinished()
            loadAd(activity)
        }
    }
}
