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
import io.bitdrift.capture.network.okhttp.CaptureOkHttpEventListenerFactory
import io.bitdrift.capture.network.retrofit.RetrofitUrlPathProvider
import io.bitdrift.gradleexample.databinding.FragmentFirstBinding
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private external fun triggerSegfault()
    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private lateinit var clipboardManager: ClipboardManager

    private lateinit var client: OkHttpClient

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        clipboardManager = binding.root.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        client = OkHttpClient.Builder()
            .eventListenerFactory(CaptureOkHttpEventListenerFactory(requestFieldProvider = RetrofitUrlPathProvider()))
            .build()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textviewFirst.text = Capture.Logger.sessionId
        binding.textviewFirst.setOnClickListener {
            val data = ClipData.newPlainText("sessionUrl", Capture.Logger.sessionUrl)
            clipboardManager.setPrimaryClip(data)
        }
        binding.buttonOkhttp.setOnClickListener {
            makeOkHttpCall()
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

    private fun makeOkHttpCall() {
        val req = Request.Builder()
            .url(
                HttpUrl.Builder()
                    .scheme("https")
                    .host("httpbin.org")
                    .build()
            )
            .method("GET", null)
            .build()

        val call = client.newCall(req)

        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                Timber.v("Http request completed with status code=${response.code}, body=${response.body?.string()}")
            }

            override fun onFailure(call: Call, e: IOException) {
                Timber.w(e, "Http request failed with IOException")
            }
        })
    }

    private fun throwException() {
        throw RuntimeException("Nested Exception", IllegalStateException("Root Exception"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
