package org.kgen.runtime.exec

/** Where an argument or return value is passed. */
sealed interface ArgLocation {
    data class Register(val registerIndex: Int) : ArgLocation
    data class FloatRegister(val registerIndex: Int) : ArgLocation
    data class Stack(val offset: Int) : ArgLocation
    data class Indirect(val registerIndex: Int) : ArgLocation
}
