// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.gradleexample

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import io.bitdrift.capture.Capture
import io.bitdrift.gradleexample.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private external fun triggerSegfault()
    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var clipboardManager: ClipboardManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        clipboardManager = binding.root.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textviewFirst.text = Capture.Logger.sessionId
        binding.textviewFirst.setOnClickListener {
            val data = ClipData.newPlainText("sessionUrl", Capture.Logger.sessionUrl)
            clipboardManager.setPrimaryClip(data)
        }
        binding.buttonCrashJvm.setOnClickListener {
            throwException()
        }
        binding.buttonCrashNative.setOnClickListener {
            triggerSegfault()
        }
        binding.buttonFirst.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }

    private fun throwException() {
        throw RuntimeException("Nested Exception", IllegalStateException("Root Exception"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
