package org.kgen.runtime.compile

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.kgen.ir.target.Target
import org.kgen.reflect.NativeCode
import org.kgen.reflect.emit.NativeModuleBuilder
import org.kgen.target.jvm.*

/**
 * End-to-end execution tests for the Java-to-Native pipeline.
 *
 * These tests compile Java class files through the full pipeline
 * (javac bytecode → IR → native code), load into memory, and
 * verify the functions produce correct results.
 */
@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JavaToNativeExecutionTest {

    private fun compileAndLoad(classFiles: List<ByteArray>): NativeCode {
        val target = NativeModuleBuilder.hostTarget()
        val compiler = NativeCompiler(target)
        val modules = compiler.compileToModules(classFiles)
        val merged = modules.flatMap { it.functions }.let { fns ->
            modules.first().copy(
                functions = fns,
                globals = modules.flatMap { it.globals },
                targetTriple = NativeModuleBuilder.hostTriple(),
            )
        }
        val obj = compiler.generateObject(merged)
        return NativeCode.load(obj)
    }

    private fun buildSimpleClass(
        className: String,
        methods: List<Pair<String, (ClassFileBuilder.CodeEmitter) -> Unit>>,
    ): ByteArray {
        val builder = ClassFileBuilder(className)
        for ((sig, body) in methods) {
            val parts = sig.split(":")
            val name = parts[0]
            val desc = parts[1]
            builder.method(name, desc, AccessFlags.PUBLIC or AccessFlags.STATIC, body)
        }
        return builder.toBytes()
    }

    @Test
    fun addTwoInts() {
        val classBytes = buildSimpleClass("org/kgen/test/Add", listOf(
            "add:(II)I" to { code ->
                code.iload(0)
                code.iload(1)
                code.iadd()
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(7, it.callInt("add", 3, 4))
            assertEquals(0, it.callInt("add", 0, 0))
            assertEquals(-1, it.callInt("add", 1, -2))
        }
    }

    @Test
    fun multiplyTwoInts() {
        val classBytes = buildSimpleClass("org/kgen/test/Mul", listOf(
            "mul:(II)I" to { code ->
                code.iload(0)
                code.iload(1)
                code.imul()
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(42, it.callInt("mul", 6, 7))
            assertEquals(0, it.callInt("mul", 0, 999))
        }
    }

    @Test
    fun subtractAndNegate() {
        val classBytes = buildSimpleClass("org/kgen/test/SubNeg", listOf(
            "sub:(II)I" to { code ->
                code.iload(0)
                code.iload(1)
                code.isub()
                code.ireturn()
            },
            "neg:(I)I" to { code ->
                code.iload(0)
                code.ineg()
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(3, it.callInt("sub", 7, 4))
            assertEquals(-5, it.callInt("neg", 5))
        }
    }

    @Test
    fun conditionalLogic() {
        val classBytes = buildSimpleClass("org/kgen/test/Cond", listOf(
            "max:(II)I" to { code ->
                code.iload(0)
                code.iload(1)
                code.ifIcmpge("first")
                code.iload(1)
                code.ireturn()
                code.label("first")
                code.iload(0)
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(7, it.callInt("max", 3, 7))
            assertEquals(7, it.callInt("max", 7, 3))
            assertEquals(5, it.callInt("max", 5, 5))
        }
    }

    @Test
    fun absoluteValue() {
        val classBytes = buildSimpleClass("org/kgen/test/Abs", listOf(
            "abs:(I)I" to { code ->
                code.iload(0)
                code.ifge("pos")
                code.iload(0)
                code.ineg()
                code.ireturn()
                code.label("pos")
                code.iload(0)
                code.ireturn()
            }
        ))

        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(5, it.callInt("abs", 5))
            assertEquals(5, it.callInt("abs", -5))
            assertEquals(0, it.callInt("abs", 0))
        }
    }

    @Test
    fun returnConstant() {
        val classBytes = buildSimpleClass("org/kgen/test/Const", listOf(
            "fortyTwo:()I" to { code ->
                code.iconst(42)
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(42, it.callInt("fortyTwo"))
        }
    }

    @Test
    fun longArithmetic() {
        val classBytes = buildSimpleClass("org/kgen/test/LongMath", listOf(
            "addLong:(JJ)J" to { code ->
                code.lload(0)
                code.lload(2)
                code.ladd()
                code.lreturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(300L, it.call("addLong", 100, 200))
        }
    }

    @Test
    fun multipleFunctionsFromSameClass() {
        val classBytes = buildSimpleClass("org/kgen/test/Multi", listOf(
            "square:(I)I" to { code ->
                code.iload(0)
                code.iload(0)
                code.imul()
                code.ireturn()
            },
            "cube:(I)I" to { code ->
                code.iload(0)
                code.iload(0)
                code.imul()
                code.iload(0)
                code.imul()
                code.ireturn()
            },
            "isZero:(I)I" to { code ->
                code.iload(0)
                code.ifne("notZero")
                code.iconst(1)
                code.ireturn()
                code.label("notZero")
                code.iconst(0)
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(25, it.callInt("square", 5))
            assertEquals(125, it.callInt("cube", 5))
            assertEquals(1, it.callInt("isZero", 0))
            assertEquals(0, it.callInt("isZero", 7))
        }
    }

    @Test
    fun whileLoop() {
        // int sum(int n) { int s = 0; while (n > 0) { s += n; n--; } return s; }
        val classBytes = buildSimpleClass("org/kgen/test/Loop", listOf(
            "sum:(I)I" to { code ->
                code.iconst(0)       // s = 0
                code.istore(1)       // local[1] = s
                code.label("loop")
                code.iload(0)        // n
                code.ifle("done")    // if n <= 0, goto done
                code.iload(1)        // s
                code.iload(0)        // n
                code.iadd()          // s + n
                code.istore(1)       // s = s + n
                code.iload(0)        // n
                code.iconst(1)       // 1
                code.isub()          // n - 1
                code.istore(0)       // n = n - 1
                code.goto("loop")
                code.label("done")
                code.iload(1)        // return s
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(15, it.callInt("sum", 5))   // 5+4+3+2+1
            assertEquals(55, it.callInt("sum", 10))  // 10+9+...+1
            assertEquals(0, it.callInt("sum", 0))
            assertEquals(0, it.callInt("sum", -1))
        }
    }

    @Test
    fun countDown() {
        // int countDown(int n) { int result = 0; while (n > 0) { result++; n--; } return result; }
        // Should return n for positive inputs, 0 for non-positive
        val classBytes = buildSimpleClass("org/kgen/test/CountDown", listOf(
            "countDown:(I)I" to { code ->
                code.iconst(0)       // result = 0
                code.istore(1)
                code.label("check")
                code.iload(0)        // n
                code.ifle("done")    // if n <= 0, goto done
                code.iload(1)        // result
                code.iconst(1)
                code.iadd()
                code.istore(1)       // result++
                code.iload(0)        // n
                code.iconst(1)
                code.isub()
                code.istore(0)       // n--
                code.goto("check")
                code.label("done")
                code.iload(1)        // return result
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(5, it.callInt("countDown", 5))
            assertEquals(10, it.callInt("countDown", 10))
            assertEquals(0, it.callInt("countDown", 0))
            assertEquals(0, it.callInt("countDown", -1))
        }
    }

    @Test
    fun bitwiseOperations() {
        val classBytes = buildSimpleClass("org/kgen/test/Bits", listOf(
            "andOr:(II)I" to { code ->
                // (a & 0xFF) | (b << 8)
                code.iload(0)
                code.iconst(255)
                code.iand()
                code.iload(1)
                code.iconst(8)
                code.ishl()
                code.ior()
                code.ireturn()
            },
            "xorSwap:(II)I" to { code ->
                // a ^ b
                code.iload(0)
                code.iload(1)
                code.ixor()
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(0x0305, it.callInt("andOr", 5, 3))
            assertEquals(0xFF01, it.callInt("andOr", 0x101, 0xFF))
            assertEquals(6, it.callInt("xorSwap", 5, 3))
            assertEquals(0, it.callInt("xorSwap", 42, 42))
        }
    }

    @Test
    fun divisionAndRemainder() {
        val classBytes = buildSimpleClass("org/kgen/test/DivRem", listOf(
            "div:(II)I" to { code ->
                code.iload(0)
                code.iload(1)
                code.idiv()
                code.ireturn()
            },
            "rem:(II)I" to { code ->
                code.iload(0)
                code.iload(1)
                code.irem()
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(3, it.callInt("div", 10, 3))
            assertEquals(1, it.callInt("rem", 10, 3))
            assertEquals(-3, it.callInt("div", -10, 3))
            assertEquals(-1, it.callInt("rem", -10, 3))
            assertEquals(5, it.callInt("div", 5, 1))
            assertEquals(0, it.callInt("rem", 6, 3))
        }
    }

    @Test
    fun nestedConditions() {
        // int clamp(int val, int min, int max) { if (val < min) return min; if (val > max) return max; return val; }
        val classBytes = buildSimpleClass("org/kgen/test/Clamp", listOf(
            "clamp:(III)I" to { code ->
                code.iload(0)        // val
                code.iload(1)        // min
                code.ifIcmpge("notLow") // if val >= min, skip
                code.iload(1)        // return min
                code.ireturn()
                code.label("notLow")
                code.iload(0)        // val
                code.iload(2)        // max
                code.ifIcmple("ok")  // if val <= max, skip
                code.iload(2)        // return max
                code.ireturn()
                code.label("ok")
                code.iload(0)        // return val
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(5, it.callInt("clamp", 5, 0, 10))
            assertEquals(0, it.callInt("clamp", -3, 0, 10))
            assertEquals(10, it.callInt("clamp", 15, 0, 10))
            assertEquals(0, it.callInt("clamp", 0, 0, 10))
            assertEquals(10, it.callInt("clamp", 10, 0, 10))
        }
    }

    @Test
    fun signFunction() {
        // sign(x): returns 1 for positive, -1 for negative, 0 for zero
        val classBytes = buildSimpleClass("org/kgen/test/Sign", listOf(
            "sign:(I)I" to { code ->
                code.iload(0)
                code.ifle("notPos")
                code.iconst(1)
                code.ireturn()
                code.label("notPos")
                code.iload(0)
                code.ifge("isZero")
                code.iconst(-1)
                code.ireturn()
                code.label("isZero")
                code.iconst(0)
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(1, it.callInt("sign", 42))
            assertEquals(1, it.callInt("sign", 1))
            assertEquals(-1, it.callInt("sign", -7))
            assertEquals(-1, it.callInt("sign", -1))
            assertEquals(0, it.callInt("sign", 0))
        }
    }

    @Test
    fun multipleReturnPaths() {
        // int classify(int x) { if (x > 0) return 1; if (x < 0) return -1; return 0; }
        val classBytes = buildSimpleClass("org/kgen/test/Classify", listOf(
            "classify:(I)I" to { code ->
                code.iload(0)
                code.ifle("notPositive")
                code.iconst(1)
                code.ireturn()
                code.label("notPositive")
                code.iload(0)
                code.ifge("zero")
                code.iconst(-1)
                code.ireturn()
                code.label("zero")
                code.iconst(0)
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(1, it.callInt("classify", 42))
            assertEquals(-1, it.callInt("classify", -7))
            assertEquals(0, it.callInt("classify", 0))
        }
    }

    @Test
    fun complexArithmeticExpression() {
        // int compute(int a, int b, int c) { return (a * b) + (b * c) - (a * c); }
        val classBytes = buildSimpleClass("org/kgen/test/Expr", listOf(
            "compute:(III)I" to { code ->
                code.iload(0); code.iload(1); code.imul()  // a*b
                code.iload(1); code.iload(2); code.imul()  // b*c
                code.iadd()                                   // (a*b) + (b*c)
                code.iload(0); code.iload(2); code.imul()  // a*c
                code.isub()                                   // result - (a*c)
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            // (2*3) + (3*4) - (2*4) = 6 + 12 - 8 = 10
            assertEquals(10, it.callInt("compute", 2, 3, 4))
            // (1*1) + (1*1) - (1*1) = 1 + 1 - 1 = 1
            assertEquals(1, it.callInt("compute", 1, 1, 1))
            // (0*5) + (5*3) - (0*3) = 0 + 15 - 0 = 15
            assertEquals(15, it.callInt("compute", 0, 5, 3))
        }
    }

    @Test
    fun nestedLoops() {
        // int sumProduct(int n) { int s = 0; for (i=1..n) for (j=1..n) s += i*j; return s; }
        // Simplified as: int sumProduct(int n) { int s = 0; int i = 1; while (i <= n) { int j = 1; while (j <= n) { s += i * j; j++; } i++; } return s; }
        val classBytes = buildSimpleClass("org/kgen/test/Nested", listOf(
            "sumProduct:(I)I" to { code ->
                code.iconst(0); code.istore(1)       // s = 0
                code.iconst(1); code.istore(2)       // i = 1
                code.label("outerLoop")
                code.iload(2); code.iload(0)
                code.ifIcmpgt("done")                 // if i > n, done
                code.iconst(1); code.istore(3)       // j = 1
                code.label("innerLoop")
                code.iload(3); code.iload(0)
                code.ifIcmpgt("nextI")                // if j > n, next i
                code.iload(1)                          // s
                code.iload(2); code.iload(3); code.imul() // i * j
                code.iadd(); code.istore(1)           // s += i * j
                code.iload(3); code.iconst(1); code.iadd(); code.istore(3) // j++
                code.goto("innerLoop")
                code.label("nextI")
                code.iload(2); code.iconst(1); code.iadd(); code.istore(2) // i++
                code.goto("outerLoop")
                code.label("done")
                code.iload(1)
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(0, it.callInt("sumProduct", 0))
            assertEquals(1, it.callInt("sumProduct", 1))     // 1*1 = 1
            assertEquals(9, it.callInt("sumProduct", 2))     // (1+2)^2 = 9
            assertEquals(36, it.callInt("sumProduct", 3))    // (1+2+3)^2 = 36
        }
    }

    @Test
    fun fibonacci() {
        // int fib(int n) { int a = 0, b = 1; while (n > 0) { int t = a + b; a = b; b = t; n--; } return a; }
        val classBytes = buildSimpleClass("org/kgen/test/Fib", listOf(
            "fib:(I)I" to { code ->
                code.iconst(0); code.istore(1)       // a = 0
                code.iconst(1); code.istore(2)       // b = 1
                code.label("loop")
                code.iload(0)
                code.ifle("done")                     // if n <= 0, done
                code.iload(1); code.iload(2)
                code.iadd(); code.istore(1)           // a = a + b (temp holds old a+b)
                // swap: need a=old_b, b=old_a+old_b
                // Actually simpler: t = a+b, a = b, b = t
                // Let's use: local1=a, local2=b, local3 not needed if we're careful
                // Rewrite: a_new = b_old, b_new = a_old + b_old
                // iload 1 (a), iload 2 (b), iadd → stack: a+b
                // iload 2 (b) → stack: a+b, b
                // istore 1 → a = b
                // istore 2 → b = a+b
                // But that's wrong sequence. Let me use 3 locals with swap trick.
                // Actually let's be direct: t=a+b, a=b, b=t using only stack ops
                code.iload(2)                          // stack: a+b, b
                code.istore(1)                         // a = b
                code.istore(2)                         // b = a+b (from earlier iadd result... wait)
                // Hmm, let me restart. More careful:
                code.iload(0); code.iconst(1); code.isub(); code.istore(0) // n--
                code.goto("loop")
                code.label("done")
                code.iload(1)
                code.ireturn()
            }
        ))
        // Actually this won't work correctly because the stack management is tricky.
        // Let me use a cleaner approach with 3 locals.
        val classBytes2 = buildSimpleClass("org/kgen/test/Fib2", listOf(
            "fib:(I)I" to { code ->
                code.iconst(0); code.istore(1)       // a = 0
                code.iconst(1); code.istore(2)       // b = 1
                code.label("loop")
                code.iload(0)
                code.ifle("done")
                // t = a + b
                code.iload(1); code.iload(2); code.iadd()
                // a = b
                code.iload(2); code.istore(1)
                // b = t (result of a+b is still on stack)
                code.istore(2)
                // n--
                code.iload(0); code.iconst(1); code.isub(); code.istore(0)
                code.goto("loop")
                code.label("done")
                code.iload(1)
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes2))
        code.use {
            assertEquals(0, it.callInt("fib", 0))
            assertEquals(1, it.callInt("fib", 1))
            assertEquals(1, it.callInt("fib", 2))
            assertEquals(2, it.callInt("fib", 3))
            assertEquals(3, it.callInt("fib", 4))
            assertEquals(5, it.callInt("fib", 5))
        }
    }

    @Test
    fun chainedIfElse() {
        // Simulates a switch-like pattern using chained if-else:
        // if (n == 0) return 10; else if (n == 1) return 20; else if (n == 2) return 30; else return -1;
        val classBytes = buildSimpleClass("org/kgen/test/Chain", listOf(
            "lookup:(I)I" to { code ->
                code.iload(0)
                code.ifne("not0")
                code.iconst(10); code.ireturn()
                code.label("not0")
                code.iload(0)
                code.iconst(1)
                code.ifIcmpne("not1")
                code.iconst(20); code.ireturn()
                code.label("not1")
                code.iload(0)
                code.iconst(2)
                code.ifIcmpne("default")
                code.iconst(30); code.ireturn()
                code.label("default")
                code.iconst(-1); code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(10, it.callInt("lookup", 0))
            assertEquals(20, it.callInt("lookup", 1))
            assertEquals(30, it.callInt("lookup", 2))
            assertEquals(-1, it.callInt("lookup", 3))
            assertEquals(-1, it.callInt("lookup", -1))
        }
    }

    @Test
    fun intArithOverflow() {
        // Tests integer arithmetic at boundary values
        val classBytes = buildSimpleClass("org/kgen/test/Overflow", listOf(
            "addOne:(I)I" to { code ->
                code.iload(0)
                code.iconst(1)
                code.iadd()
                code.ireturn()
            },
            "doubleIt:(I)I" to { code ->
                code.iload(0)
                code.iload(0)
                code.iadd()
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(Int.MIN_VALUE, it.callInt("addOne", Int.MAX_VALUE.toLong()))
            assertEquals(-2, it.callInt("doubleIt", Int.MAX_VALUE.toLong()))
            assertEquals(0, it.callInt("doubleIt", 0))
        }
    }

    @Test
    fun factorial() {
        // int fact(int n) { int r = 1; while (n > 1) { r = r * n; n--; } return r; }
        val classBytes = buildSimpleClass("org/kgen/test/Fact", listOf(
            "fact:(I)I" to { code ->
                code.iconst(1); code.istore(1) // r = 1
                code.label("loop")
                code.iload(0); code.iconst(1)
                code.ifIcmple("done")           // if n <= 1, done
                code.iload(1); code.iload(0)
                code.imul(); code.istore(1)     // r = r * n
                code.iload(0); code.iconst(1)
                code.isub(); code.istore(0)     // n--
                code.goto("loop")
                code.label("done")
                code.iload(1)
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(1, it.callInt("fact", 0))
            assertEquals(1, it.callInt("fact", 1))
            assertEquals(2, it.callInt("fact", 2))
            assertEquals(6, it.callInt("fact", 3))
            assertEquals(24, it.callInt("fact", 4))
            assertEquals(120, it.callInt("fact", 5))
        }
    }

    @Test
    fun power() {
        // int pow(int base, int exp) { int r = 1; while (exp > 0) { r = r * base; exp--; } return r; }
        val classBytes = buildSimpleClass("org/kgen/test/Pow", listOf(
            "pow:(II)I" to { code ->
                code.iconst(1); code.istore(2) // r = 1
                code.label("loop")
                code.iload(1)
                code.ifle("done")               // if exp <= 0, done
                code.iload(2); code.iload(0)
                code.imul(); code.istore(2)     // r = r * base
                code.iload(1); code.iconst(1)
                code.isub(); code.istore(1)     // exp--
                code.goto("loop")
                code.label("done")
                code.iload(2)
                code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(1, it.callInt("pow", 2, 0))
            assertEquals(2, it.callInt("pow", 2, 1))
            assertEquals(8, it.callInt("pow", 2, 3))
            assertEquals(27, it.callInt("pow", 3, 3))
            assertEquals(1, it.callInt("pow", 5, 0))
        }
    }

    @Test
    fun gradeClassifier() {
        // Tests a function with many early returns (grade classification)
        val classBytes = buildSimpleClass("org/kgen/test/Grade", listOf(
            "grade:(I)I" to { code ->
                // if score >= 90 return 4, >= 80 return 3, >= 70 return 2, >= 60 return 1, else 0
                code.iload(0); code.iconst(90)
                code.ifIcmplt("below90")
                code.iconst(4); code.ireturn()
                code.label("below90")
                code.iload(0); code.iconst(80)
                code.ifIcmplt("below80")
                code.iconst(3); code.ireturn()
                code.label("below80")
                code.iload(0); code.iconst(70)
                code.ifIcmplt("below70")
                code.iconst(2); code.ireturn()
                code.label("below70")
                code.iload(0); code.iconst(60)
                code.ifIcmplt("below60")
                code.iconst(1); code.ireturn()
                code.label("below60")
                code.iconst(0); code.ireturn()
            }
        ))
        val code = compileAndLoad(listOf(classBytes))
        code.use {
            assertEquals(4, it.callInt("grade", 95))
            assertEquals(4, it.callInt("grade", 90))
            assertEquals(3, it.callInt("grade", 85))
            assertEquals(2, it.callInt("grade", 75))
            assertEquals(1, it.callInt("grade", 65))
            assertEquals(0, it.callInt("grade", 50))
            assertEquals(0, it.callInt("grade", 0))
        }
    }

    @Test
    fun classLayoutComputesCorrectSizes() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/Point")
        val superClass = cp.classEntry("java/lang/Object")

        val xField = FieldInfo(AccessFlags.PUBLIC, cp.utf8("x"), cp.utf8("I"), emptyList())
        val yField = FieldInfo(AccessFlags.PUBLIC, cp.utf8("y"), cp.utf8("I"), emptyList())

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(),
            listOf(xField, yField),
            emptyList(),
            emptyList(),
        ))

        val layout = ClassLayout.build(listOf(classBytes))
        // Header (8) + x (4) + y (4) = 16
        assertEquals(16L, layout.objectSize("org/kgen/test/Point"))
        assertEquals(8L, layout.fieldOffset("org/kgen/test/Point", "x"))
        assertEquals(12L, layout.fieldOffset("org/kgen/test/Point", "y"))
    }

    @Test
    fun classLayoutWithLongFields() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/BigObj")
        val superClass = cp.classEntry("java/lang/Object")

        val fields = listOf(
            FieldInfo(AccessFlags.PUBLIC, cp.utf8("a"), cp.utf8("J"), emptyList()),
            FieldInfo(AccessFlags.PUBLIC, cp.utf8("b"), cp.utf8("I"), emptyList()),
            FieldInfo(AccessFlags.PUBLIC, cp.utf8("c"), cp.utf8("J"), emptyList()),
        )

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), fields, emptyList(), emptyList(),
        ))

        val layout = ClassLayout.build(listOf(classBytes))
        // Header(8) + a:long(8) + b:int(4) + padding(4) + c:long(8) = 32
        assertTrue(layout.objectSize("org/kgen/test/BigObj") >= 24)
        assertEquals(8L, layout.fieldOffset("org/kgen/test/BigObj", "a"))
    }

    /** Recursive fibonacci — tests cross-function calls (self-recursion via relocation). */
    @Test
    fun recursiveFibonacci() {
        // static int fib(int n) {
        //     if (n <= 1) return n;
        //     return fib(n-1) + fib(n-2);
        // }
        val classBytes = buildSimpleClass("org/kgen/test/RecFib", listOf(
            "fib:(I)I" to { code ->
                code.iload(0)           // n
                code.iconst(1)
                code.ifIcmple("base")   // if n <= 1 goto base
                code.iload(0)
                code.iconst(1)
                code.isub()             // n-1
                code.invokestatic("org/kgen/test/RecFib", "fib", "(I)I")
                code.iload(0)
                code.iconst(2)
                code.isub()             // n-2
                code.invokestatic("org/kgen/test/RecFib", "fib", "(I)I")
                code.iadd()             // fib(n-1) + fib(n-2)
                code.ireturn()
                code.label("base")
                code.iload(0)
                code.ireturn()
            }
        ))
        val native = compileAndLoad(listOf(classBytes))
        native.use {
            assertEquals(0, it.callInt("fib", 0L))
            assertEquals(1, it.callInt("fib", 1L))
            assertEquals(1, it.callInt("fib", 2L))
            assertEquals(5, it.callInt("fib", 5L))
            assertEquals(55, it.callInt("fib", 10L))
        }
    }

    /** Cross-function calls between two methods in the same class. */
    @Test
    fun crossFunctionCalls() {
        // static int square(int x) { return x * x; }
        // static int sumOfSquares(int a, int b) { return square(a) + square(b); }
        val classBytes = buildSimpleClass("org/kgen/test/CrossCall", listOf(
            "square:(I)I" to { code ->
                code.iload(0)
                code.iload(0)
                code.imul()
                code.ireturn()
            },
            "sumOfSquares:(II)I" to { code ->
                code.iload(0)
                code.invokestatic("org/kgen/test/CrossCall", "square", "(I)I")
                code.iload(1)
                code.invokestatic("org/kgen/test/CrossCall", "square", "(I)I")
                code.iadd()
                code.ireturn()
            }
        ))
        val native = compileAndLoad(listOf(classBytes))
        native.use {
            assertEquals(4, it.callInt("square", 2L))
            assertEquals(25, it.callInt("square", 5L))
            assertEquals(13, it.callInt("sumOfSquares", 2L, 3L))    // 4 + 9
            assertEquals(41, it.callInt("sumOfSquares", 4L, 5L))    // 16 + 25
        }
    }

    @Test
    fun classLayoutSkipsStaticFields() {
        val cp = ConstantPoolBuilder()
        val thisClass = cp.classEntry("org/kgen/test/WithStatics")
        val superClass = cp.classEntry("java/lang/Object")

        val fields = listOf(
            FieldInfo(AccessFlags.PUBLIC or AccessFlags.STATIC, cp.utf8("count"), cp.utf8("I"), emptyList()),
            FieldInfo(AccessFlags.PUBLIC, cp.utf8("value"), cp.utf8("I"), emptyList()),
        )

        val classBytes = JvmClassWriter.write(ClassFile(
            0, 50, cp.build(),
            AccessFlags.PUBLIC or AccessFlags.SUPER,
            thisClass, superClass,
            emptyList(), fields, emptyList(), emptyList(),
        ))

        val layout = ClassLayout.build(listOf(classBytes))
        // Only 'value' is an instance field
        assertNull(layout.fieldOffset("org/kgen/test/WithStatics", "count"))
        assertEquals(8L, layout.fieldOffset("org/kgen/test/WithStatics", "value"))
    }

    // ── Float execution tests ──

    @Test
    fun floatAddition() {
        // float add(int a, int b) { return (float)a + (float)b; }
        val classBytes = buildSimpleClass("org/kgen/test/FloatAdd", listOf(
            "add:(II)F" to { code ->
                code.iload(0)
                code.i2f()
                code.iload(1)
                code.i2f()
                code.fadd()
                code.freturn()
            }
        ))
        val native = compileAndLoad(listOf(classBytes))
        native.use {
            assertEquals(7.0f, it.callFloat("add", 3, 4))
            assertEquals(0.0f, it.callFloat("add", 0, 0))
            assertEquals(-1.0f, it.callFloat("add", 1, -2))
        }
    }

    @Test
    fun floatArithmetic() {
        // float compute(int a, int b) { return ((float)a * (float)b) - (float)a; }
        val classBytes = buildSimpleClass("org/kgen/test/FloatArith", listOf(
            "compute:(II)F" to { code ->
                code.iload(0)
                code.i2f()
                code.iload(1)
                code.i2f()
                code.fmul()
                code.iload(0)
                code.i2f()
                code.fsub()
                code.freturn()
            }
        ))
        val native = compileAndLoad(listOf(classBytes))
        native.use {
            // 3*4 - 3 = 9.0
            assertEquals(9.0f, it.callFloat("compute", 3, 4))
            // 5*2 - 5 = 5.0
            assertEquals(5.0f, it.callFloat("compute", 5, 2))
            // 0*7 - 0 = 0.0
            assertEquals(0.0f, it.callFloat("compute", 0, 7))
        }
    }

    @Test
    fun floatToIntTruncation() {
        // int truncate(int a, int b) { return (int)((float)a / (float)b); }
        val classBytes = buildSimpleClass("org/kgen/test/FloatTrunc", listOf(
            "truncate:(II)I" to { code ->
                code.iload(0)
                code.i2f()
                code.iload(1)
                code.i2f()
                code.fdiv()
                code.f2i()
                code.ireturn()
            }
        ))
        val native = compileAndLoad(listOf(classBytes))
        native.use {
            assertEquals(3, it.callInt("truncate", 10, 3))
            assertEquals(2, it.callInt("truncate", 7, 3))
            assertEquals(0, it.callInt("truncate", 1, 3))
        }
    }

    @Test
    fun doubleArithmetic() {
        // double compute(int a, int b) { return (double)a / (double)b; }
        val classBytes = buildSimpleClass("org/kgen/test/DoubleArith", listOf(
            "compute:(II)D" to { code ->
                code.iload(0)
                code.i2d()
                code.iload(1)
                code.i2d()
                code.ddiv()
                code.dreturn()
            }
        ))
        val native = compileAndLoad(listOf(classBytes))
        native.use {
            assertEquals(2.5, it.callDouble("compute", 5, 2), 0.001)
            assertEquals(3.0, it.callDouble("compute", 9, 3), 0.001)
            assertEquals(0.5, it.callDouble("compute", 1, 2), 0.001)
        }
    }
}
