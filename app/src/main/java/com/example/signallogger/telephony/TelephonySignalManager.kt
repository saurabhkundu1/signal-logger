package com.example.signallogger.telephony

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.signallogger.data.SignalMetricDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TelephonySignalManager(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val _currentMetric = MutableStateFlow(buildCurrentMetric(-85, 3))
    val currentMetric: StateFlow<SignalMetricDto> = _currentMetric.asStateFlow()

    private var telephonyCallback: Any? = null

    fun startListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    val dbm = extractDbm(signalStrength)
                    val level = extractLevel(signalStrength)
                    _currentMetric.value = buildCurrentMetric(dbm, level)
                }
            }
            telephonyCallback = callback
            try {
                telephonyManager.registerTelephonyCallback(
                    context.mainExecutor,
                    callback
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                    if (signalStrength != null) {
                        val dbm = extractDbm(signalStrength)
                        val level = extractLevel(signalStrength)
                        _currentMetric.value = buildCurrentMetric(dbm, level)
                    }
                }
            }
            telephonyCallback = listener
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        }
    }

    fun stopListening() {
        telephonyCallback?.let { callback ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && callback is TelephonyCallback) {
                telephonyManager.unregisterTelephonyCallback(callback)
            } else if (callback is PhoneStateListener) {
                @Suppress("DEPRECATION")
                telephonyManager.listen(callback, PhoneStateListener.LISTEN_NONE)
            }
        }
        telephonyCallback = null
    }

    fun fetchCurrentMetricNow(): SignalMetricDto {
        return buildCurrentMetric(
            _currentMetric.value.signalStrengthDbm,
            _currentMetric.value.signalLevel
        )
    }

    private fun buildCurrentMetric(dbm: Int, level: Int): SignalMetricDto {
        return SignalMetricDto(
            operatorName = getOperatorNameInternal(),
            simOperatorName = getSimOperatorNameInternal(),
            countryIso = getCountryIsoInternal(),
            signalStrengthDbm = dbm,
            signalLevel = level,
            networkType = getNetworkTypeInternal(),
            isRoaming = telephonyManager.isNetworkRoaming,
            dataState = getDataStateInternal(),
            simState = getSimStateInternal(),
            timestamp = System.currentTimeMillis()
        )
    }

    private fun getOperatorNameInternal(): String {
        val networkOperatorName = telephonyManager.networkOperatorName
        val simOperatorName = telephonyManager.simOperatorName

        return when {
            !networkOperatorName.isNullOrEmpty() -> networkOperatorName
            !simOperatorName.isNullOrEmpty() -> simOperatorName
            else -> "Unknown Operator"
        }
    }

    private fun getSimOperatorNameInternal(): String {
        val simOperatorName = telephonyManager.simOperatorName
        return if (!simOperatorName.isNullOrEmpty()) simOperatorName else "N/A"
    }

    private fun getCountryIsoInternal(): String {
        val iso = telephonyManager.networkCountryIso
        return if (!iso.isNullOrEmpty()) iso.uppercase() else "US"
    }

    private fun getDataStateInternal(): String {
        return when (telephonyManager.dataState) {
            TelephonyManager.DATA_CONNECTED -> "Connected"
            TelephonyManager.DATA_CONNECTING -> "Connecting"
            TelephonyManager.DATA_SUSPENDED -> "Suspended"
            TelephonyManager.DATA_DISCONNECTED -> "Disconnected"
            else -> "Disconnected"
        }
    }

    private fun getSimStateInternal(): String {
        return when (telephonyManager.simState) {
            TelephonyManager.SIM_STATE_READY -> "Ready"
            TelephonyManager.SIM_STATE_ABSENT -> "Absent"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Required"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Required"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
            else -> "Unknown"
        }
    }

    private fun getNetworkTypeInternal(): String {
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return "Cellular"
        }

        return try {
            @Suppress("DEPRECATION")
            when (telephonyManager.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE / 4G"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> "Cellular"
            }
        } catch (e: SecurityException) {
            "Cellular"
        }
    }

    private fun extractDbm(signalStrength: SignalStrength): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cellSignals = signalStrength.cellSignalStrengths
            cellSignals.firstOrNull()?.let {
                return it.dbm
            }
        }
        return -85
    }

    private fun extractLevel(signalStrength: SignalStrength): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return signalStrength.level
        }
        return 3
    }
}
