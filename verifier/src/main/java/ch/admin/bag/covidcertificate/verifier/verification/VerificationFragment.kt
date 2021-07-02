/*
 * Copyright (c) 2021 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package ch.admin.bag.covidcertificate.verifier.verification

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import ch.admin.bag.covidcertificate.common.util.*
import ch.admin.bag.covidcertificate.common.views.VerticalMarginItemDecoration
import ch.admin.bag.covidcertificate.sdk.android.extensions.DEFAULT_DISPLAY_DATE_FORMATTER
import ch.admin.bag.covidcertificate.sdk.android.extensions.prettyPrintIsoDateTime
import ch.admin.bag.covidcertificate.sdk.core.models.healthcert.DccHolder
import ch.admin.bag.covidcertificate.sdk.core.models.state.VerificationState
import ch.admin.bag.covidcertificate.verifier.R
import ch.admin.bag.covidcertificate.verifier.databinding.FragmentVerificationBinding
import kotlin.math.max
import kotlin.math.roundToInt

class VerificationFragment : Fragment() {

	companion object {
		private val TAG = VerificationFragment::class.java.canonicalName
		private const val ARG_DECODE_DGC = "ARG_DECODE_DGC"

		fun newInstance(dccHolder: DccHolder): VerificationFragment {
			return VerificationFragment().apply {
				arguments = Bundle().apply {
					putSerializable(ARG_DECODE_DGC, dccHolder)
				}
			}
		}
	}

	private var _binding: FragmentVerificationBinding? = null
	private val binding get() = _binding!!
	private val verificationViewModel: VerificationViewModel by viewModels()
	private var dccHolder: DccHolder? = null
	private var isClosedByUser = false

	private lateinit var verificationAdapter: VerificationAdapter

	private val onBackPressedCallback = object : OnBackPressedCallback(true) {
		override fun handleOnBackPressed() {
			isClosedByUser = true
			parentFragmentManager.popBackStack()
			remove()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if (arguments?.containsKey(ARG_DECODE_DGC) == false) {
			return
		}

		dccHolder = requireArguments().getSerializable(ARG_DECODE_DGC) as DccHolder

		requireActivity().onBackPressedDispatcher.addCallback(onBackPressedCallback)
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = FragmentVerificationBinding.inflate(inflater, container, false)
		return binding.root
	}

	@SuppressLint("SetTextI18n")
	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		val dccHolder = dccHolder ?: return
		val euDgc = dccHolder.euDGC ?: return

		binding.verificationFamilyName.text = euDgc.person.familyName
		binding.verificationGivenName.text = euDgc.person.givenName
		binding.verificationBirthdate.text = euDgc.dateOfBirth.prettyPrintIsoDateTime(DEFAULT_DISPLAY_DATE_FORMATTER)
		binding.verificationStandardizedNameLabel.text =
			"${euDgc.person.standardizedFamilyName}<<${euDgc.person.standardizedGivenName}"

		binding.verificationFooterButton.setOnClickListener {
			isClosedByUser = true
			onBackPressedCallback.remove()
			parentFragmentManager.popBackStack()
		}

		view.doOnLayout { setupScrollBehavior() }

		verificationViewModel.startVerification(dccHolder)

		verificationViewModel.verificationLiveData.observe(viewLifecycleOwner, {
			updateHeaderAndVerificationView(it)
		})

		verificationAdapter = VerificationAdapter {
			verificationViewModel.retryVerification(dccHolder)
		}

		binding.verificationStatusRecyclerView.apply {
			layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
			adapter = verificationAdapter
			addItemDecoration(VerticalMarginItemDecoration(context, R.dimen.spacing_very_small))
		}
	}

	override fun onPause() {
		super.onPause()
		// Pop the entire backstack back to the home screen when the verification fragment is put into the background, unless
		// it was closed by the user (e.g. with the back or OK button)
		if (!isClosedByUser) {
			onBackPressedCallback.remove()
			parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	private fun updateHeaderAndVerificationView(verificationState: VerificationState) {
		updateHeader(verificationState)
		updateStatusBubbles(verificationState)
	}

	private fun updateHeader(state: VerificationState) {
		val context = binding.root.context

		val isLoading = state == VerificationState.LOADING

		binding.verificationHeaderProgressBar.isVisible = isLoading
		binding.verificationHeaderIcon.isVisible = !isLoading
		binding.verificationHeaderIcon.setImageResource(state.getValidationStatusIconLarge())
		ColorStateList.valueOf(ContextCompat.getColor(context, state.getHeaderColor())).let { headerBackgroundTint ->
			binding.verificationBaseGroup.backgroundTintList = headerBackgroundTint
			binding.verificationContentGroup.backgroundTintList = headerBackgroundTint
			binding.verificationHeaderGroup.backgroundTintList = headerBackgroundTint
		}
	}

	private fun updateStatusBubbles(state: VerificationState) {
		val context = binding.root.context

		verificationAdapter.setItems(state.getVerificationStateItems(context))

		binding.verificationErrorCode.apply {
			visibility = View.INVISIBLE
			if (state is VerificationState.ERROR) {
				visibility = View.VISIBLE
				text = state.error.code
			} else if (state is VerificationState.INVALID) {
				val errorCode = state.getInvalidErrorCode()
				if (errorCode.isNotEmpty()) {
					visibility = View.VISIBLE
					text = errorCode
				}
			}
		}
	}

	private fun setupScrollBehavior() {
		val headerCollapseManager = HeaderCollapseManager(resources, binding)
		binding.verificationScrollView.setOnScrollChangeListener(headerCollapseManager)
	}

	internal class HeaderCollapseManager(resources: Resources, binding: FragmentVerificationBinding) : View.OnScrollChangeListener {

		private val root = binding.verificationBaseGroup
		private val headerGroup = binding.verificationHeaderGroup
		private val headerShadow = binding.verificationHeaderGroupShadow
		private val sheetGroup = binding.verificationSheetGroup

		private val scrollOffset = (sheetGroup.layoutParams as ViewGroup.MarginLayoutParams).topMargin
		private val minHeaderHeight = resources.getDimensionPixelSize(R.dimen.header_height_default)
		private val maxHeaderHeight = resources.getDimensionPixelSize(R.dimen.header_height_max)
		private val diffMaxMinHeaderHeight = maxHeaderHeight - minHeaderHeight
		private val headerShadowAnimRange = resources.getDimensionPixelSize(R.dimen.spacing_medium)
		private val sheetDefaultCornerRadius = resources.getDimensionPixelSize(R.dimen.corner_radius_sheet)

		override fun onScrollChange(v: View, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
			val scrollVertical = max(scrollY - scrollOffset, 0)

			val progressPadding = (scrollVertical / minHeaderHeight.toFloat()).coerceIn(0f, 1f)
			headerGroup.setPadding(0, ((1f - progressPadding) * minHeaderHeight).roundToInt(), 0, 0)

			val progressSize = (scrollVertical / diffMaxMinHeaderHeight.toFloat()).coerceIn(0f, 1f)
			val lp: ViewGroup.LayoutParams = headerGroup.layoutParams
			lp.height = (maxHeaderHeight - progressSize * diffMaxMinHeaderHeight).roundToInt()
			headerGroup.layoutParams = lp

			val progressHeaderShadow =
				((scrollVertical - diffMaxMinHeaderHeight) / headerShadowAnimRange.toFloat()).coerceIn(0f, 1f)
			headerShadow.alpha = progressHeaderShadow

			val progressSheetCorner = ((scrollVertical - minHeaderHeight) / scrollOffset.toFloat()).coerceIn(0f, 1f)
			val sheetCornerRadius = (1f - progressSheetCorner) * sheetDefaultCornerRadius
			(sheetGroup.background as? GradientDrawable)?.cornerRadii =
				floatArrayOf(sheetCornerRadius, sheetCornerRadius, sheetCornerRadius, sheetCornerRadius, 0f, 0f, 0f, 0f)

			root.requestLayout()
		}
	}


}