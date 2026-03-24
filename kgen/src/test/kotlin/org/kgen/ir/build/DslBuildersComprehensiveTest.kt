package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.target.Target
import org.kgen.ir.types.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DslBuildersComprehensiveTest {

    @Test
    fun `module with default target`() {
        val mod = module("test") {}
        assertEquals("test", mod.name)
    }

    @Test
    fun `module with explicit wasm target`() {
        val mod = module("test", Target.wasm()) {}
        assertEquals("test", mod.name)
    }

    @Test
    fun `module with x86 target`() {
        val mod = module("test", Target.x86_64()) {}
        assertEquals("test", mod.name)
    }

    @Test
    fun `module with target triple`() {
        val mod = module("test") {
            targetTriple("x86_64-unknown-linux-gnu")
        }
        assertEquals("x86_64-unknown-linux-gnu", mod.targetTriple)
    }

    @Test
    fun `module with data layout`() {
        val mod = module("test") {
            dataLayout("e-m:e-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128")
        }
        assertEquals("e-m:e-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128", mod.dataLayout)
    }

    @Test
    fun `module with source file`() {
        val mod = module("test") {
            sourceFile("main.c")
        }
        assertEquals("main.c", mod.sourceFile)
    }

    @Test
    fun `module with target feature`() {
        val mod = module("test") {
            targetFeature("+sse4.2")
        }
        assertTrue(mod.targetFeatures.contains("+sse4.2"))
    }

    @Test
    fun `module with multiple target features`() {
        val mod = module("test") {
            targetFeature("+sse4.2")
            targetFeature("+avx2")
        }
        assertEquals(2, mod.targetFeatures.size)
    }

    @Test
    fun `module with inline asm`() {
        val mod = module("test") {
            moduleInlineAsm(".section .text")
        }
        assertEquals(".section .text", mod.moduleInlineAsm)
    }

    @Test
    fun `module with global ctor`() {
        val mod = module("test") {
            globalCtor("init_func")
        }
        assertEquals(1, mod.globalCtors.size)
        assertEquals("init_func", mod.globalCtors[0].function)
        assertEquals(65535, mod.globalCtors[0].priority)
    }

    @Test
    fun `module with global ctor custom priority`() {
        val mod = module("test") {
            globalCtor("early_init", 100)
        }
        assertEquals(100, mod.globalCtors[0].priority)
    }

    @Test
    fun `module with global dtor`() {
        val mod = module("test") {
            globalDtor("cleanup_func")
        }
        assertEquals(1, mod.globalDtors.size)
        assertEquals("cleanup_func", mod.globalDtors[0].function)
    }

    @Test
    fun `module with global dtor custom priority`() {
        val mod = module("test") {
            globalDtor("late_cleanup", 200)
        }
        assertEquals(200, mod.globalDtors[0].priority)
    }

    @Test
    fun `module with ifunc`() {
        val mod = module("test") {
            ifunc("my_func", "resolver_func", Type.Function(emptyList(), Type.I32))
        }
        assertEquals(1, mod.ifuncs.size)
        assertEquals("my_func", mod.ifuncs[0].name)
    }

    @Test
    fun `module with comdat`() {
        val mod = module("test") {
            comdat("group1", ComdatSelectionKind.ANY)
        }
        assertEquals(1, mod.comdats.size)
        assertEquals("group1", mod.comdats[0].name)
        assertEquals(ComdatSelectionKind.ANY, mod.comdats[0].selectionKind)
    }

    @Test
    fun `module with module flag`() {
        val mod = module("test") {
            moduleFlag("PIC Level", ModuleFlagValue.IntFlag(2))
        }
        assertEquals(ModuleFlagValue.IntFlag(2), mod.moduleFlags["PIC Level"])
    }

    @Test
    fun `module with string module flag`() {
        val mod = module("test") {
            moduleFlag("SDK Version", ModuleFlagValue.StringFlag("14.0"))
        }
        assertEquals(ModuleFlagValue.StringFlag("14.0"), mod.moduleFlags["SDK Version"])
    }

    @Test
    fun `module with empty function`() {
        val mod = module("test") {
            function("main", emptyList(), Type.I32) {
                block("entry") {
                    ret(i32(0))
                }
            }
        }
        assertEquals(1, mod.functions.size)
        assertEquals("main", mod.functions[0].name)
    }

    @Test
    fun `module function has correct return type`() {
        val mod = module("test") {
            function("f", emptyList(), Type.F64) {
                block("entry") {
                    ret(f64(3.14))
                }
            }
        }
        assertEquals(Type.F64, mod.functions[0].returnType)
    }

    @Test
    fun `module function with parameters`() {
        val mod = module("test") {
            function("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val sum = add(param(0), param(1))
                    ret(sum)
                }
            }
        }
        assertEquals(2, mod.functions[0].params.size)
        assertEquals("a", mod.functions[0].params[0].name)
        assertEquals("b", mod.functions[0].params[1].name)
    }

    @Test
    fun `module function paramCount`() {
        val mod = module("test") {
            function("f", listOf(Param("x", Type.I32), Param("y", Type.I32), Param("z", Type.I32)), Type.Void) {
                assertEquals(3, paramCount)
                block("entry") { ret() }
            }
        }
        assertEquals(3, mod.functions[0].params.size)
    }

    @Test
    fun `module external function`() {
        val mod = module("test") {
            function("puts", listOf(Param("s", Type.OpaquePointer)), Type.I32, isExternal = true)
        }
        assertTrue(mod.functions[0].isExternal)
        assertTrue(mod.functions[0].blocks.isEmpty())
    }

    @Test
    fun `module function with linkage`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void, linkage = Linkage.INTERNAL) {
                block("entry") { ret() }
            }
        }
        assertEquals(Linkage.INTERNAL, mod.functions[0].linkage)
    }

    @Test
    fun `module function with visibility`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void, visibility = Visibility.HIDDEN) {
                block("entry") { ret() }
            }
        }
        assertEquals(Visibility.HIDDEN, mod.functions[0].visibility)
    }

    @Test
    fun `module function with calling convention`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void, callingConv = CallingConvention.FAST) {
                block("entry") { ret() }
            }
        }
        assertEquals(CallingConvention.FAST, mod.functions[0].callingConv)
    }

    @Test
    fun `module function with attributes`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void, attributes = setOf(FnAttribute.NOUNWIND, FnAttribute.READONLY)) {
                block("entry") { ret() }
            }
        }
        assertTrue(mod.functions[0].attributes.contains(FnAttribute.NOUNWIND))
        assertTrue(mod.functions[0].attributes.contains(FnAttribute.READONLY))
    }

    @Test
    fun `module function with vararg`() {
        val mod = module("test") {
            function("printf", listOf(Param("fmt", Type.OpaquePointer)), Type.I32, isVarArg = true) {
                block("entry") { ret(i32(0)) }
            }
        }
        assertTrue(mod.functions[0].isVarArg)
    }

    @Test
    fun `module function with section`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void, section = ".text.hot") {
                block("entry") { ret() }
            }
        }
        assertEquals(".text.hot", mod.functions[0].section)
    }

    @Test
    fun `module function with alignment`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void, align = 16) {
                block("entry") { ret() }
            }
        }
        assertEquals(16, mod.functions[0].align)
    }

    @Test
    fun `module function with gc`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void, gc = "shadow-stack") {
                block("entry") { ret() }
            }
        }
        assertEquals("shadow-stack", mod.functions[0].gc)
    }

    @Test
    fun `module function with personality`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void,
                personality = FunctionRef("__gxx_personality_v0", Type.Function(emptyList(), Type.I32))
            ) {
                block("entry") { ret() }
            }
        }
        assertNotNull(mod.functions[0].personality)
        assertEquals("__gxx_personality_v0", mod.functions[0].personality!!.name)
    }

    @Test
    fun `module with multiple functions`() {
        val mod = module("test") {
            function("f1", emptyList(), Type.Void) {
                block("entry") { ret() }
            }
            function("f2", emptyList(), Type.I32) {
                block("entry") { ret(i32(42)) }
            }
        }
        assertEquals(2, mod.functions.size)
    }

    @Test
    fun `module with global variable`() {
        val mod = module("test") {
            global("counter", Type.I32, Constant.I32(0))
        }
        assertEquals(1, mod.globals.size)
        assertEquals("counter", mod.globals[0].name)
        assertEquals(Type.I32, mod.globals[0].type)
    }

    @Test
    fun `module with constant global`() {
        val mod = module("test") {
            global("PI", Type.F64, Constant.F64(3.14159), isConstant = true)
        }
        assertTrue(mod.globals[0].isConstant)
    }

    @Test
    fun `module global with linkage`() {
        val mod = module("test") {
            global("g", Type.I32, linkage = Linkage.INTERNAL)
        }
        assertEquals(Linkage.INTERNAL, mod.globals[0].linkage)
    }

    @Test
    fun `module global with visibility`() {
        val mod = module("test") {
            global("g", Type.I32, visibility = Visibility.HIDDEN)
        }
        assertEquals(Visibility.HIDDEN, mod.globals[0].visibility)
    }

    @Test
    fun `module global with thread local`() {
        val mod = module("test") {
            global("tls_var", Type.I32, threadLocal = ThreadLocalMode.GENERAL_DYNAMIC)
        }
        assertEquals(ThreadLocalMode.GENERAL_DYNAMIC, mod.globals[0].threadLocal)
    }

    @Test
    fun `module global with section`() {
        val mod = module("test") {
            global("g", Type.I32, section = ".bss")
        }
        assertEquals(".bss", mod.globals[0].section)
    }

    @Test
    fun `module global with alignment`() {
        val mod = module("test") {
            global("g", Type.I64, align = 8)
        }
        assertEquals(8, mod.globals[0].align)
    }

    @Test
    fun `module global with address space`() {
        val mod = module("test") {
            global("g", Type.I32, addressSpace = 1)
        }
        assertEquals(1, mod.globals[0].addressSpace)
    }

    @Test
    fun `module with struct definition`() {
        val mod = module("test") {
            struct("Point", listOf(Param("x", Type.F64), Param("y", Type.F64)))
        }
        assertEquals(1, mod.structs.size)
        assertEquals("Point", mod.structs[0].name)
        assertEquals(2, mod.structs[0].fields.size)
    }

    @Test
    fun `module with packed struct`() {
        val mod = module("test") {
            struct("Packed", listOf(Param("a", Type.I8), Param("b", Type.I32)), packed = true)
        }
        assertTrue(mod.structs[0].packed)
    }

    @Test
    fun `module with aligned struct`() {
        val mod = module("test") {
            struct("Aligned", listOf(Param("x", Type.I32)), align = 16)
        }
        assertEquals(16, mod.structs[0].align)
    }

    @Test
    fun `module with class definition`() {
        val cls = ClassDefinition("Counter", fields = listOf(FieldDefinition("count", Type.I32)))
        val mod = module("test") {
            classDef(cls)
        }
        assertEquals(1, mod.classes.size)
        assertEquals("Counter", mod.classes[0].name)
    }

    @Test
    fun `module with interface definition`() {
        val iface = InterfaceDefinition("Comparable", methods = listOf(
            MethodDefinition("compareTo", listOf(Param("other", Type.ClassRef("Object"))), Type.I32)
        ))
        val mod = module("test") {
            interfaceDef(iface)
        }
        assertEquals(1, mod.interfaces.size)
        assertEquals("Comparable", mod.interfaces[0].name)
    }

    @Test
    fun `module with enum definition`() {
        val enumDef = EnumDefinition("Color", listOf(
            EnumVariant("RED", 0),
            EnumVariant("GREEN", 1),
            EnumVariant("BLUE", 2),
        ))
        val mod = module("test") {
            enumDef(enumDef)
        }
        assertEquals(1, mod.enums.size)
        assertEquals("Color", mod.enums[0].name)
    }

    @Test
    fun `module with type alias`() {
        val mod = module("test") {
            typeAlias("Size", Type.I64)
        }
        assertEquals(1, mod.aliases.size)
        assertEquals("Size", mod.aliases[0].name)
        assertEquals(Type.I64, mod.aliases[0].type)
    }

    @Test
    fun `module with metadata`() {
        val mod = module("test") {
            metadata("dbg.version", MetadataValue.IntMD(3))
        }
        assertEquals(MetadataValue.IntMD(3), mod.metadata["dbg.version"])
    }

    @Test
    fun `module with string metadata`() {
        val mod = module("test") {
            metadata("producer", MetadataValue.StringMD("kgen 1.0"))
        }
        assertEquals(MetadataValue.StringMD("kgen 1.0"), mod.metadata["producer"])
    }

    @Test
    fun `function builder creates blocks`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") { ret() }
                block("unreachable") { unreachable() }
            }
        }
        assertEquals(2, mod.functions[0].blocks.size)
        assertEquals("entry", mod.functions[0].blocks[0].label)
        assertEquals("unreachable", mod.functions[0].blocks[1].label)
    }

    @Test
    fun `function builder block chaining`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    br(BlockRef("exit"))
                }
                block("exit") {
                    ret()
                }
            }
        }
        val br = mod.functions[0].blocks[0].instructions.last() as Br
        assertEquals(BlockRef("exit"), br.target)
    }

    @Test
    fun `block builder emits arithmetic`() {
        val mod = module("test") {
            function("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val sum = add(param(0), param(1))
                    ret(sum)
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertEquals(2, instrs.size)
        assertTrue(instrs[0] is Add)
        assertTrue(instrs[1] is Ret)
    }

    @Test
    fun `block builder emits control flow`() {
        val mod = module("test") {
            function("f", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val cmp = icmp(ICmpPredicate.SGT, param(0), i32(0))
                    condBr(cmp, BlockRef("positive"), BlockRef("negative"))
                }
                block("positive") {
                    ret(param(0))
                }
                block("negative") {
                    val neg = neg(param(0))
                    ret(neg)
                }
            }
        }
        assertEquals(3, mod.functions[0].blocks.size)
    }

    @Test
    fun `block builder emits memory operations`() {
        val mod = module("test") {
            function("f", emptyList(), Type.I32) {
                block("entry") {
                    val ptr = alloca(Type.I32)
                    store(i32(42), ptr)
                    val loaded = load(Type.I32, ptr)
                    ret(loaded)
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is Alloca)
        assertTrue(instrs[1] is Store)
        assertTrue(instrs[2] is Load)
        assertTrue(instrs[3] is Ret)
    }

    @Test
    fun `block builder emits conversions`() {
        val mod = module("test") {
            function("f", listOf(Param("x", Type.I32)), Type.I64) {
                block("entry") {
                    val extended = sext(param(0), Type.I64)
                    ret(extended)
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is SExt)
    }

    @Test
    fun `block builder emits phi`() {
        val mod = module("test") {
            function("f", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val cmp = icmp(ICmpPredicate.SGT, param(0), i32(0))
                    condBr(cmp, BlockRef("then"), BlockRef("else"))
                }
                block("then") {
                    br(BlockRef("merge"))
                }
                block("else") {
                    br(BlockRef("merge"))
                }
                block("merge") {
                    val result = phi(Type.I32, listOf(i32(1) to BlockRef("then"), i32(0) to BlockRef("else")))
                    ret(result)
                }
            }
        }
        val mergeInstrs = mod.functions[0].blocks[3].instructions
        assertTrue(mergeInstrs[0] is Phi)
    }

    @Test
    fun `block builder emits select`() {
        val mod = module("test") {
            function("max", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val cmp = icmp(ICmpPredicate.SGT, param(0), param(1))
                    val result = select(cmp, param(0), param(1))
                    ret(result)
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[1] is Select)
    }

    @Test
    fun `block builder emits switch`() {
        val mod = module("test") {
            function("f", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    switch(param(0), "default", listOf(
                        Constant.I32(0) to "case0",
                        Constant.I32(1) to "case1",
                    ))
                }
                block("case0") { ret(i32(10)) }
                block("case1") { ret(i32(20)) }
                block("default") { ret(i32(-1)) }
            }
        }
        val sw = mod.functions[0].blocks[0].instructions.last() as Switch
        assertEquals(2, sw.cases.size)
    }

    @Test
    fun `block builder emits call`() {
        val mod = module("test") {
            function("caller", emptyList(), Type.I32) {
                block("entry") {
                    val result = call("callee", listOf(i32(1)), Type.I32)
                    ret(result)
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is Call)
    }

    @Test
    fun `block builder emits void call`() {
        val mod = module("test") {
            function("caller", emptyList(), Type.Void) {
                block("entry") {
                    call("sideEffect", emptyList(), Type.Void)
                    ret()
                }
            }
        }
        val callInstr = mod.functions[0].blocks[0].instructions[0] as Call
        assertNull(callInstr.dest)
    }

    @Test
    fun `block builder emits high-level object operations`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val obj = newObject("Point")
                    putField(obj, "Point", "x", Type.F64, f64(1.0))
                    val x = getField(obj, "Point", "x", Type.F64)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is NewObject)
        assertTrue(instrs[1] is PutField)
        assertTrue(instrs[2] is GetField)
    }

    @Test
    fun `block builder emits array operations`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val arr = newArray(Type.I32, i32(10))
                    arraySet(arr, i32(0), i32(42), Type.I32)
                    val elem = arrayGet(arr, i32(0), Type.I32)
                    val len = arrayLength(arr)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is NewArray)
        assertTrue(instrs[1] is ArraySet)
        assertTrue(instrs[2] is ArrayGet)
        assertTrue(instrs[3] is ArrayLength)
    }

    @Test
    fun `createBlock and addBlock imperative style`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                val entry = createBlock("entry")
                entry.ret()
                addBlock(entry)
            }
        }
        assertEquals(1, mod.functions[0].blocks.size)
        assertEquals("entry", mod.functions[0].blocks[0].label)
    }

    @Test
    fun `createBlock emits instructions correctly`() {
        val mod = module("test") {
            function("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                val entry = createBlock("entry")
                val sum = entry.add(param(0), param(1))
                entry.ret(sum)
                addBlock(entry)
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertEquals(2, instrs.size)
    }

    @Test
    fun `addBlock with pre-built BasicBlock`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                val bb = BasicBlock("entry", listOf(Ret(null)))
                addBlock(bb)
            }
        }
        assertEquals(1, mod.functions[0].blocks.size)
    }

    @Test
    fun `i1 constant helper`() {
        val t = i1(true)
        assertTrue(t is Constant.I1)
        assertEquals(true, (t as Constant.I1).value)
    }

    @Test
    fun `i1 constant false`() {
        val f = i1(false)
        assertEquals(false, (f as Constant.I1).value)
    }

    @Test
    fun `i8 constant helper`() {
        val v = i8(42)
        assertTrue(v is Constant.I8)
    }

    @Test
    fun `i16 constant helper`() {
        val v = i16(1000)
        assertTrue(v is Constant.I16)
    }

    @Test
    fun `i32 constant helper`() {
        val v = i32(42)
        assertTrue(v is Constant.I32)
        assertEquals(42, (v as Constant.I32).value)
    }

    @Test
    fun `i64 constant helper`() {
        val v = i64(100L)
        assertTrue(v is Constant.I64)
        assertEquals(100L, (v as Constant.I64).value)
    }

    @Test
    fun `i128 constant helper`() {
        val v = i128(999L)
        assertTrue(v is Constant.I128)
    }

    @Test
    fun `f32 constant helper`() {
        val v = f32(3.14f)
        assertTrue(v is Constant.F32)
        assertEquals(3.14f, (v as Constant.F32).value)
    }

    @Test
    fun `f64 constant helper`() {
        val v = f64(2.71828)
        assertTrue(v is Constant.F64)
        assertEquals(2.71828, (v as Constant.F64).value)
    }

    @Test
    fun `f16 constant helper`() {
        val v = f16(1.0f)
        assertTrue(v is Constant.F16)
    }

    @Test
    fun `bf16 constant helper`() {
        val v = bf16(1.0f)
        assertTrue(v is Constant.BF16)
    }

    @Test
    fun `f80 constant helper`() {
        val v = f80(3.14)
        assertTrue(v is Constant.F80)
    }

    @Test
    fun `f128 constant helper`() {
        val v = f128(3.14)
        assertTrue(v is Constant.F128)
    }

    @Test
    fun `ModuleBuilder chaining returns self`() {
        val builder = DslModuleBuilder("test")
        val result = builder.targetTriple("x86_64")
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder dataLayout chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.dataLayout("e-m:e")
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder sourceFile chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.sourceFile("test.c")
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder targetFeature chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.targetFeature("+avx")
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder moduleInlineAsm chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.moduleInlineAsm(".text")
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder function chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.function("f", emptyList(), Type.Void) {
            block("entry") { ret() }
        }
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder global chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.global("g", Type.I32)
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder struct chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.struct("S", listOf(Param("x", Type.I32)))
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder classDef chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.classDef(ClassDefinition("C"))
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder interfaceDef chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.interfaceDef(InterfaceDefinition("I"))
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder enumDef chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.enumDef(EnumDefinition("E", emptyList()))
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder typeAlias chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.typeAlias("Alias", Type.I32)
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder metadata chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.metadata("k", MetadataValue.IntMD(1))
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder globalCtor chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.globalCtor("init")
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder globalDtor chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.globalDtor("fini")
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder ifunc chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.ifunc("f", "r", Type.Function(emptyList(), Type.I32))
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder comdat chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.comdat("g", ComdatSelectionKind.ANY)
        assertSame(builder, result)
    }

    @Test
    fun `ModuleBuilder moduleFlag chaining`() {
        val builder = DslModuleBuilder("test")
        val result = builder.moduleFlag("k", ModuleFlagValue.IntFlag(1))
        assertSame(builder, result)
    }

    @Test
    fun `FunctionBuilder block chaining`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("a") { br(BlockRef("b")) }
                    .block("b") { ret() }
            }
        }
        assertEquals(2, mod.functions[0].blocks.size)
    }

    @Test
    fun `nested block scoping preserves instruction order`() {
        val mod = module("test") {
            function("f", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val a = add(param(0), i32(1))
                    val b = mul(a, i32(2))
                    val c = sub(b, i32(3))
                    ret(c)
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertEquals(4, instrs.size)
        assertTrue(instrs[0] is Add)
        assertTrue(instrs[1] is Mul)
        assertTrue(instrs[2] is Sub)
        assertTrue(instrs[3] is Ret)
    }

    @Test
    fun `SSA names are unique across blocks`() {
        val mod = module("test") {
            function("f", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val a = add(param(0), i32(1))
                    br(BlockRef("exit"))
                }
                block("exit") {
                    val b = add(param(0), i32(2))
                    ret(b)
                }
            }
        }
        val addInEntry = mod.functions[0].blocks[0].instructions[0] as Add
        val addInExit = mod.functions[0].blocks[1].instructions[0] as Add
        assertNotEquals(addInEntry.dest.name, addInExit.dest.name)
    }

    @Test
    fun `complex module with globals functions and structs`() {
        val mod = module("complex") {
            targetTriple("wasm32-unknown-unknown")
            struct("Vec2", listOf(Param("x", Type.F32), Param("y", Type.F32)))
            global("origin", Type.Struct("Vec2", listOf(Type.F32, Type.F32)),
                Constant.StructConst(Type.Struct("Vec2", listOf(Type.F32, Type.F32)), listOf(Constant.F32(0.0f), Constant.F32(0.0f))))
            function("dot", listOf(Param("a", Type.Struct("Vec2", listOf(Type.F32, Type.F32))),
                Param("b", Type.Struct("Vec2", listOf(Type.F32, Type.F32)))), Type.F32) {
                block("entry") {
                    val ax = extractValue(param(0), 0)
                    val bx = extractValue(param(1), 0)
                    val ay = extractValue(param(0), 1)
                    val by = extractValue(param(1), 1)
                    val px = fmul(ax, bx)
                    val py = fmul(ay, by)
                    val sum = fadd(px, py)
                    ret(sum)
                }
            }
        }
        assertEquals(1, mod.structs.size)
        assertEquals(1, mod.globals.size)
        assertEquals(1, mod.functions.size)
        assertEquals(8, mod.functions[0].blocks[0].instructions.size)
    }

    @Test
    fun `block builder with bitwise operations`() {
        val mod = module("test") {
            function("mask", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val shifted = shl(param(0), i32(4))
                    val masked = and(shifted, i32(0xFF0))
                    ret(masked)
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is Shl)
        assertTrue(instrs[1] is And)
    }

    @Test
    fun `block builder with comparison and branch`() {
        val mod = module("test") {
            function("clamp", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val tooSmall = icmp(ICmpPredicate.SLT, param(0), i32(0))
                    condBr(tooSmall, BlockRef("clampLow"), BlockRef("checkHigh"))
                }
                block("clampLow") {
                    ret(i32(0))
                }
                block("checkHigh") {
                    val tooBig = icmp(ICmpPredicate.SGT, param(0), i32(100))
                    condBr(tooBig, BlockRef("clampHigh"), BlockRef("inRange"))
                }
                block("clampHigh") {
                    ret(i32(100))
                }
                block("inRange") {
                    ret(param(0))
                }
            }
        }
        assertEquals(5, mod.functions[0].blocks.size)
    }

    @Test
    fun `block builder with float comparison`() {
        val mod = module("test") {
            function("isPositive", listOf(Param("x", Type.F64)), Type.I1) {
                block("entry") {
                    val result = fcmp(FCmpPredicate.OGT, param(0), f64(0.0))
                    ret(result)
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is FCmp)
    }

    @Test
    fun `block builder with GEP`() {
        val mod = module("test") {
            function("f", emptyList(), Type.I32) {
                block("entry") {
                    val arr = alloca(Type.Array(Type.I32, 10))
                    val elemPtr = gep(Type.Array(Type.I32, 10), arr, i32(0), i32(3))
                    store(i32(42), elemPtr)
                    val loaded = load(Type.I32, elemPtr)
                    ret(loaded)
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is Alloca)
        assertTrue(instrs[1] is GetElementPtr)
    }

    @Test
    fun `block builder with virtual call`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val obj = newObject("Animal")
                    val methodType = Type.Function(emptyList(), Type.I32)
                    virtualCall(obj, "Animal", "getAge", methodType, emptyList())
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[1] is VirtualCall)
    }

    @Test
    fun `block builder with exception handling`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    tryCatch("tryBlock", listOf(
                        CatchHandler(Type.ClassRef("IOException"), "ioHandler"),
                        CatchHandler(Type.ClassRef("Exception"), "generalHandler"),
                    ), finallyBlock = "cleanup")
                    ret()
                }
            }
        }
        val tc = mod.functions[0].blocks[0].instructions[0] as TryCatchRegion
        assertEquals(2, tc.catches.size)
        assertEquals(BlockRef("cleanup"), tc.finallyBlock)
    }

    @Test
    fun `multiple modules can be built independently`() {
        val mod1 = module("mod1") {
            function("f1", emptyList(), Type.Void) {
                block("entry") { ret() }
            }
        }
        val mod2 = module("mod2") {
            function("f2", emptyList(), Type.Void) {
                block("entry") { ret() }
            }
        }
        assertEquals("mod1", mod1.name)
        assertEquals("mod2", mod2.name)
        assertEquals("f1", mod1.functions[0].name)
        assertEquals("f2", mod2.functions[0].name)
    }

    @Test
    fun `block builder with conversion chain`() {
        val mod = module("test") {
            function("f", listOf(Param("x", Type.I32)), Type.F64) {
                block("entry") {
                    val extended = sext(param(0), Type.I64)
                    val fp = sitofp(extended, Type.F64)
                    ret(fp)
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is SExt)
        assertTrue(instrs[1] is SIToFP)
    }

    @Test
    fun `block builder with unreachable`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    unreachable()
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions.last() is Unreachable)
    }

    @Test
    fun `BlockBuilder secondary constructor with FunctionBuilder`() {
        val mod = module("test") {
            function("f", listOf(Param("x", Type.I32)), Type.I32) {
                val bb = BlockBuilder("entry", this)
                bb.ret(param(0))
                addBlock(bb)
            }
        }
        assertEquals(1, mod.functions[0].blocks.size)
        assertTrue(mod.functions[0].blocks[0].instructions[0] is Ret)
    }

    @Test
    fun `module with all comdat selection kinds`() {
        val mod = module("test") {
            comdat("g1", ComdatSelectionKind.ANY)
            comdat("g2", ComdatSelectionKind.EXACT_MATCH)
            comdat("g3", ComdatSelectionKind.LARGEST)
            comdat("g4", ComdatSelectionKind.NO_DUPLICATES)
            comdat("g5", ComdatSelectionKind.SAME_SIZE)
        }
        assertEquals(5, mod.comdats.size)
    }

    @Test
    fun `module with multiple globals`() {
        val mod = module("test") {
            global("a", Type.I32, Constant.I32(1))
            global("b", Type.I64, Constant.I64(2))
            global("c", Type.F32, Constant.F32(3.0f))
        }
        assertEquals(3, mod.globals.size)
    }

    @Test
    fun `module with multiple structs`() {
        val mod = module("test") {
            struct("A", listOf(Param("x", Type.I32)))
            struct("B", listOf(Param("y", Type.F64)))
        }
        assertEquals(2, mod.structs.size)
    }

    @Test
    fun `block builder with monitor operations`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val obj = newObject("Lock")
                    monitorEnter(obj)
                    monitorExit(obj)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[1] is MonitorEnter)
        assertTrue(instrs[2] is MonitorExit)
    }

    @Test
    fun `block builder with boxing`() {
        val mod = module("test") {
            function("f", listOf(Param("x", Type.I32)), Type.ClassRef("Integer")) {
                block("entry") {
                    val boxed = box(param(0), Type.ClassRef("Integer"))
                    ret(boxed)
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is Box)
    }

    @Test
    fun `block builder with GC operations`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val ref = gcAlloc(Type.I32)
                    gcSafepoint()
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is GCAlloc)
        assertTrue(instrs[1] is GCSafepoint)
    }

    @Test
    fun `block builder with coroutine operations`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val mem = alloca(Type.I8)
                    val handle = coroBegin(i64(0), mem)
                    val state = coroSuspend()
                    coroEnd(handle)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs.any { it is CoroBegin })
        assertTrue(instrs.any { it is CoroSuspend })
        assertTrue(instrs.any { it is CoroEnd })
    }

    @Test
    fun `block builder with debug operations`() {
        val mod = module("test") {
            function("f", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    debugLoc(10, 5, "f")
                    val ptr = alloca(Type.I32)
                    debugDeclare("x", ptr)
                    store(param(0), ptr)
                    debugValue("x", param(0))
                    val loaded = load(Type.I32, ptr)
                    ret(loaded)
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs.any { it is DebugLoc })
        assertTrue(instrs.any { it is DebugDeclare })
        assertTrue(instrs.any { it is DebugValue })
    }

    @Test
    fun `block builder with intrinsic`() {
        val mod = module("test") {
            function("f", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val result = intrinsic("llvm.bswap.i32", listOf(param(0)), Type.I32)
                    ret(result)
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is Intrinsic)
    }

    @Test
    fun `block builder with tagged union`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val unionType = Type.TaggedUnion("Option", Type.I32, listOf(
                        TaggedVariant("Some", 0, listOf(Type.I32)),
                        TaggedVariant("None", 1, emptyList()),
                    ))
                    val some = constructVariant(unionType, "Some", listOf(i32(42)))
                    val tag = getTag(some)
                    val value = getVariantField(some, "Some", 0, Type.I32)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is ConstructVariant)
        assertTrue(instrs[1] is GetTag)
        assertTrue(instrs[2] is GetVariantField)
    }

    @Test
    fun `block builder emits closure operations`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val fn = GlobalRef("adder", Type.Function(listOf(Type.I32, Type.I32), Type.I32))
                    val closureType = Type.Function(listOf(Type.I32), Type.I32)
                    val closure = closureCreate(fn, listOf(i32(10)), closureType)
                    val result = closureInvoke(closure, listOf(i32(5)), Type.I32)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is ClosureCreate)
        assertTrue(instrs[1] is ClosureInvoke)
    }

    @Test
    fun `empty module builds correctly`() {
        val mod = module("empty") {}
        assertEquals("empty", mod.name)
        assertTrue(mod.functions.isEmpty())
        assertTrue(mod.globals.isEmpty())
        assertTrue(mod.structs.isEmpty())
    }

    @Test
    fun `module with metadata node`() {
        val mod = module("test") {
            metadata("list", MetadataValue.NodeMD(listOf(
                MetadataValue.StringMD("a"),
                MetadataValue.IntMD(42),
            )))
        }
        val node = mod.metadata["list"] as MetadataValue.NodeMD
        assertEquals(2, node.values.size)
    }

    @Test
    fun `module with metadata ref`() {
        val mod = module("test") {
            metadata("ref", MetadataValue.RefMD("other"))
        }
        assertEquals(MetadataValue.RefMD("other"), mod.metadata["ref"])
    }

    @Test
    fun `block builder with invoke`() {
        val mod = module("test") {
            function("f", emptyList(), Type.I32,
                personality = FunctionRef("__gxx_personality_v0", Type.Function(emptyList(), Type.I32))
            ) {
                block("entry") {
                    val fnRef = GlobalRef("mayThrow", Type.Function(emptyList(), Type.I32))
                    invoke(fnRef, emptyList(), Type.I32, BlockRef("normal"), BlockRef("unwind"))
                }
                block("normal") {
                    ret(i32(0))
                }
                block("unwind") {
                    val lp = landingPad(
                        Type.Struct(null, listOf(Type.OpaquePointer, Type.I32)),
                        emptyList(), cleanup = true
                    )
                    resume(lp)
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is Invoke)
    }

    @Test
    fun `block builder with atomic operations`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val ptr = alloca(Type.I32)
                    store(i32(0), ptr)
                    fence(AtomicOrdering.SEQ_CST)
                    val old = atomicRMW(AtomicRMWOp.ADD, ptr, i32(1), AtomicOrdering.SEQ_CST)
                    val cx = cmpxchg(ptr, i32(1), i32(2), AtomicOrdering.SEQ_CST, AtomicOrdering.ACQUIRE)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs.any { it is Fence })
        assertTrue(instrs.any { it is AtomicRMW })
        assertTrue(instrs.any { it is CmpXchg })
    }

    @Test
    fun `block builder with refcounting`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val obj = newObject("Obj")
                    refRetain(obj)
                    val count = refCount(obj)
                    refRelease(obj)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs.any { it is RefRetain })
        assertTrue(instrs.any { it is RefCount })
        assertTrue(instrs.any { it is RefRelease })
    }

    @Test
    fun `block builder with managed call`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val fnRef = GlobalRef("nativeFunc", Type.Function(emptyList(), Type.I32))
                    val result = managedCall(fnRef, emptyList(), Type.I32, ManagedCallDirection.MANAGED_TO_NATIVE)
                    ret()
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is ManagedCall)
    }

    @Test
    fun `block builder with pin and unpin`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val ref = gcAlloc(Type.I32)
                    val pinned = pin(ref)
                    unpin(ref)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs.any { it is Pin })
        assertTrue(instrs.any { it is Unpin })
    }

    @Test
    fun `block builder with interior pointer`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val ref = gcAlloc(Type.I32)
                    val iptr = interiorPtr(ref, i32(0), Type.I32)
                    ret()
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is InteriorPtr })
    }

    @Test
    fun `block builder with write and read barrier`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val ref = gcAlloc(Type.I32)
                    writeBarrier(ref, i32(0), i32(42))
                    val rb = readBarrier(ref)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs.any { it is WriteBarrier })
        assertTrue(instrs.any { it is ReadBarrier })
    }

    @Test
    fun `block builder with type check operations`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val obj = newObject("Dog")
                    val isAnimal = instanceOf(obj, Type.ClassRef("Animal"))
                    val cast = checkCast(obj, Type.ClassRef("Animal"))
                    val tid = typeId(obj)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs.any { it is InstanceOf })
        assertTrue(instrs.any { it is CheckCast })
        assertTrue(instrs.any { it is TypeId })
    }

    @Test
    fun `block builder with static field operations`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    putStatic("Counter", "count", Type.I32, i32(0))
                    val value = getStatic("Counter", "count", Type.I32)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is PutStatic)
        assertTrue(instrs[1] is GetStatic)
    }

    @Test
    fun `block builder with static call`() {
        val mod = module("test") {
            function("f", emptyList(), Type.I32) {
                block("entry") {
                    val methodType = Type.Function(listOf(Type.I32), Type.I32)
                    val result = staticCall("Math", "abs", methodType, listOf(i32(-5)))
                    ret(result)
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is StaticCall)
    }

    @Test
    fun `block builder with interface call`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val obj = newObject("ArrayList")
                    val methodType = Type.Function(listOf(Type.ClassRef("Object")), Type.I1)
                    interfaceCall(obj, "Collection", "add", methodType, listOf(obj))
                    ret()
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is InterfaceCall })
    }

    @Test
    fun `block builder with special call`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val obj = newObject("Child")
                    val methodType = Type.Function(emptyList(), Type.Void)
                    specialCall(obj, "Parent", "init", methodType, emptyList())
                    ret()
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is SpecialCall })
    }

    @Test
    fun `block builder with constructor call`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val obj = newObject("Point")
                    val ctorType = Type.Function(listOf(Type.F64, Type.F64), Type.Void)
                    constructorCall(obj, "Point", ctorType, listOf(f64(1.0), f64(2.0)))
                    ret()
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is ConstructorCall })
    }

    @Test
    fun `block builder with throw`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val exc = newObject("RuntimeException")
                    throwException(exc)
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions.last() is Throw)
    }

    @Test
    fun `block builder with memcpy memset memmove`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val dst = alloca(Type.I8)
                    val src = alloca(Type.I8)
                    memcpy(dst, src, i32(10))
                    memset(dst, i8(0), i32(10))
                    memmove(dst, src, i32(10))
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs.any { it is MemCpy })
        assertTrue(instrs.any { it is MemSet })
        assertTrue(instrs.any { it is MemMove })
    }

    @Test
    fun `block builder with stack save and restore`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val sp = stackSave()
                    stackRestore(sp)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs.any { it is StackSave })
        assertTrue(instrs.any { it is StackRestore })
    }

    @Test
    fun `block builder with vector operations`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val vecType = Type.Vector(Type.I32, 4)
                    val vec = splat(i32(42), vecType)
                    val elem = extractElement(vec, i32(0))
                    val vec2 = insertElement(vec, i32(99), i32(1))
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs.any { it is Splat })
        assertTrue(instrs.any { it is ExtractElement })
        assertTrue(instrs.any { it is InsertElement })
    }

    @Test
    fun `block builder with inline asm`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    inlineAsm("nop", "")
                    ret()
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is InlineAsm)
    }

    @Test
    fun `block builder with assume and expect`() {
        val mod = module("test") {
            function("f", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val cond = icmp(ICmpPredicate.SGT, param(0), i32(0))
                    assume(cond)
                    val result = expect(param(0), Constant.I32(1))
                    ret(result)
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs.any { it is Assume })
        assertTrue(instrs.any { it is Expect })
    }

    @Test
    fun `block builder with freeze`() {
        val mod = module("test") {
            function("f", listOf(Param("x", Type.I32)), Type.I32) {
                block("entry") {
                    val frozen = freeze(param(0))
                    ret(frozen)
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is Freeze)
    }

    @Test
    fun `block builder with trap and debug trap`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    debugTrap()
                    trap()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is DebugTrap)
        assertTrue(instrs[1] is Trap)
    }

    @Test
    fun `block builder with indirect branch`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val addr = alloca(Type.I8)
                    indirectBr(addr, listOf(BlockRef("t1"), BlockRef("t2")))
                }
                block("t1") { ret() }
                block("t2") { ret() }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions.last() is IndirectBr)
    }

    @Test
    fun `block builder with multi array`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val arr = newMultiArray(Type.I32, listOf(i32(3), i32(4)))
                    ret()
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is NewMultiArray)
    }

    @Test
    fun `block builder with vaarg operations`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void, isVarArg = true) {
                block("entry") {
                    val list = alloca(Type.I8)
                    vaStart(list)
                    val arg = vaArg(list, Type.I32)
                    vaEnd(list)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs.any { it is VAStart })
        assertTrue(instrs.any { it is VAArg })
        assertTrue(instrs.any { it is VAEnd })
    }

    @Test
    fun `block builder with lifetime markers`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val ptr = alloca(Type.I32)
                    lifetimeStart(ptr, 4)
                    store(i32(0), ptr)
                    lifetimeEnd(ptr, 4)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs.any { it is LifetimeStart })
        assertTrue(instrs.any { it is LifetimeEnd })
    }

    @Test
    fun `block builder with extractValue and insertValue`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val structType = Type.Struct(null, listOf(Type.I32, Type.F64))
                    val undef = InstructionRef("%undef", structType)
                    val withI32 = insertValue(undef, i32(42), 0)
                    val extracted = extractValue(withI32, 0)
                    ret()
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs.any { it is InsertValue })
        assertTrue(instrs.any { it is ExtractValue })
    }

    @Test
    fun `block builder with overflow arithmetic`() {
        val mod = module("test") {
            function("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val result = saddOverflow(param(0), param(1))
                    val value = extractValue(result, 0)
                    val overflowed = extractValue(result, 1)
                    ret(value)
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is SAddOverflow)
    }

    @Test
    fun `block builder with saturating arithmetic`() {
        val mod = module("test") {
            function("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val result = saddSat(param(0), param(1))
                    ret(result)
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is SAddSat)
    }

    @Test
    fun `block builder with min max abs`() {
        val mod = module("test") {
            function("f", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                block("entry") {
                    val mn = smin(param(0), param(1))
                    val mx = smax(param(0), param(1))
                    val ab = abs(param(0))
                    ret(ab)
                }
            }
        }
        val instrs = mod.functions[0].blocks[0].instructions
        assertTrue(instrs[0] is SMin)
        assertTrue(instrs[1] is SMax)
        assertTrue(instrs[2] is Abs)
    }

    @Test
    fun `module global without initializer`() {
        val mod = module("test") {
            global("uninit", Type.I32)
        }
        assertNull(mod.globals[0].initializer)
    }

    @Test
    fun `module with all thread local modes`() {
        val mod = module("test") {
            global("a", Type.I32, threadLocal = ThreadLocalMode.GENERAL_DYNAMIC)
            global("b", Type.I32, threadLocal = ThreadLocalMode.LOCAL_DYNAMIC)
            global("c", Type.I32, threadLocal = ThreadLocalMode.INITIAL_EXEC)
            global("d", Type.I32, threadLocal = ThreadLocalMode.LOCAL_EXEC)
        }
        assertEquals(4, mod.globals.size)
        assertEquals(ThreadLocalMode.GENERAL_DYNAMIC, mod.globals[0].threadLocal)
        assertEquals(ThreadLocalMode.LOCAL_DYNAMIC, mod.globals[1].threadLocal)
        assertEquals(ThreadLocalMode.INITIAL_EXEC, mod.globals[2].threadLocal)
        assertEquals(ThreadLocalMode.LOCAL_EXEC, mod.globals[3].threadLocal)
    }

    @Test
    fun `module function with all linkage types`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void, linkage = Linkage.INTERNAL) { block("e") { ret() } }
            function("g", emptyList(), Type.Void, linkage = Linkage.PRIVATE) { block("e") { ret() } }
            function("h", emptyList(), Type.Void, linkage = Linkage.WEAK) { block("e") { ret() } }
        }
        assertEquals(Linkage.INTERNAL, mod.functions[0].linkage)
        assertEquals(Linkage.PRIVATE, mod.functions[1].linkage)
        assertEquals(Linkage.WEAK, mod.functions[2].linkage)
    }

    @Test
    fun `block builder with dynamic call`() {
        val mod = module("test") {
            function("f", emptyList(), Type.Void) {
                block("entry") {
                    val bootstrap = BootstrapMethod("Bootstrap", "invoke", Type.Function(emptyList(), Type.I32))
                    val methodType = Type.Function(emptyList(), Type.I32)
                    val result = dynamicCall(bootstrap, "target", methodType, emptyList())
                    ret()
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions[0] is DynamicCall)
    }

    @Test
    fun `block builder with unbox`() {
        val mod = module("test") {
            function("f", emptyList(), Type.I32) {
                block("entry") {
                    val boxed = newObject("Integer")
                    val value = unbox(boxed, Type.I32)
                    ret(value)
                }
            }
        }
        assertTrue(mod.functions[0].blocks[0].instructions.any { it is Unbox })
    }
}
