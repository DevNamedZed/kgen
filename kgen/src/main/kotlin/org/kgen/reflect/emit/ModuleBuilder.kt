package org.kgen.reflect.emit

/**
 * Builds a complete module (assembly, class file, wasm module) for a specific target.
 *
 * Use factory methods to create target-specific builders, then define types,
 * methods, and fields. Each target provides its own instruction emitter.
 *
 * ```java
 * // CLR (.NET assembly)
 * ClrModuleBuilder mod = ModuleBuilder.clr("MyLib");
 * var type = mod.defineType("MyNamespace.MyClass");
 * var add = type.defineMethod("Add", sig, MethodFlag.PUBLIC, MethodFlag.STATIC);
 * add.il().ldarg(0);
 * add.il().ldarg(1);
 * add.il().add();
 * add.il().ret();
 * byte[] bytes = mod.toBytes();
 * ```
 */
abstract class ModuleBuilder(
    /** The module/assembly name. */
    val name: String,
) {
    /** Serialize the module to target-specific binary format. */
    abstract fun toBytes(): ByteArray

    companion object {
        /** Create a CLR/.NET module builder. */
        @JvmStatic
        fun clr(name: String): ClrModuleBuilder = ClrModuleBuilder(name)

        /** Create a JVM class file module builder. */
        @JvmStatic
        fun jvm(className: String): JvmModuleBuilder = JvmModuleBuilder(className)

        /** Create a WASM module builder. */
        @JvmStatic
        fun wasm(name: String): WasmModuleBuilder = WasmModuleBuilder(name)

        /** Create a native module builder for the host platform (auto-detected). */
        @JvmStatic
        fun native_(name: String): NativeModuleBuilder =
            NativeModuleBuilder(name, NativeModuleBuilder.hostCodeGenerator())

        /** Create a native module builder using a specific code generator. */
        @JvmStatic
        fun native_(name: String, codeGenerator: org.kgen.codegen.CodeGenerator): NativeModuleBuilder =
            NativeModuleBuilder(name, codeGenerator)
    }
}

