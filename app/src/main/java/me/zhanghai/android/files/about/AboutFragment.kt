/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 * Modified for mftlrs, 2026.
 */

package me.zhanghai.android.files.about

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import me.zhanghai.android.files.databinding.AboutFragmentBinding
import me.zhanghai.android.files.ui.view.LicensesDialogFragment
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.startActivitySafe

class AboutFragment : Fragment() {
    private lateinit var binding: AboutFragmentBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        AboutFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.gitHubLayout.setOnClickListener { startActivitySafe(GITHUB_URI.createViewIntent()) }
        binding.licensesLayout.setOnClickListener { LicensesDialogFragment.show(this) }
        // TODO(mftlrs): show it again with own PRIVACY_POLICY_URI when ACRA is added.
        //binding.privacyPolicyLayout.isVisible = true
        //binding.privacyPolicyLayout.setOnClickListener {
        //    startActivitySafe(PRIVACY_POLICY_URI.createViewIntent())
        //}
        binding.basedOnLayout.setOnClickListener {
            startActivitySafe(UPSTREAM_URI.createViewIntent())
        }
    }

    companion object {
        private val GITHUB_URI = Uri.parse("https://github.com/efimov-r/mftlrs")
        private val PRIVACY_POLICY_URI =
            Uri.parse("https://github.com/efimov-r/mftlrs/blob/master/PRIVACY.md")
        private val UPSTREAM_URI = Uri.parse("https://github.com/zhanghai/MaterialFiles")
    }
}
