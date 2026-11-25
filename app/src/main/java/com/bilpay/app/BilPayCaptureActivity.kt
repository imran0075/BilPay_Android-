package com.bilpay.app

import android.os.Bundle
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/**
 * BilPay QR scanner: portrait-only camera preview with the red laser line hidden.
 */
class BilPayCaptureActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<DecoratedBarcodeView>(com.google.zxing.client.android.R.id.zxing_barcode_scanner)
            ?.viewFinder
            ?.setLaserVisibility(false)
    }
}
