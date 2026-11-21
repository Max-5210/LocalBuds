package com.localbuds.codec

import android.content.Context
import com.localbuds.codec.shizuku.ShizukuCodecAccess

/** The GitHub build's privileged codec route: Shizuku (B8, docs/research/play-readiness.md). */
internal object PrivilegedCodecRoute {
    fun create(context: Context): PrivilegedCodecAccess = ShizukuCodecAccess(context)
}
