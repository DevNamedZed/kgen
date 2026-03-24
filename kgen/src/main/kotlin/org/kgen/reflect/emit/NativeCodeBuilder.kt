package org.kgen.reflect.emit

import org.kgen.binary.mangling.ManglingScheme
import org.kgen.binary.mangling.SymbolMangling
import org.kgen.codegen.CodeGenerator
import org.kgen.ir.Constant
import org.kgen.ir.Param
import org.kgen.ir.Type
import org.kgen.ir.build.ModuleBuilder
import org.kgen.ir.target.Target
import org.kgen.reflect.NativeCode
import org.kgen.reflect.Signature
import org.kgen.reflect.TypeRef

/**
 * Generates native code at runtime from reflected signatures without writing IR directly.
 *
 * Produces stubs, wrappers, delegates, and constant-returning functions that match
 * a given [Signature]. The output is compiled machine code that can be loaded and called
 * immediately.
 *
 * ```java
 * // Return a constant
 * var code = NativeCodeBuilder.forTarget(Target.x86_64())
 *     .function("getFortyTwo", Signature.returning(TypeRef.I64).build())
 *     .returnConstant(42L)
 *     .compile();
 * long result = code.call("getFortyTwo");  // 42
 *
 * // Delegate to another function by name
 * var code = NativeCodeBuilder.forTarget(Target.x86_64())
 *     .function("wrappedAdd", Signature.LONG_LONG_TO_LONG)
 *     .delegateTo("add")
 *     .compile();
 *
 * // Generate a stub that returns the default value (0)
 * var code = NativeCodeBuilder.forTarget(Target.x86_64())
 *     .stub("myStub", Signature.LONG_TO_LONG)
 *     .compile();
 * ```
 */
class NativeCodeBuilder private constructor(private val target: Target) {

    private val functions = mutableListOf<FunctionSpec>()
    private var manglingScheme: ManglingScheme? = null

    /**
     * Set a mangling scheme for generated symbols. When set, function names
     * are mangled according to the chosen convention before emission.
     *
     * ```java
     * var code = NativeCodeBuilder.forTarget(Target.x86_64())
     *     .withMangling(ManglingScheme.ITANIUM)
     *     .function("add", Signature.LONG_LONG_TO_LONG)
     *     .returnDefault()
     *     .compile();
     * // Symbol name will be "_Z3addxx" instead of "add"
     * ```
     */
    fun withMangling(scheme: ManglingScheme): NativeCodeBuilder {
        this.manglingScheme = scheme
        return this
    }

    /**
     * Begin defining a function with the given name and signature.
     * Chain a body method ([FunctionBuilder.returnConstant], [FunctionBuilder.delegateTo],
     * [FunctionBuilder.returnDefault]) to specify what the function does.
     */
    fun function(name: String, signature: Signature): FunctionBuilder {
        return FunctionBuilder(name, signature)
    }

    /**
     * Add a stub function that returns the default value for its return type (0 / 0.0 / void).
     */
    @JvmOverloads
    fun stub(name: String, signature: Signature = Signature.VOID): NativeCodeBuilder {
        functions.add(FunctionSpec.ReturnDefault(name, signature))
        return this
    }

    /**
     * Compile all defined functions and return loaded native code, ready to call.
     */
    fun compile(): NativeCode {
        require(functions.isNotEmpty()) { "No functions defined" }
        val compiled = buildCompiledCode()
        val symbolMap = mutableMapOf<String, Long>()
        for (sym in compiled.symbols) {
            symbolMap[sym.name] = sym.offset
        }
        return NativeCode.loadBytes(compiled.textBytes, symbolMap)
    }

    /**
     * Compile and return the raw machine code bytes (without loading into executable memory).
     */
    fun compileBytes(): ByteArray {
        require(functions.isNotEmpty()) { "No functions defined" }
        return buildCompiledCode().textBytes
    }

    private fun buildCompiledCode(): org.kgen.codegen.CompiledCode {
        val ir = ModuleBuilder(moduleName(), target)
        ir.targetTriple = NativeModuleBuilder.hostTriple()
        for (spec in functions) {
            emitFunction(ir, spec)
        }
        val module = ir.build()
        val codegen = findCodegen()
        return codegen.generateCode(module)
    }

    private fun moduleName(): String {
        return if (functions.size == 1) functions[0].name else "native_code"
    }

    private fun findCodegen(): CodeGenerator {
        return when (target.arch) {
            org.kgen.ir.target.Arch.X86_64 -> org.kgen.target.x86.codegen.X86CodeGenerator()
            org.kgen.ir.target.Arch.ARM64 -> org.kgen.target.arm64.codegen.Arm64CodeGenerator()
            org.kgen.ir.target.Arch.RISCV64 -> org.kgen.target.riscv.codegen.RiscVCodeGenerator()
            org.kgen.ir.target.Arch.WASM32, org.kgen.ir.target.Arch.WASM64 ->
                org.kgen.target.wasm.codegen.WasmCodeGenerator()
            org.kgen.ir.target.Arch.JVM -> org.kgen.target.jvm.codegen.JvmCodeGenerator()
            org.kgen.ir.target.Arch.MSIL, org.kgen.ir.target.Arch.MSIL_MIXED ->
                org.kgen.target.clr.codegen.CilCodeGenerator()
        }
    }

    private fun emitFunction(ir: ModuleBuilder, spec: FunctionSpec) {
        val irParams = spec.signature.parameters().mapIndexed { i, p ->
            Param(p.name ?: "p$i", toIrType(p.type))
        }
        val irReturnType = toIrType(spec.signature.returnType())
        val emitName = mangleIfNeeded(spec.name, irParams.map { it.type }, irReturnType)

        when (spec) {
            is FunctionSpec.ReturnDefault -> {
                ir.createFunction(emitName, irParams, irReturnType)
                ir.appendBlock("entry")
                emitReturnDefault(ir, irReturnType)
                ir.finalizeFunction()
            }

            is FunctionSpec.ReturnConst -> {
                ir.createFunction(emitName, irParams, irReturnType)
                ir.appendBlock("entry")
                val c = makeConstant(irReturnType, spec.value)
                ir.ret(c)
                ir.finalizeFunction()
            }

            is FunctionSpec.Delegate -> {
                val params = ir.createFunction(emitName, irParams, irReturnType)
                ir.appendBlock("entry")
                ir.declareFunction(spec.targetName, irParams, irReturnType)
                val args = params.map { it as org.kgen.ir.Value }
                if (irReturnType == Type.Void) {
                    ir.call(spec.targetName, args, irReturnType)
                    ir.ret()
                } else {
                    val result = ir.call(spec.targetName, args, irReturnType)
                    ir.ret(result)
                }
                ir.finalizeFunction()
            }

            is FunctionSpec.Identity -> {
                val params = ir.createFunction(emitName, irParams, irReturnType)
                ir.appendBlock("entry")
                ir.ret(params[spec.paramIndex])
                ir.finalizeFunction()
            }

            is FunctionSpec.Custom -> {
                val params = ir.createFunction(emitName, irParams, irReturnType)
                ir.appendBlock("entry")
                spec.body(ir, params)
                ir.finalizeFunction()
            }
        }
    }

    private fun mangleIfNeeded(name: String, paramTypes: List<Type>, returnType: Type): String {
        val scheme = manglingScheme ?: return name
        return SymbolMangling.mangleFunction(name, paramTypes, returnType, scheme)
    }

    private fun emitReturnDefault(ir: ModuleBuilder, returnType: Type) {
        when (returnType) {
            Type.Void -> ir.ret()
            Type.F32 -> ir.ret(Constant.F32(0.0f))
            Type.F64 -> ir.ret(Constant.F64(0.0))
            else -> ir.ret(Constant.I64(0))
        }
    }

    private fun makeConstant(type: Type, value: Long): Constant {
        return when (type) {
            Type.I1 -> Constant.I1(value != 0L)
            Type.I8 -> Constant.I8(value.toByte())
            Type.I16 -> Constant.I16(value.toShort())
            Type.I32 -> Constant.I32(value.toInt())
            Type.I64 -> Constant.I64(value)
            Type.F32 -> Constant.F32(Float.fromBits(value.toInt()))
            Type.F64 -> Constant.F64(Double.fromBits(value))
            else -> Constant.I64(value)
        }
    }

    inner class FunctionBuilder(
        private val name: String,
        private val signature: Signature,
    ) {
        /**
         * The function returns the given constant value.
         */
        fun returnConstant(value: Long): NativeCodeBuilder {
            functions.add(FunctionSpec.ReturnConst(name, signature, value))
            return this@NativeCodeBuilder
        }

        /**
         * The function returns zero or void (default for its return type).
         */
        fun returnDefault(): NativeCodeBuilder {
            functions.add(FunctionSpec.ReturnDefault(name, signature))
            return this@NativeCodeBuilder
        }

        /**
         * The function delegates to another function by name (declared as external).
         * Use this when linking will resolve the symbol at load time.
         */
        fun delegateTo(targetName: String): NativeCodeBuilder {
            functions.add(FunctionSpec.Delegate(name, signature, targetName))
            return this@NativeCodeBuilder
        }

        /**
         * The function returns one of its parameters unchanged.
         */
        @JvmOverloads
        fun returnParam(index: Int = 0): NativeCodeBuilder {
            require(index in 0 until signature.parameterCount()) {
                "Parameter index $index out of range [0, ${signature.parameterCount()})"
            }
            functions.add(FunctionSpec.Identity(name, signature, index))
            return this@NativeCodeBuilder
        }

        /**
         * Build the function body using ModuleBuilder directly.
         * The lambda receives the builder positioned at the entry block and the parameter list.
         */
        fun body(block: (ModuleBuilder, List<org.kgen.ir.Parameter>) -> Unit): NativeCodeBuilder {
            functions.add(FunctionSpec.Custom(name, signature, block))
            return this@NativeCodeBuilder
        }
    }

    private sealed class FunctionSpec(val name: String, val signature: Signature) {
        class ReturnDefault(name: String, sig: Signature) : FunctionSpec(name, sig)
        class ReturnConst(name: String, sig: Signature, val value: Long) : FunctionSpec(name, sig)
        class Delegate(name: String, sig: Signature, val targetName: String) : FunctionSpec(name, sig)
        class Identity(name: String, sig: Signature, val paramIndex: Int) : FunctionSpec(name, sig)
        class Custom(
            name: String, sig: Signature,
            val body: (ModuleBuilder, List<org.kgen.ir.Parameter>) -> Unit,
        ) : FunctionSpec(name, sig)
    }

    companion object {
        /** Create a builder for the host platform (auto-detected). */
        @JvmStatic
        fun create(): NativeCodeBuilder = NativeCodeBuilder(NativeModuleBuilder.hostTarget())

        /** Create a builder for a specific target architecture. */
        @JvmStatic
        fun forTarget(target: Target): NativeCodeBuilder = NativeCodeBuilder(target)

        /**
         * Maps a [TypeRef] (reflect type) to an IR [Type].
         *
         * Primitives map directly; pointers become opaque pointers; arrays become
         * opaque pointers (since native arrays are pointer-based). Named/unresolved
         * types fall back to i64 (pointer-sized integer).
         */
        @JvmStatic
        fun toIrType(ref: TypeRef): Type {
            if (ref.isVoid()) return Type.Void
            if (ref.isPointer()) return Type.OpaquePointer
            if (ref.isByRef()) return Type.OpaquePointer
            if (ref.isArray()) return Type.OpaquePointer

            return when (ref) {
                TypeRef.BOOL -> Type.I1
                TypeRef.I8, TypeRef.U8 -> Type.I8
                TypeRef.I16, TypeRef.U16 -> Type.I16
                TypeRef.I32, TypeRef.U32 -> Type.I32
                TypeRef.I64, TypeRef.U64 -> Type.I64
                TypeRef.F32 -> Type.F32
                TypeRef.F64 -> Type.F64
                TypeRef.POINTER -> Type.OpaquePointer
                else -> Type.I64
            }
        }
    }
}
