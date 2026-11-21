package com.localbuds.bt

import com.localbuds.protocol.qcultra2.BoseModel

/** Which stack drives a pair of headphones. LocalBuds is dedicated exclusively to Bose BMAP. */
enum class DeviceFamily {
    /** Bose BMAP: QC Ultra Earbuds, QC Ultra Headphones, QC Earbuds II, QC45 and other Bose models. */
    BOSE,
    ;

    companion object {
        private val BOSE_NAME = Regex(
            """\bbose\b|quietcomfort|\bqc(?:\s?\d|\s+(?:ultra|earbuds|headphones|se|sc)\b)""",
            RegexOption.IGNORE_CASE,
        )

        fun looksLikeBose(name: String?): Boolean = name != null && BOSE_NAME.containsMatchIn(name)

        fun forName(name: String?): DeviceFamily = BOSE

        fun fromKey(key: String?): DeviceFamily? = entries.firstOrNull { it.name.equals(key, ignoreCase = true) }

        fun earbudsByName(name: String?): Boolean? =
            BoseModel.fromName(name)?.let { it.formFactor != com.localbuds.protocol.qcultra2.ProductForm.HEADPHONES }

        fun resolve(override: String?, name: String?, learned: DeviceFamily? = null): DeviceFamily = BOSE
    }
}
