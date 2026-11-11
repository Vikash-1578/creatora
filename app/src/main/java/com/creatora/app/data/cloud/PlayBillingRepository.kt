package com.creatora.app.data.cloud

import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.creatora.app.domain.repository.BillingRepository
import com.creatora.app.domain.repository.BillingState
import com.creatora.app.domain.repository.LaunchOutcome
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Google Play Billing for the Pro subscription. The product [PRO_PRODUCT_ID] has to exist in Play Console; until it
 * does, [BillingState.available] stays false and the app says Pro is not available yet. Pro status is read from the
 * phone's Play purchases only; a server check is needed before launch (see the Phase A spec).
 */
@Singleton
class PlayBillingRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val bridge: ActivityBridge,
) : BillingRepository, PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val current = MutableStateFlow(BillingState())
    private var details: ProductDetails? = null

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    override val state: Flow<BillingState> = current

    override suspend fun refresh() {
        if (!connect()) {
            current.value = BillingState()
            return
        }
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        details = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build())
            .productDetailsList?.firstOrNull()
        val purchases = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        ).purchasesList.filter { PRO_PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
        // Unacknowledged purchases are refunded by Google after three days.
        purchases.filterNot { it.isAcknowledged }.forEach {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build())
        }
        current.value = BillingState(
            available = details != null,
            price = details?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice,
            hasPro = purchases.isNotEmpty(),
        )
    }

    override suspend fun launchUpgrade(): LaunchOutcome {
        val activity = bridge.activity ?: return LaunchOutcome.UNAVAILABLE
        val product = details ?: return LaunchOutcome.UNAVAILABLE
        val offer = product.subscriptionOfferDetails?.firstOrNull() ?: return LaunchOutcome.UNAVAILABLE
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(offer.offerToken)
                        .build(),
                ),
            )
            .build()
        return if (client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK) {
            LaunchOutcome.STARTED
        } else {
            LaunchOutcome.UNAVAILABLE
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        scope.launch { refresh() }
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() = Unit
            })
        }
    }

    companion object {
        /** The subscription's product id in Play Console. */
        const val PRO_PRODUCT_ID = "creatora_pro"
    }
}
