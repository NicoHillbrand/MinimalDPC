package com.nico.minidpc

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest

class MainActivity : Activity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName
    private lateinit var status: TextView
    private lateinit var passwordField: EditText

    private val privateDnsHost = "dcbec8.dns.nextdns.io"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        admin = ComponentName(this, AdminReceiver::class.java)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
        }

        status = TextView(this).apply { textSize = 16f }
        root.addView(status)

        root.addView(button("Apply lockdown (DNS + restrict)") { applyLockdown() })

        val pwLabel = TextView(this).apply {
            text = "\nUnlock password (required for actions below):"
            textSize = 14f
        }
        root.addView(pwLabel)
        passwordField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Password"
        }
        root.addView(passwordField)

        root.addView(button("Lift DNS restriction") { requirePassword { liftLockdown() } })
        root.addView(button("Clear device owner (allows uninstall)") { requirePassword { clearDeviceOwner() } })
        root.addView(button("Refresh status") { /* refresh only */ })

        setContentView(root)
        refresh()
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setOnClickListener { action(); refresh() }
        }

    private fun refresh() {
        val isDO = dpm.isDeviceOwnerApp(packageName)
        val um = getSystemService(USER_SERVICE) as UserManager
        val restricted = um.userRestrictions
            .getBoolean(UserManager.DISALLOW_CONFIG_PRIVATE_DNS, false)
        status.text = buildString {
            append("Package: $packageName\n")
            append("Device owner: $isDO\n")
            append("DNS restriction active: $restricted\n")
            append("Private DNS target: $privateDnsHost\n")
            append("\nTo open this UI later:\n")
            append("adb shell am start -n $packageName/.MainActivity\n")
        }
    }

    private fun applyLockdown() {
        if (!dpm.isDeviceOwnerApp(packageName)) {
            toast("Not device owner. Run:\nadb shell dpm set-device-owner $packageName/.AdminReceiver")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val r = dpm.setGlobalPrivateDnsModeSpecifiedHost(admin, privateDnsHost)
                toast("setGlobalPrivateDns result: $r")
            } catch (e: Exception) {
                toast("setGlobalPrivateDns failed: ${e.message}")
            }
        }
        try {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            toast("Lockdown applied")
        } catch (e: Exception) {
            toast("addUserRestriction failed: ${e.message}")
        }
    }

    private fun liftLockdown() {
        if (!dpm.isDeviceOwnerApp(packageName)) return
        try {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
            toast("DNS restriction lifted")
        } catch (e: Exception) {
            toast("clearUserRestriction failed: ${e.message}")
        }
    }

    private fun clearDeviceOwner() {
        if (!dpm.isDeviceOwnerApp(packageName)) return
        try {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS)
        } catch (_: Exception) {}
        try {
            dpm.clearDeviceOwnerApp(packageName)
            toast("Device owner cleared")
        } catch (e: Exception) {
            toast("clearDeviceOwnerApp failed: ${e.message}")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun requirePassword(action: () -> Unit) {
        val entered = passwordField.text.toString()
        if (entered.isEmpty()) {
            toast("Enter password first")
            return
        }
        if (sha256(entered) == PasswordHash.SHA256) {
            passwordField.setText("")
            action()
        } else {
            toast("Wrong password")
        }
    }

    private fun sha256(s: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
