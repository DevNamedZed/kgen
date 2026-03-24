package org.kgen.unmanaged.lib

import org.kgen.unmanaged.*

@KgenNative
object ExceptionFunctions {

    @KgenImport("_Unwind_RaiseException") external fun unwindRaiseException(exception: Long): Int
    @KgenImport external fun abort()

    @KgenExport("kgen_throw")
    @JvmStatic fun kgenThrow(exception: Long) {
        unwindRaiseException(exception)
        abort()
    }
}
