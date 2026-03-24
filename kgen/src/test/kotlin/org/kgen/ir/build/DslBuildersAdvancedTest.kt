package org.kgen.ir.build

import org.kgen.ir.*
import org.kgen.ir.instructions.*
import org.kgen.ir.target.Target
import org.kgen.ir.types.*
import org.kgen.ir.verify.IrVerifier
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DslBuildersAdvancedTest {

    @Nested
    inner class ModuleBuilderAdvanced {

        @Test
        fun `ifunc declaration`() {
            val mod = module("test") {
                ifunc("my_memcpy", "resolve_memcpy",
                    Type.Function(listOf(Type.OpaquePointer, Type.OpaquePointer, Type.I64), Type.OpaquePointer))
            }
            assertEquals(1, mod.ifuncs.size)
            assertEquals("my_memcpy", mod.ifuncs[0].name)
            assertEquals("resolve_memcpy", mod.ifuncs[0].resolverFunction)
        }

        @Test
        fun `comdat definition`() {
            val mod = module("test") {
                comdat("group", ComdatSelectionKind.EXACT_MATCH)
            }
            assertEquals(1, mod.comdats.size)
            assertEquals(ComdatSelectionKind.EXACT_MATCH, mod.comdats[0].selectionKind)
        }

        @Test
        fun `module flag`() {
            val mod = module("test") {
                moduleFlag("wchar_size", ModuleFlagValue.IntFlag(4L))
            }
            assertEquals(ModuleFlagValue.IntFlag(4L), mod.moduleFlags["wchar_size"])
        }

        @Test
        fun `module inline asm`() {
            val mod = module("test") {
                moduleInlineAsm(".section .note")
            }
            assertEquals(".section .note", mod.moduleInlineAsm)
        }

        @Test
        fun `global constructor and destructor`() {
            val mod = module("test") {
                globalCtor("__init", 100)
                globalDtor("__fini", 200)
            }
            assertEquals(1, mod.globalCtors.size)
            assertEquals("__init", mod.globalCtors[0].function)
            assertEquals(100, mod.globalCtors[0].priority)
            assertEquals(1, mod.globalDtors.size)
            assertEquals("__fini", mod.globalDtors[0].function)
        }

        @Test
        fun `class definition`() {
            val cls = ClassDefinition("Animal",
                fields = listOf(FieldDefinition("name", Type.OpaquePointer)),
                methods = listOf(MethodDefinition("speak", emptyList(), Type.Void)))
            val mod = module("test") {
                classDef(cls)
            }
            assertEquals(1, mod.classes.size)
            assertEquals("Animal", mod.classes[0].name)
        }

        @Test
        fun `interface definition`() {
            val iface = InterfaceDefinition("Comparable",
                methods = listOf(MethodDefinition("compareTo", listOf(Param("other", Type.ClassRef("Object"))), Type.I32)))
            val mod = module("test") {
                interfaceDef(iface)
            }
            assertEquals(1, mod.interfaces.size)
            assertEquals("Comparable", mod.interfaces[0].name)
        }

        @Test
        fun `enum definition`() {
            val enumDef = EnumDefinition("Color", listOf(
                EnumVariant("RED", 0),
                EnumVariant("GREEN", 1),
                EnumVariant("BLUE", 2),
            ))
            val mod = module("test") {
                enumDef(enumDef)
            }
            assertEquals(1, mod.enums.size)
            assertEquals(3, mod.enums[0].variants.size)
        }

        @Test
        fun `type alias`() {
            val mod = module("test") {
                typeAlias("IntPtr", Type.Pointer(Type.I32))
            }
            assertEquals(1, mod.aliases.size)
            assertEquals("IntPtr", mod.aliases[0].name)
        }

        @Test
        fun `metadata`() {
            val mod = module("test") {
                metadata("producer", MetadataValue.StringMD("kgen 1.0"))
            }
            assertEquals(MetadataValue.StringMD("kgen 1.0"), mod.metadata["producer"])
        }
    }

    @Nested
    inner class FunctionBuilderAdvanced {

        @Test
        fun `external function has no blocks`() {
            val mod = module("test") {
                function("ext_fn", listOf(Param("x", Type.I32)), Type.I32, isExternal = true)
            }
            assertTrue(mod.functions[0].isExternal)
            assertTrue(mod.functions[0].blocks.isEmpty())
        }

        @Test
        fun `function with custom linkage`() {
            val mod = module("test") {
                function("weak_fn", emptyList(), Type.Void, linkage = Linkage.WEAK) {
                    block("entry") { ret() }
                }
            }
            assertEquals(Linkage.WEAK, mod.functions[0].linkage)
        }

        @Test
        fun `function with visibility`() {
            val mod = module("test") {
                function("hidden_fn", emptyList(), Type.Void, visibility = Visibility.HIDDEN) {
                    block("entry") { ret() }
                }
            }
            assertEquals(Visibility.HIDDEN, mod.functions[0].visibility)
        }

        @Test
        fun `function with attributes`() {
            val mod = module("test") {
                function("noreturn_fn", emptyList(), Type.Void,
                    attributes = setOf(FnAttribute.NORETURN)) {
                    block("entry") { unreachable() }
                }
            }
            assertTrue(mod.functions[0].attributes.contains(FnAttribute.NORETURN))
        }

        @Test
        fun `function with varargs`() {
            val mod = module("test") {
                function("va_fn", listOf(Param("fmt", Type.OpaquePointer)), Type.I32,
                    isVarArg = true) {
                    block("entry") { ret(i32(0)) }
                }
            }
            assertTrue(mod.functions[0].isVarArg)
        }

        @Test
        fun `function with section`() {
            val mod = module("test") {
                function("init_fn", emptyList(), Type.Void, section = ".init") {
                    block("entry") { ret() }
                }
            }
            assertEquals(".init", mod.functions[0].section)
        }

        @Test
        fun `function with gc strategy`() {
            val mod = module("test") {
                function("gc_fn", emptyList(), Type.Void, gc = "statepoint-example") {
                    block("entry") { ret() }
                }
            }
            assertEquals("statepoint-example", mod.functions[0].gc)
        }

        @Test
        fun `createBlock and addBlock workflow`() {
            val mod = module("test") {
                function("multi_block", listOf(Param("x", Type.I32)), Type.I32) {
                    val entryBlock = createBlock("entry")
                    val exitBlock = createBlock("exit")

                    entryBlock.br(BlockRef("exit"))
                    exitBlock.ret(param(0))

                    addBlock(entryBlock)
                    addBlock(exitBlock)
                }
            }
            assertEquals(2, mod.functions[0].blocks.size)
            assertEquals("entry", mod.functions[0].blocks[0].label)
            assertEquals("exit", mod.functions[0].blocks[1].label)
        }

        @Test
        fun `param access in function builder`() {
            val mod = module("test") {
                function("echo", listOf(Param("a", Type.I32), Param("b", Type.I64)), Type.I32) {
                    assertEquals(2, paramCount)
                    assertEquals("a", param(0).name)
                    assertEquals(Type.I32, param(0).type)
                    assertEquals("b", param(1).name)
                    assertEquals(Type.I64, param(1).type)
                    block("entry") { ret(param(0)) }
                }
            }
            assertTrue(IrVerifier.verify(mod).isValid)
        }

        @Test
        fun `addBlock with BasicBlock object`() {
            val mod = module("test") {
                function("prebuilt", emptyList(), Type.Void) {
                    val bb = BasicBlock("entry", listOf(Ret(null)))
                    addBlock(bb)
                }
            }
            assertEquals(1, mod.functions[0].blocks.size)
        }
    }

    @Nested
    inner class BlockBuilderAdvanced {

        @Test
        fun `block builder emits multiple instructions`() {
            val mod = module("test") {
                function("chain", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                    block("entry") {
                        val sum = add(param(0), param(1))
                        val doubled = mul(sum, i32(2))
                        val result = sub(doubled, param(0))
                        ret(result)
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
        fun `block builder with control flow`() {
            val mod = module("test") {
                function("branch", listOf(Param("x", Type.I32)), Type.I32) {
                    block("entry") {
                        val cmp = icmp(ICmpPredicate.SGT, param(0), i32(0))
                        condBr(cmp, BlockRef("pos"), BlockRef("neg"))
                    }
                    block("pos") { ret(param(0)) }
                    block("neg") { ret(neg(param(0))) }
                }
            }
            assertEquals(3, mod.functions[0].blocks.size)
            assertTrue(IrVerifier.verify(mod).isValid)
        }

        @Test
        fun `block builder with memory ops`() {
            val mod = module("test") {
                function("mem", listOf(Param("x", Type.I32)), Type.I32) {
                    block("entry") {
                        val ptr = alloca(Type.I32)
                        store(param(0), ptr)
                        val loaded = load(Type.I32, ptr)
                        ret(loaded)
                    }
                }
            }
            val instrs = mod.functions[0].blocks[0].instructions
            assertTrue(instrs[0] is Alloca)
            assertTrue(instrs[1] is Store)
            assertTrue(instrs[2] is Load)
        }

        @Test
        fun `BlockBuilder secondary constructor with FunctionBuilder`() {
            val mod = module("test") {
                function("alt_block", emptyList(), Type.Void) {
                    val bb = BlockBuilder("entry", this)
                    bb.ret()
                    addBlock(bb)
                }
            }
            assertEquals(1, mod.functions[0].blocks.size)
            assertEquals("entry", mod.functions[0].blocks[0].label)
        }
    }

    @Nested
    inner class GlobalDefinitions {

        @Test
        fun `global with all properties`() {
            val mod = module("test") {
                global("tls_counter", Type.I32, Type.i32(0),
                    isConstant = false,
                    linkage = Linkage.INTERNAL,
                    visibility = Visibility.HIDDEN,
                    threadLocal = ThreadLocalMode.LOCAL_EXEC,
                    section = ".tdata",
                    align = 4,
                    addressSpace = 0)
            }
            val g = mod.globals[0]
            assertEquals("tls_counter", g.name)
            assertFalse(g.isConstant)
            assertEquals(Linkage.INTERNAL, g.linkage)
            assertEquals(Visibility.HIDDEN, g.visibility)
            assertEquals(ThreadLocalMode.LOCAL_EXEC, g.threadLocal)
            assertEquals(".tdata", g.section)
            assertEquals(4, g.align)
        }

        @Test
        fun `constant global`() {
            val mod = module("test") {
                global("PI", Type.F64, Type.f64(3.14159265), isConstant = true)
            }
            assertTrue(mod.globals[0].isConstant)
        }
    }

    @Nested
    inner class ConstantHelpers {

        @Test
        fun `i1 creates boolean constant`() {
            assertEquals(Type.I1, i1(true).type)
        }

        @Test
        fun `i8 creates byte constant`() {
            assertEquals(Type.I8, i8(42).type)
        }

        @Test
        fun `i16 creates short constant`() {
            assertEquals(Type.I16, i16(1000).type)
        }

        @Test
        fun `i32 creates int constant`() {
            assertEquals(Type.I32, i32(100).type)
        }

        @Test
        fun `i64 creates long constant`() {
            assertEquals(Type.I64, i64(100L).type)
        }

        @Test
        fun `i128 creates i128 constant`() {
            assertEquals(Type.I128, i128(0L).type)
        }

        @Test
        fun `f32 creates float constant`() {
            assertEquals(Type.F32, f32(1.5f).type)
        }

        @Test
        fun `f64 creates double constant`() {
            assertEquals(Type.F64, f64(3.14).type)
        }

        @Test
        fun `f16 creates half float constant`() {
            assertEquals(Type.F16, f16(1.0f).type)
        }

        @Test
        fun `bf16 creates brain float constant`() {
            assertEquals(Type.BF16, bf16(1.0f).type)
        }

        @Test
        fun `f80 creates extended double constant`() {
            val c = f80(1.0)
            assertTrue(c is Constant.F80)
        }

        @Test
        fun `f128 creates quad precision constant`() {
            val c = f128(1.0)
            assertTrue(c is Constant.F128)
        }
    }

    @Nested
    inner class CompleteModuleScenarios {

        @Test
        fun `full module with globals structs and functions`() {
            val mod = module("calculator", Target.x86_64()) {
                targetTriple("x86_64-unknown-linux-gnu")
                dataLayout("e-m:e-p270:32:32")
                sourceFile("calc.kt")
                targetFeature("+sse4.2")

                struct("Result", listOf(Param("value", Type.I32), Param("error", Type.I1)))
                global("last_result", Type.I32, Type.i32(0))
                metadata("version", MetadataValue.StringMD("1.0"))

                function("add", listOf(Param("a", Type.I32), Param("b", Type.I32)), Type.I32) {
                    block("entry") {
                        val sum = add(param(0), param(1))
                        ret(sum)
                    }
                }
                function("negate", listOf(Param("x", Type.I32)), Type.I32) {
                    block("entry") {
                        ret(neg(param(0)))
                    }
                }
            }

            assertEquals("calculator", mod.name)
            assertEquals("x86_64-unknown-linux-gnu", mod.targetTriple)
            assertEquals("e-m:e-p270:32:32", mod.dataLayout)
            assertEquals("calc.kt", mod.sourceFile)
            assertTrue(mod.targetFeatures.contains("+sse4.2"))
            assertEquals(1, mod.structs.size)
            assertEquals(1, mod.globals.size)
            assertEquals(2, mod.functions.size)
            assertTrue(IrVerifier.verify(mod).isValid)
        }

        @Test
        fun `ModuleBuilder build returns fluent self`() {
            val builder = DslModuleBuilder("test", Target.wasm())
            val same = builder.targetTriple("wasm32-unknown-unknown")
            assertSame(builder, same)

            val same2 = builder.dataLayout("layout")
            assertSame(builder, same2)

            val same3 = builder.sourceFile("file.kt")
            assertSame(builder, same3)

            val same4 = builder.targetFeature("+simd128")
            assertSame(builder, same4)
        }
    }
}
