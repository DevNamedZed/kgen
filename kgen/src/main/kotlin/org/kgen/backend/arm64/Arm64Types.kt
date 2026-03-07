package org.kgen.backend.arm64

sealed interface Arm64Operand32
sealed interface Arm64Operand64

sealed interface Arm64Register32 : Arm64Operand32
sealed interface Arm64Register64 : Arm64Operand64

sealed interface Arm64VecS
sealed interface Arm64VecD
sealed interface Arm64VecQ
