package com.find.phone.gps.location.tracker.repo.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.text.NumberFormat
import java.util.Currency
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object ProStoreBilling {

    private const val TAG = "ProStoreBilling"
    private const val PREFS = "premium"
    private const val KEY_ACTIVE = "active"

    var onPurchaseResult: ((PurchaseOutcome) -> Unit)? = null

    enum class PurchaseOutcome { PURCHASED, PENDING, CANCELED, FAILED }

    private var client: BillingClient? = null
    private val details = HashMap<String, ProductDetails>()

    fun isPremium(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

    private fun setPremium(context: Context, active: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, active).apply()
        Log.i(TAG, "premium=$active")
    }

    private fun client(context: Context): BillingClient {
        client?.let { return it }
        val app = context.applicationContext
        return BillingClient.newBuilder(app)
            .setListener { result, purchases -> onPurchasesUpdated(app, result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
            .also { client = it }
    }

    private val waiting = ArrayList<(BillingClient?) -> Unit>()
    private var connecting = false

    private fun connected(context: Context, block: (BillingClient?) -> Unit) {
        val c = client(context)
        if (c.isReady) return block(c)
        synchronized(this) {
            waiting += block
            if (connecting) return
            connecting = true
        }
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                if (!ok) Log.w(TAG, "setup failed: ${result.responseCode} ${result.debugMessage}")
                val ready = synchronized(this@ProStoreBilling) {
                    connecting = false
                    ArrayList(waiting).also { waiting.clear() }
                }
                ready.forEach { it(if (ok) c else null) }
            }

            override fun onBillingServiceDisconnected() {

                Log.w(TAG, "service disconnected")
            }
        })
    }

    fun sync(context: Context, onDone: (Boolean?) -> Unit = {}) {
        val app = context.applicationContext
        val ids = ProSettings.read(app).plans.map { it.productId }.toSet()
        connected(app) { c ->
            if (c == null) return@connected onDone(null)
            val owned = ArrayList<Purchase>()
            val pending = AtomicInteger(2)
            val failed = AtomicBoolean(false)
            val collect = { ok: Boolean, list: List<Purchase> ->
                synchronized(owned) { owned += list }
                if (!ok) failed.set(true)
                if (pending.decrementAndGet() == 0) {
                    if (failed.get()) {
                        Log.w(TAG, "sync incomplete — keeping premium=${isPremium(app)}")
                        onDone(null)
                    } else {
                        val active = owned.any { p ->
                            p.purchaseState == Purchase.PurchaseState.PURCHASED && p.products.any { it in ids }
                        }
                        owned.filter { !it.isAcknowledged && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                            .forEach { acknowledge(c, it) }
                        setPremium(app, active)
                        onDone(active)
                    }
                }
            }
            for (type in listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP)) {
                c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { result, list ->
                    val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                    if (!ok) Log.w(TAG, "queryPurchases($type): ${result.responseCode} ${result.debugMessage}")
                    collect(ok, list)
                }
            }
        }
    }

    fun fetchDetails(context: Context, plans: List<ProTier>, onLoaded: (Map<String, ProductDetails>) -> Unit) {
        connected(context) { c ->
            if (c == null) return@connected onLoaded(HashMap(details))
            val groups = plans.groupBy { if (it.isSubscription) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP }
            val pending = AtomicInteger(groups.size)
            for ((type, group) in groups) {
                val products = group.map {
                    QueryProductDetailsParams.Product.newBuilder().setProductId(it.productId).setProductType(type).build()
                }
                c.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(products).build()) { result, res ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        synchronized(details) { res.productDetailsList.forEach { details[it.productId] = it } }
                        val found = res.productDetailsList.map { it.productId }.toSet()
                        val missing = group.map { it.productId }.filter { it !in found }
                        if (missing.isNotEmpty()) Log.w(TAG, "productDetails($type): not found in Play: $missing")
                        else Log.i(TAG, "productDetails($type): loaded ${found.sorted()}")
                    } else {
                        Log.w(TAG, "productDetails($type): ${result.responseCode} ${result.debugMessage}")
                    }
                    if (pending.decrementAndGet() == 0) onLoaded(synchronized(details) { HashMap(details) })
                }
            }
        }
    }

    fun prefetchDetails(context: Context) {
        val app = context.applicationContext
        fetchDetails(app, ProSettings.read(app).plans) {}
    }

    private fun detailsOf(plan: ProTier): ProductDetails? = synchronized(details) { details[plan.productId] }

    fun price(plan: ProTier): String? {
        val d = detailsOf(plan) ?: return null
        return if (plan.isSubscription) {
            basePlan(d)?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
        } else {
            d.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    fun weeklyPrice(plan: ProTier): String? {
        if (plan.kind != ProTier.Kind.MONTHLY) return null
        val phase = detailsOf(plan)?.let { basePlan(it) }?.pricingPhases?.pricingPhaseList?.lastOrNull() ?: return null
        return runCatching {
            NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(phase.priceCurrencyCode) }
                .format(phase.priceAmountMicros / 4 / 1_000_000.0)
        }.getOrNull()
    }

    fun trialDays(plan: ProTier): Int? {
        if (!plan.isSubscription) return 0
        val d = detailsOf(plan) ?: return null
        val phase = trialOffer(d)?.pricingPhases?.pricingPhaseList?.firstOrNull { it.priceAmountMicros == 0L } ?: return 0
        return days(phase.billingPeriod) * phase.billingCycleCount.coerceAtLeast(1)
    }

    private fun trialOffer(d: ProductDetails): ProductDetails.SubscriptionOfferDetails? =
        d.subscriptionOfferDetails?.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } }

    private fun days(period: String): Int {
        val m = Regex("""P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?""").matchEntire(period) ?: return 0
        val (y, mo, w, d) = m.destructured
        return (y.toIntOrNull() ?: 0) * 365 + (mo.toIntOrNull() ?: 0) * 30 + (w.toIntOrNull() ?: 0) * 7 + (d.toIntOrNull() ?: 0)
    }

    fun purchase(activity: Activity, plan: ProTier): Boolean {
        val d = detailsOf(plan) ?: return false
        val params = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d)
        if (plan.isSubscription) {

            val token = (trialOffer(d) ?: basePlan(d))?.offerToken ?: return false
            params.setOfferToken(token)
        }
        val c = client?.takeIf { it.isReady } ?: return false
        val result = c.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params.build())).build())
        Log.i(TAG, "launchBillingFlow(${plan.productId}): ${result.responseCode} ${result.debugMessage}")
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun basePlan(d: ProductDetails): ProductDetails.SubscriptionOfferDetails? =
        d.subscriptionOfferDetails?.let { offers -> offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull() }

    private fun onPurchasesUpdated(context: Context, result: BillingResult, purchases: List<Purchase>?) {
        val outcome = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                list.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
                    .forEach { p -> client?.let { acknowledge(it, p) } }
                when {
                    list.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } -> {
                        setPremium(context, true)
                        PurchaseOutcome.PURCHASED
                    }
                    list.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> PurchaseOutcome.PENDING
                    else -> PurchaseOutcome.FAILED
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                setPremium(context, true)
                PurchaseOutcome.PURCHASED
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseOutcome.CANCELED
            else -> PurchaseOutcome.FAILED
        }
        Log.i(TAG, "purchase update: ${result.responseCode} → $outcome")
        onPurchaseResult?.invoke(outcome)
    }

    private fun acknowledge(c: BillingClient, purchase: Purchase) {
        c.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { r ->
            Log.i(TAG, "acknowledge ${purchase.products}: ${r.responseCode}")
        }
    }
}
