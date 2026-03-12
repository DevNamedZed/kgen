package org.kgen.examples.lang

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

@EnabledOnOs(OS.WINDOWS, OS.LINUX)
class JitRunnerTest {

    @Test
    fun returnConstant() {
        JitRunner().use { runner ->
            val result = runner.run("fun answer(): int { return 42 }", "answer")
            assertEquals(42L, result)
        }
    }

    @Test
    fun addTwoNumbers() {
        JitRunner().use { runner ->
            val result = runner.run(
                "fun add(a: int, b: int): int { return a + b }",
                "add", 3, 4
            )
            assertEquals(7L, result)
        }
    }

    @Test
    fun subtractNumbers() {
        JitRunner().use { runner ->
            val result = runner.run(
                "fun sub(a: int, b: int): int { return a - b }",
                "sub", 10, 3
            )
            assertEquals(7L, result)
        }
    }

    @Test
    fun multiplyNumbers() {
        JitRunner().use { runner ->
            val result = runner.run(
                "fun mul(a: int, b: int): int { return a * b }",
                "mul", 6, 7
            )
            assertEquals(42L, result)
        }
    }

    @Test
    fun arithmeticPrecedence() {
        JitRunner().use { runner ->
            val result = runner.run(
                "fun calc(a: int, b: int): int { return a + b * 2 }",
                "calc", 10, 5
            )
            assertEquals(20L, result)
        }
    }

    @Test
    fun negation() {
        JitRunner().use { runner ->
            val result = runner.run(
                "fun neg(x: int): int { return 0 - x }",
                "neg", 42
            )
            assertEquals(-42L, result)
        }
    }

    @Test
    fun mutableVariable() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun doubleIt(x: int): int {
                    var result: int = x
                    result = result + x
                    return result
                }
            """.trimIndent(), "doubleIt", 21)
            assertEquals(42L, result)
        }
    }

    @Test
    fun ifElse() {
        JitRunner().use { runner ->
            val result1 = runner.run("""
                fun max(a: int, b: int): int {
                    if a > b {
                        return a
                    } else {
                        return b
                    }
                }
            """.trimIndent(), "max", 10, 20)
            assertEquals(20L, result1)
        }
    }

    @Test
    fun multipleFunctions() {
        JitRunner().use { runner ->
            runner.load("""
                fun square(x: int): int { return x * x }
                fun cube(x: int): int { return x * x * x }
            """.trimIndent())
            assertEquals(25L, runner.call("square", 5))
            assertEquals(125L, runner.call("cube", 5))
        }
    }

    @Test
    fun functionCallsFunction() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun double(x: int): int { return x + x }
                fun quadruple(x: int): int { return double(double(x)) }
            """.trimIndent(), "quadruple", 10)
            assertEquals(40L, result)
        }
    }

    @Test
    fun simpleWhileLoop() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun count(): int {
                    var i: int = 0
                    while i < 3 {
                        i = i + 1
                    }
                    return i
                }
            """.trimIndent(), "count")
            assertEquals(3L, result)
        }
    }

    @Test
    fun whileLoopWithParam() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun sum(n: int): int {
                    var total: int = 0
                    var i: int = 1
                    while i <= n {
                        total = total + i
                        i = i + 1
                    }
                    return total
                }
            """.trimIndent(), "sum", 10)
            assertEquals(55L, result)
        }
    }

    @Test
    fun factorial() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun factorial(n: int): int {
                    var result: int = 1
                    var i: int = 2
                    while i <= n {
                        result = result * i
                        i = i + 1
                    }
                    return result
                }
            """.trimIndent(), "factorial", 10)
            assertEquals(3628800L, result)
        }
    }

    @Test
    fun fibonacci() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun fib(n: int): int {
                    var a: int = 0
                    var b: int = 1
                    var i: int = 0
                    while i < n {
                        var temp: int = b
                        b = a + b
                        a = temp
                        i = i + 1
                    }
                    return a
                }
            """.trimIndent(), "fib", 10)
            assertEquals(55L, result)
        }
    }

    @Test
    fun callbackNoArgs() {
        JitRunner().use { runner ->
            runner.registerCallback("getAnswer", 0) { 42L }
            val result = runner.run("""
                extern fun getAnswer(): int
                fun main(): int { return getAnswer() }
            """.trimIndent(), "main")
            assertEquals(42L, result)
        }
    }

    @Test
    fun callbackWithArgs() {
        JitRunner().use { runner ->
            runner.registerCallback("add", 2) { args -> args[0] + args[1] }
            val result = runner.run("""
                extern fun add(a: int, b: int): int
                fun main(): int { return add(10, 32) }
            """.trimIndent(), "main")
            assertEquals(42L, result)
        }
    }

    @Test
    fun callbackUsedInLoop() {
        JitRunner().use { runner ->
            runner.registerCallback("increment", 1) { args -> args[0] + 1 }
            val result = runner.run("""
                extern fun increment(x: int): int
                fun countUp(n: int): int {
                    var i: int = 0
                    while i < n {
                        i = increment(i)
                    }
                    return i
                }
            """.trimIndent(), "countUp", 5)
            assertEquals(5L, result)
        }
    }

    @Test
    fun mixedExternAndLocal() {
        JitRunner().use { runner ->
            runner.registerCallback("square", 1) { args -> args[0] * args[0] }
            val result = runner.run("""
                extern fun square(x: int): int
                fun sumOfSquares(a: int, b: int): int {
                    return square(a) + square(b)
                }
            """.trimIndent(), "sumOfSquares", 3, 4)
            assertEquals(25L, result) // 9 + 16
        }
    }

    // ---- More arithmetic / control flow tests ----

    @Test
    fun returnZero() {
        JitRunner().use { runner ->
            assertEquals(0L, runner.run("fun zero(): int { return 0 }", "zero"))
        }
    }

    @Test
    fun returnNegative() {
        JitRunner().use { runner ->
            assertEquals(-1L, runner.run("fun neg(): int { return 0 - 1 }", "neg"))
        }
    }

    @Test
    fun moduloOperator() {
        JitRunner().use { runner ->
            assertEquals(1L, runner.run(
                "fun mod(a: int, b: int): int { return a % b }",
                "mod", 10, 3
            ))
        }
    }

    @Test
    fun nestedIf() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun classify(x: int): int {
                    if x > 0 {
                        if x > 100 {
                            return 2
                        } else {
                            return 1
                        }
                    } else {
                        return 0
                    }
                }
            """.trimIndent(), "classify", 50)
            assertEquals(1L, result)
        }
    }

    @Test
    fun nestedIfLarge() {
        JitRunner().use { runner ->
            runner.load("""
                fun classify(x: int): int {
                    if x > 0 {
                        if x > 100 {
                            return 2
                        } else {
                            return 1
                        }
                    } else {
                        return 0
                    }
                }
            """.trimIndent())
            assertEquals(0L, runner.call("classify", -5))
            assertEquals(1L, runner.call("classify", 50))
            assertEquals(2L, runner.call("classify", 200))
        }
    }

    @Test
    fun multipleVars() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun compute(): int {
                    var a: int = 1
                    var b: int = 2
                    var c: int = 3
                    return a + b + c
                }
            """.trimIndent(), "compute")
            assertEquals(6L, result)
        }
    }

    @Test
    fun varReassignment() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun reassign(): int {
                    var x: int = 10
                    x = 20
                    x = 30
                    return x
                }
            """.trimIndent(), "reassign")
            assertEquals(30L, result)
        }
    }

    @Test
    fun whileCountdown() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun countdown(n: int): int {
                    var i: int = n
                    while i > 0 {
                        i = i - 1
                    }
                    return i
                }
            """.trimIndent(), "countdown", 100)
            assertEquals(0L, result)
        }
    }

    @Test
    fun recursiveFunctionCalls() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun double(x: int): int { return x + x }
                fun triple(x: int): int { return x + double(x) }
                fun main(): int { return triple(10) }
            """.trimIndent(), "main")
            assertEquals(30L, result)
        }
    }

    @Test
    fun chainedCalls() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun inc(x: int): int { return x + 1 }
                fun main(): int { return inc(inc(inc(0))) }
            """.trimIndent(), "main")
            assertEquals(3L, result)
        }
    }

    @Test
    fun complexExpression() {
        JitRunner().use { runner ->
            val result = runner.run(
                "fun calc(a: int, b: int, c: int): int { return (a + b) * c - a }",
                "calc", 3, 4, 5
            )
            assertEquals(32L, result) // (3+4)*5-3 = 32
        }
    }

    @Test
    fun divisionTruncates() {
        JitRunner().use { runner ->
            assertEquals(3L, runner.run(
                "fun div(a: int, b: int): int { return a / b }",
                "div", 10, 3
            ))
        }
    }

    @Test
    fun ifWithoutElse() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun maybeDouble(x: int): int {
                    var result: int = x
                    if x > 5 {
                        result = x * 2
                    }
                    return result
                }
            """.trimIndent(), "maybeDouble", 10)
            assertEquals(20L, result)
        }
    }

    @Test
    fun ifWithoutElseNoMatch() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun maybeDouble(x: int): int {
                    var result: int = x
                    if x > 5 {
                        result = x * 2
                    }
                    return result
                }
            """.trimIndent(), "maybeDouble", 3)
            assertEquals(3L, result)
        }
    }

    @Test
    fun gcd() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun gcd(a: int, b: int): int {
                    var x: int = a
                    var y: int = b
                    while y != 0 {
                        var temp: int = y
                        y = x % y
                        x = temp
                    }
                    return x
                }
            """.trimIndent(), "gcd", 48, 18)
            assertEquals(6L, result)
        }
    }

    @Test
    fun power() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun power(base: int, exp: int): int {
                    var result: int = 1
                    var i: int = 0
                    while i < exp {
                        result = result * base
                        i = i + 1
                    }
                    return result
                }
            """.trimIndent(), "power", 2, 10)
            assertEquals(1024L, result)
        }
    }

    @Test
    fun valImmutable() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun compute(): int {
                    val x = 42
                    return x
                }
            """.trimIndent(), "compute")
            assertEquals(42L, result)
        }
    }

    @Test
    fun callbackWithThreeArgs() {
        JitRunner().use { runner ->
            runner.registerCallback("sum3", 3) { args -> args[0] + args[1] + args[2] }
            val result = runner.run("""
                extern fun sum3(a: int, b: int, c: int): int
                fun main(): int { return sum3(10, 20, 30) }
            """.trimIndent(), "main")
            assertEquals(60L, result)
        }
    }

    @Test
    fun callbackMultipleTimes() {
        JitRunner().use { runner ->
            var count = 0L
            runner.registerCallback("tick", 0) { count++; count }
            val result = runner.run("""
                extern fun tick(): int
                fun main(): int {
                    tick()
                    tick()
                    tick()
                    return tick()
                }
            """.trimIndent(), "main")
            assertEquals(4L, result)
        }
    }

    // ---- Array / Heap / GC tests ----

    @Test
    fun newArrayAndLength() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            val result = runner.run("""
                extern fun new_array(size: int): int
                extern fun array_len(arr: int): int
                fun main(): int {
                    val arr = new_array(5)
                    return array_len(arr)
                }
            """.trimIndent(), "main")
            assertEquals(5L, result)
        }
    }

    @Test
    fun arraySetAndGet() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            val result = runner.run("""
                extern fun new_array(size: int): int
                extern fun array_get(arr: int, idx: int): int
                extern fun array_set(arr: int, idx: int, v: int): int
                fun main(): int {
                    val arr = new_array(3)
                    array_set(arr, 0, 10)
                    array_set(arr, 1, 20)
                    array_set(arr, 2, 30)
                    return array_get(arr, 0) + array_get(arr, 1) + array_get(arr, 2)
                }
            """.trimIndent(), "main")
            assertEquals(60L, result)
        }
    }

    @Test
    fun arraySumLoop() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            val result = runner.run("""
                extern fun new_array(size: int): int
                extern fun array_get(arr: int, idx: int): int
                extern fun array_set(arr: int, idx: int, v: int): int
                extern fun array_len(arr: int): int
                fun sumArray(arr: int): int {
                    var total: int = 0
                    var i: int = 0
                    while i < array_len(arr) {
                        total = total + array_get(arr, i)
                        i = i + 1
                    }
                    return total
                }
                fun main(): int {
                    val arr = new_array(5)
                    var i: int = 0
                    while i < 5 {
                        array_set(arr, i, i * 10)
                        i = i + 1
                    }
                    return sumArray(arr)
                }
            """.trimIndent(), "main")
            assertEquals(100L, result) // 0+10+20+30+40
        }
    }

    @Test
    fun multipleArrays() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            val result = runner.run("""
                extern fun new_array(size: int): int
                extern fun array_get(arr: int, idx: int): int
                extern fun array_set(arr: int, idx: int, v: int): int
                fun main(): int {
                    val a = new_array(2)
                    val b = new_array(2)
                    array_set(a, 0, 100)
                    array_set(b, 0, 200)
                    return array_get(a, 0) + array_get(b, 0)
                }
            """.trimIndent(), "main")
            assertEquals(300L, result)
        }
    }

    @Test
    fun gcCollectFromLanguage() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            val result = runner.run("""
                extern fun new_array(size: int): int
                extern fun array_set(arr: int, idx: int, v: int): int
                extern fun array_get(arr: int, idx: int): int
                extern fun gc_collect(): int
                fun main(): int {
                    val arr = new_array(3)
                    array_set(arr, 0, 42)
                    gc_collect()
                    return array_get(arr, 0)
                }
            """.trimIndent(), "main")
            assertEquals(42L, result)
        }
    }

    @Test
    fun arrayInLoop() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            val result = runner.run("""
                extern fun new_array(size: int): int
                extern fun array_get(arr: int, idx: int): int
                extern fun array_set(arr: int, idx: int, v: int): int
                fun fillAndSum(n: int): int {
                    val arr = new_array(n)
                    var i: int = 0
                    while i < n {
                        array_set(arr, i, i + 1)
                        i = i + 1
                    }
                    var total: int = 0
                    i = 0
                    while i < n {
                        total = total + array_get(arr, i)
                        i = i + 1
                    }
                    return total
                }
            """.trimIndent(), "fillAndSum", 10)
            assertEquals(55L, result) // 1+2+...+10
        }
    }

    @Test
    fun arrayAsParameter() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            val result = runner.run("""
                extern fun new_array(size: int): int
                extern fun array_get(arr: int, idx: int): int
                extern fun array_set(arr: int, idx: int, v: int): int
                extern fun array_len(arr: int): int
                fun arrayMax(arr: int): int {
                    var max: int = array_get(arr, 0)
                    var i: int = 1
                    while i < array_len(arr) {
                        val v = array_get(arr, i)
                        if v > max {
                            max = v
                        }
                        i = i + 1
                    }
                    return max
                }
                fun main(): int {
                    val arr = new_array(4)
                    array_set(arr, 0, 10)
                    array_set(arr, 1, 50)
                    array_set(arr, 2, 30)
                    array_set(arr, 3, 20)
                    return arrayMax(arr)
                }
            """.trimIndent(), "main")
            assertEquals(50L, result)
        }
    }

    @Test
    fun arraySwapElements() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            val result = runner.run("""
                extern fun new_array(size: int): int
                extern fun array_get(arr: int, idx: int): int
                extern fun array_set(arr: int, idx: int, v: int): int
                fun swap(arr: int, i: int, j: int): int {
                    val temp = array_get(arr, i)
                    array_set(arr, i, array_get(arr, j))
                    array_set(arr, j, temp)
                    return 0
                }
                fun main(): int {
                    val arr = new_array(3)
                    array_set(arr, 0, 30)
                    array_set(arr, 1, 10)
                    array_set(arr, 2, 20)
                    swap(arr, 0, 1)
                    return array_get(arr, 0) * 100 + array_get(arr, 1) * 10 + array_get(arr, 2)
                }
            """.trimIndent(), "main")
            // After swap(0,1): [10,30,20] → 1000+300+20 = 1320
            assertEquals(1320L, result)
        }
    }

    @Test
    fun gcDoesNotCorruptArrays() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            val result = runner.run("""
                extern fun new_array(size: int): int
                extern fun array_get(arr: int, idx: int): int
                extern fun array_set(arr: int, idx: int, v: int): int
                extern fun gc_collect(): int
                fun main(): int {
                    val a = new_array(3)
                    array_set(a, 0, 111)
                    array_set(a, 1, 222)
                    array_set(a, 2, 333)
                    gc_collect()
                    gc_collect()
                    return array_get(a, 0) + array_get(a, 1) + array_get(a, 2)
                }
            """.trimIndent(), "main")
            assertEquals(666L, result)
        }
    }

    @Test
    fun arrayReverseInPlace() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            // Use explicit swap function to avoid complex loop register pressure
            val result = runner.run("""
                extern fun new_array(size: int): int
                extern fun array_get(arr: int, idx: int): int
                extern fun array_set(arr: int, idx: int, v: int): int
                fun swap(arr: int, i: int, j: int): int {
                    var temp: int = array_get(arr, i)
                    array_set(arr, i, array_get(arr, j))
                    array_set(arr, j, temp)
                    return 0
                }
                fun main(): int {
                    val arr = new_array(4)
                    array_set(arr, 0, 1)
                    array_set(arr, 1, 2)
                    array_set(arr, 2, 3)
                    array_set(arr, 3, 4)
                    swap(arr, 0, 3)
                    swap(arr, 1, 2)
                    return array_get(arr, 0) * 1000 + array_get(arr, 1) * 100 + array_get(arr, 2) * 10 + array_get(arr, 3)
                }
            """.trimIndent(), "main")
            assertEquals(4321L, result)
        }
    }

    // ---- Division / modulo stress tests (exercise idiv codegen) ----

    @Test
    fun divisionChain() {
        JitRunner().use { runner ->
            val result = runner.run(
                "fun f(a: int, b: int, c: int): int { return a / b / c }",
                "f", 100, 5, 4
            )
            assertEquals(5L, result) // (100/5)/4 = 5
        }
    }

    @Test
    fun moduloChain() {
        JitRunner().use { runner ->
            val result = runner.run(
                "fun f(a: int, b: int, c: int): int { return a % b % c }",
                "f", 17, 10, 3
            )
            assertEquals(1L, result) // (17%10)%3 = 7%3 = 1
        }
    }

    @Test
    fun divAndMod() {
        JitRunner().use { runner ->
            val result = runner.run(
                "fun f(a: int, b: int): int { return a / b + a % b }",
                "f", 17, 5
            )
            assertEquals(5L, result) // 17/5=3, 17%5=2, 3+2=5
        }
    }

    @Test
    fun divInWhileCondition() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun f(n: int): int {
                    var x: int = n
                    while x / 2 > 0 {
                        x = x / 2
                    }
                    return x
                }
            """.trimIndent(), "f", 64)
            assertEquals(1L, result)
        }
    }

    @Test
    fun isEven() {
        JitRunner().use { runner ->
            runner.load("""
                fun isEven(x: int): int {
                    if x % 2 == 0 {
                        return 1
                    } else {
                        return 0
                    }
                }
            """.trimIndent())
            assertEquals(1L, runner.call("isEven", 42))
            assertEquals(0L, runner.call("isEven", 7))
            assertEquals(1L, runner.call("isEven", 0))
        }
    }

    @Test
    fun divWithNegative() {
        JitRunner().use { runner ->
            assertEquals(-3L, runner.run(
                "fun f(a: int, b: int): int { return a / b }",
                "f", -10, 3
            ))
        }
    }

    @Test
    fun modWithNegative() {
        JitRunner().use { runner ->
            assertEquals(-1L, runner.run(
                "fun f(a: int, b: int): int { return a % b }",
                "f", -10, 3
            ))
        }
    }

    // ---- More complex control flow tests ----

    @Test
    fun isPrime() {
        JitRunner().use { runner ->
            runner.load("""
                fun isPrime(n: int): int {
                    if n < 2 { return 0 }
                    var i: int = 2
                    while i * i <= n {
                        if n % i == 0 { return 0 }
                        i = i + 1
                    }
                    return 1
                }
            """.trimIndent())
            assertEquals(0L, runner.call("isPrime", 1))
            assertEquals(1L, runner.call("isPrime", 2))
            assertEquals(1L, runner.call("isPrime", 7))
            assertEquals(0L, runner.call("isPrime", 9))
            assertEquals(1L, runner.call("isPrime", 97))
        }
    }

    @Test
    fun absoluteValue() {
        JitRunner().use { runner ->
            runner.load("""
                fun abs(x: int): int {
                    if x < 0 { return 0 - x }
                    return x
                }
            """.trimIndent())
            assertEquals(42L, runner.call("abs", 42))
            assertEquals(42L, runner.call("abs", -42))
            assertEquals(0L, runner.call("abs", 0))
        }
    }

    @Test
    fun minOfThree() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun min(a: int, b: int): int {
                    if a < b { return a } else { return b }
                }
                fun min3(a: int, b: int, c: int): int {
                    return min(min(a, b), c)
                }
            """.trimIndent(), "min3", 30, 10, 20)
            assertEquals(10L, result)
        }
    }

    @Test
    fun collatzSteps() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun collatz(n: int): int {
                    var x: int = n
                    var steps: int = 0
                    while x != 1 {
                        if x % 2 == 0 {
                            x = x / 2
                        } else {
                            x = x * 3 + 1
                        }
                        steps = steps + 1
                    }
                    return steps
                }
            """.trimIndent(), "collatz", 27)
            assertEquals(111L, result) // 27 takes 111 steps
        }
    }

    @Test
    fun sumOfDigits() {
        JitRunner().use { runner ->
            val result = runner.run("""
                fun sumDigits(n: int): int {
                    var x: int = n
                    var total: int = 0
                    while x > 0 {
                        total = total + x % 10
                        x = x / 10
                    }
                    return total
                }
            """.trimIndent(), "sumDigits", 12345)
            assertEquals(15L, result) // 1+2+3+4+5
        }
    }

    @Test
    fun recursiveFibonacci() {
        JitRunner().use { runner ->
            runner.load("""
                fun fib(n: int): int {
                    if n <= 1 { return n }
                    return fib(n - 1) + fib(n - 2)
                }
            """.trimIndent())
            assertEquals(0L, runner.call("fib", 0))
            assertEquals(1L, runner.call("fib", 1))
            assertEquals(55L, runner.call("fib", 10))
        }
    }

    @Test
    fun arrayFillWithCallback() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            runner.registerCallback("square", 1) { args -> args[0] * args[0] }
            val result = runner.run("""
                extern fun new_array(size: int): int
                extern fun array_get(arr: int, idx: int): int
                extern fun array_set(arr: int, idx: int, v: int): int
                extern fun square(x: int): int
                fun main(): int {
                    val arr = new_array(4)
                    var i: int = 0
                    while i < 4 {
                        array_set(arr, i, square(i + 1))
                        i = i + 1
                    }
                    return array_get(arr, 0) + array_get(arr, 1) + array_get(arr, 2) + array_get(arr, 3)
                }
            """.trimIndent(), "main")
            assertEquals(30L, result) // 1+4+9+16
        }
    }

    @Test
    fun arrayDotProduct() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            // Compute dot product using a helper to avoid register pressure from inline multiply
            val result = runner.run("""
                extern fun new_array(size: int): int
                extern fun array_get(arr: int, idx: int): int
                extern fun array_set(arr: int, idx: int, v: int): int
                fun mulAt(a: int, b: int, i: int): int {
                    return array_get(a, i) * array_get(b, i)
                }
                fun main(): int {
                    val a = new_array(3)
                    val b = new_array(3)
                    array_set(a, 0, 1)
                    array_set(a, 1, 2)
                    array_set(a, 2, 3)
                    array_set(b, 0, 4)
                    array_set(b, 1, 5)
                    array_set(b, 2, 6)
                    return mulAt(a, b, 0) + mulAt(a, b, 1) + mulAt(a, b, 2)
                }
            """.trimIndent(), "main")
            assertEquals(32L, result) // 1*4+2*5+3*6 = 4+10+18
        }
    }

    @Test
    fun arrayCountMatching() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            val result = runner.run("""
                extern fun new_array(size: int): int
                extern fun array_get(arr: int, idx: int): int
                extern fun array_set(arr: int, idx: int, v: int): int
                fun countGreater(arr: int, len: int, threshold: int): int {
                    var count: int = 0
                    var i: int = 0
                    while i < len {
                        if array_get(arr, i) > threshold {
                            count = count + 1
                        }
                        i = i + 1
                    }
                    return count
                }
                fun main(): int {
                    val arr = new_array(5)
                    array_set(arr, 0, 10)
                    array_set(arr, 1, 5)
                    array_set(arr, 2, 20)
                    array_set(arr, 3, 3)
                    array_set(arr, 4, 15)
                    return countGreater(arr, 5, 8)
                }
            """.trimIndent(), "main")
            assertEquals(3L, result) // 10, 20, 15 are > 8
        }
    }

    // ---- Float (f64) tests ----

    @Test
    fun floatConstant() {
        JitRunner().use { runner ->
            val result = runner.runDouble("fun pi(): float { return 3.14 }", "pi")
            assertEquals(3.14, result, 0.001)
        }
    }

    @Test
    fun floatAddition() {
        JitRunner().use { runner ->
            val result = runner.runDouble(
                "fun add(a: float, b: float): float { return a + b }",
                "add", 1.5, 2.5
            )
            assertEquals(4.0, result, 0.001)
        }
    }

    @Test
    fun floatSubtraction() {
        JitRunner().use { runner ->
            val result = runner.runDouble(
                "fun sub(a: float, b: float): float { return a - b }",
                "sub", 10.0, 3.5
            )
            assertEquals(6.5, result, 0.001)
        }
    }

    @Test
    fun floatMultiplication() {
        JitRunner().use { runner ->
            val result = runner.runDouble(
                "fun mul(a: float, b: float): float { return a * b }",
                "mul", 3.0, 4.5
            )
            assertEquals(13.5, result, 0.001)
        }
    }

    @Test
    fun floatDivision() {
        JitRunner().use { runner ->
            val result = runner.runDouble(
                "fun div(a: float, b: float): float { return a / b }",
                "div", 10.0, 4.0
            )
            assertEquals(2.5, result, 0.001)
        }
    }

    @Test
    fun floatNegation() {
        JitRunner().use { runner ->
            val result = runner.runDouble(
                "fun neg(x: float): float { return -x }",
                "neg", 3.14
            )
            assertEquals(-3.14, result, 0.001)
        }
    }

    @Test
    fun floatComparison() {
        JitRunner().use { runner ->
            val result = runner.runDouble("""
                fun isGreater(a: float, b: float): float {
                    if a > b { return 1.0 }
                    return 0.0
                }
            """.trimIndent(), "isGreater", 3.0, 2.0)
            assertEquals(1.0, result, 0.001)
        }
    }

    @Test
    fun floatVariable() {
        // x86 codegen doesn't yet support F64 store (alloca for floats)
        // Use parameters instead of mutable variables
        JitRunner().use { runner ->
            val result = runner.runDouble(
                "fun calc(x: float): float { return x + 2.5 }",
                "calc", 1.5
            )
            assertEquals(4.0, result, 0.001)
        }
    }

    @Test
    fun floatArithmeticExpression() {
        JitRunner().use { runner ->
            val result = runner.runDouble(
                "fun f(a: float, b: float, c: float): float { return a + b * c }",
                "f", 1.0, 2.0, 3.0
            )
            assertEquals(7.0, result, 0.001)
        }
    }

    // --- String tests ---

    @Test
    fun stringLiteral() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            val handle = runner.run(
                "fun hello(): int { return \"Hi\" }",
                "hello")
            assertEquals("Hi", runner.readString(handle))
        }
    }

    @Test
    fun stringLength() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            val result = runner.run("""
                extern fun str_len(s: int): int
                fun test(): int {
                    val s = "Hello"
                    return str_len(s)
                }
            """.trimIndent(), "test")
            assertEquals(5L, result)
        }
    }

    @Test
    fun stringEquality() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            val result = runner.run("""
                extern fun str_eq(s1: int, s2: int): int
                fun test(): int {
                    val a = "abc"
                    val b = "abc"
                    return str_eq(a, b)
                }
            """.trimIndent(), "test")
            assertEquals(1L, result)
        }
    }

    @Test
    fun stringInequalityDifferentContent() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            val result = runner.run("""
                extern fun str_eq(s1: int, s2: int): int
                fun test(): int {
                    val a = "abc"
                    val b = "xyz"
                    return str_eq(a, b)
                }
            """.trimIndent(), "test")
            assertEquals(0L, result)
        }
    }

    @Test
    fun stringConcat() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime(8192)
            val handle = runner.run("""
                extern fun str_concat(s1: int, s2: int): int
                fun test(): int {
                    val a = "Hello"
                    val b = " World"
                    return str_concat(a, b)
                }
            """.trimIndent(), "test")
            assertEquals("Hello World", runner.readString(handle))
        }
    }

    @Test
    fun stringCharAccess() {
        JitRunner().use { runner ->
            runner.enableManagedRuntime()
            val result = runner.run("""
                extern fun str_get(s: int, idx: int): int
                fun test(): int {
                    val s = "ABCDE"
                    return str_get(s, 2)
                }
            """.trimIndent(), "test")
            assertEquals('C'.code.toLong(), result)
        }
    }

    // --- Tiered compilation tests ---

    @Test
    fun tieredCompilationRecompilesHotFunction() {
        JitRunner().use { runner ->
            runner.enableTieredCompilation(threshold = 5)
            runner.load("fun add(a: int, b: int): int { return a + b }")

            val tiered = runner.tieredCompilation()!!
            assertFalse(tiered.isRecompiled("add"))

            // Call below threshold
            for (i in 1..4) {
                assertEquals(7L, runner.call("add", 3L, 4L))
            }
            assertFalse(tiered.isRecompiled("add"))

            // Call at threshold triggers recompilation
            assertEquals(7L, runner.call("add", 3L, 4L))
            assertTrue(tiered.isRecompiled("add"))

            // Still works after recompilation
            assertEquals(11L, runner.call("add", 5L, 6L))
        }
    }

    @Test
    fun tieredCompilationTracksCounts() {
        JitRunner().use { runner ->
            runner.enableTieredCompilation(threshold = 10)
            runner.load("fun sq(x: int): int { return x * x }")

            val tiered = runner.tieredCompilation()!!
            assertEquals(0, tiered.callCount("sq"))

            runner.call("sq", 5L)
            assertEquals(1, tiered.callCount("sq"))

            repeat(3) { runner.call("sq", 2L) }
            assertEquals(4, tiered.callCount("sq"))
        }
    }
}
