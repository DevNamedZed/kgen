package org.kgen.reflect.emit

import org.kgen.codegen.CodeGenOptions
import org.kgen.codegen.CodeGenerator
import org.kgen.ir.build.IrBuilder
import org.kgen.reflect.NativeCode

/**
 * Module builder that produces native binary output via IR and a [CodeGenerator].
 *
 * ```java
 * NativeModuleBuilder mod = ModuleBuilder.native_("myLib", x86Generator);
 * IrBuilder ir = mod.irBuilder();
 * var params = ir.createFunction("add", List.of(new Param("a", Type.I64), new Param("b", Type.I64)), Type.I64);
 * ir.appendBlock("entry");
 * var sum = ir.add(params.get(0), params.get(1));
 * ir.ret(sum);
 * ir.finalizeFunction();
 *
 * // Compile to executable memory and call immediately
 * NativeCode code = mod.compile();
 * long result = code.call("add", 3, 4);  // 7
 * code.close();
 *
 * // Or get raw machine code bytes
 * byte[] bytes = mod.toBytes();
 * ```
 */
class NativeModuleBuilder @JvmOverloads constructor(
    name: String,
    private val codeGenerator: CodeGenerator = hostCodeGenerator(),
    private val options: CodeGenOptions = CodeGenOptions(),
    target: org.kgen.ir.target.Target = hostTarget(),
) : ModuleBuilder(name) {

    private val irBuilder = IrBuilder(name, target)

    /** Access the IR builder for defining functions and types. */
    fun irBuilder(): IrBuilder = irBuilder

    /** Access the underlying IR module being built. */
    fun irModule(): org.kgen.ir.Module = irBuilder.build()

    /** Serialize to raw assembled machine code bytes (no linking, no object file headers). */
    override fun toBytes(): ByteArray {
        val module = irBuilder.build().let { m ->
            if (m.targetTriple != null) m else m.copy(targetTriple = hostTriple())
        }
        return codeGenerator.generateCode(module).textBytes
    }

    /**
     * Compile to executable memory and return callable [NativeCode].
     * Functions can be invoked immediately via [NativeCode.call].
     *
     * Automatically detects the host platform ABI so the generated code
     * uses the correct calling convention for FFM downcalls.
     */
    fun compile(): NativeCode {
        val module = irBuilder.build().let { m ->
            if (m.targetTriple != null) m else m.copy(targetTriple = hostTriple())
        }
        val compiled = codeGenerator.generateCode(module)
        val symbolMap = mutableMapOf<String, Long>()
        for (sym in compiled.symbols) {
            symbolMap[sym.name] = sym.offset
        }
        return NativeCode.loadBytes(compiled.textBytes, symbolMap)
    }

    companion object {
        private val osName = System.getProperty("os.name").lowercase()
        private val hostArch = System.getProperty("os.arch").lowercase()
        private val isWindows = osName.contains("win")

        internal fun hostTriple(): String {
            val archPart = when {
                hostArch.contains("amd64") || hostArch.contains("x86_64") -> "x86_64"
                hostArch.contains("aarch64") || hostArch.contains("arm64") -> "aarch64"
                else -> hostArch
            }
            val osPart = when {
                isWindows -> "windows"
                osName.contains("mac") || osName.contains("darwin") -> "macos"
                else -> "linux"
            }
            return "$archPart-$osPart"
        }

        @JvmStatic
        fun hostTarget(): org.kgen.ir.target.Target = when {
            hostArch.contains("amd64") || hostArch.contains("x86_64") ->
                org.kgen.ir.target.Target.x86_64()
            hostArch.contains("aarch64") || hostArch.contains("arm64") ->
                org.kgen.ir.target.Target.arm64()
            else -> org.kgen.ir.target.Target.x86_64()
        }

        @JvmStatic
        fun hostCodeGenerator(): CodeGenerator = when {
            hostArch.contains("amd64") || hostArch.contains("x86_64") ->
                org.kgen.target.x86.codegen.X86CodeGenerator()
            hostArch.contains("aarch64") || hostArch.contains("arm64") ->
                org.kgen.target.arm64.codegen.Arm64CodeGenerator()
            else -> org.kgen.target.x86.codegen.X86CodeGenerator()
        }
    }
}
