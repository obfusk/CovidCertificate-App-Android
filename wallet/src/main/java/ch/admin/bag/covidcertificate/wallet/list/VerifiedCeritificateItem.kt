package ch.admin.bag.covidcertificate.wallet.list

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import ch.admin.bag.covidcertificate.common.verification.VerificationState
import ch.admin.bag.covidcertificate.eval.models.Bagdgc
import ch.admin.bag.covidcertificate.eval.models.CertType
import ch.admin.bag.covidcertificate.wallet.CertificatesViewModel
import ch.admin.bag.covidcertificate.wallet.R
import ch.admin.bag.covidcertificate.wallet.util.getQrAlpha
import ch.admin.bag.covidcertificate.wallet.util.getStatusIcon

data class VerifiedCeritificateItem(val verifiedCertificate: CertificatesViewModel.VerifiedCertificate) {

	fun bindView(itemView: View, onCertificateClickListener: ((Bagdgc) -> Unit)? = null) {
		val context = itemView.context
		val certificate = verifiedCertificate.certificate
		val state = verifiedCertificate.state

		val type = certificate.getType()
		var typeBackgroundColor = R.color.blue
		var typeTextColor = R.color.white
		var typeLabelRes = R.string.certificate_reason_vaccinated
		when (certificate.getType()) {
			CertType.RECOVERY -> {
				typeLabelRes = R.string.certificate_reason_recovered
			}
			CertType.TEST -> {
				typeLabelRes = R.string.certificate_reason_tested
				typeBackgroundColor = R.color.blueish
				typeTextColor = R.color.blue
			}
		}

		val isInvalid = state is VerificationState.INVALID
		if (isInvalid) {
			typeBackgroundColor = R.color.greyish
			typeTextColor = R.color.grey
		}

		val name = "${certificate.dgc.nam.fn} ${certificate.dgc.nam.gn}"
		val qrAlpha = state.getQrAlpha()
		itemView.findViewById<TextView>(R.id.item_certificate_list_name).apply {
			text = name
			alpha = qrAlpha
		}
		itemView.findViewById<View>(R.id.item_certificate_list_icon_qr).alpha = qrAlpha

		val isFinished = state != VerificationState.LOADING
		itemView.findViewById<View>(R.id.item_certificate_list_icon_loading_view).isVisible = !isFinished
		itemView.findViewById<ImageView>(R.id.item_certificate_list_icon_status).apply {
			isVisible = isFinished
			setImageResource(state.getStatusIcon())
		}
		itemView.findViewById<View>(R.id.item_certificate_list_icon_status_group).isVisible =
			state !is VerificationState.SUCCESS

		itemView.findViewById<TextView>(R.id.item_certificate_list_type).apply {
			backgroundTintList = context.resources.getColorStateList(typeBackgroundColor, context.theme)
			setTextColor(ContextCompat.getColor(context, typeTextColor))
			setText(typeLabelRes)
			isVisible = type != null
		}

		itemView.setOnClickListener {
			onCertificateClickListener?.invoke(certificate)
		}

	}
}