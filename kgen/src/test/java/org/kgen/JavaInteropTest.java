package org.kgen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.kgen.ir.*;
import org.kgen.ir.build.IrBuilder;
import org.kgen.ir.target.Arch;
import org.kgen.ir.target.Target;
import org.kgen.ir.verify.IrVerifier;
import org.kgen.ir.verify.VerificationResult;
import org.kgen.ir.text.IrPrinter;
import org.kgen.pass.*;

import java.util.List;
import java.util.Set;

/**
 * Tests that the kgen public API is usable from pure Java code.
 * Verifies @JvmStatic, @JvmOverloads, and general API ergonomics.
 */
public class JavaInteropTest {

    // Type constants for readability
    private static final Type I32 = Type.I32.INSTANCE;
    private static final Type I64 = Type.I64.INSTANCE;
    private static final Type I8 = Type.I8.INSTANCE;
    private static final Type F32 = Type.F32.INSTANCE;
    private static final Type F64 = Type.F64.INSTANCE;
    private static final Type VOID = Type.Void.INSTANCE;

    @Test
    void createModuleWithFunction() {
        var builder = new IrBuilder("test", Target.x86_64());
        var params = builder.createFunction("add",
            List.of(new Param("a", I32), new Param("b", I32)), I32);

        builder.appendBlock("entry");
        // @JvmOverloads allows omitting nuw/nsw defaults
        Value result = builder.add(params.get(0), params.get(1));
        builder.ret(result);
        builder.finalizeFunction();

        org.kgen.ir.Module module = builder.build();
        assertEquals(1, module.getFunctions().size());
        assertEquals("add", module.getFunctions().get(0).getName());
    }

    @Test
    void typeFactoryMethods() {
        assertNotNull(I32);
        assertNotNull(I64);
        assertNotNull(I8);
        assertNotNull(F32);
        assertNotNull(F64);
        assertNotNull(VOID);

        Type ptr = Type.pointer(I32);
        assertNotNull(ptr);

        Type vec = Type.vector(I32, 4);
        assertNotNull(vec);

        Type arr = Type.array(I32, 10);
        assertNotNull(arr);

        Type funcType = Type.function(List.of(I32, I32), I32);
        assertNotNull(funcType);

        Type structType = Type.struct("point", List.of(F32, F32));
        assertNotNull(structType);
    }

    @Test
    void constantFactoryMethods() {
        var c32 = new Constant.I32(42);
        assertEquals(42, c32.getValue());

        var c64 = new Constant.I64(100L);
        assertEquals(100L, c64.getValue());

        var cf32 = new Constant.F32(3.14f);
        assertEquals(3.14f, cf32.getValue(), 0.001f);

        var cf64 = new Constant.F64(2.718);
        assertEquals(2.718, cf64.getValue(), 0.001);

        var ctrue = new Constant.I1(true);
        assertTrue(ctrue.getValue());
    }

    @Test
    void targetCreation() {
        Target x86 = Target.x86_64();
        assertNotNull(x86);
        assertEquals(Arch.X86_64, x86.getArch());

        Target arm = Target.arm64();
        assertEquals(Arch.ARM64, arm.getArch());

        Target wasm = Target.wasm();
        assertEquals(Arch.WASM32, wasm.getArch());

        Target riscv = Target.riscv64();
        assertEquals(Arch.RISCV64, riscv.getArch());

        Target jvm = Target.jvm();
        assertEquals(Arch.JVM, jvm.getArch());
    }

    @Test
    void buildAndVerifyModule() {
        var builder = new IrBuilder("verify_test", Target.x86_64());
        var params = builder.createFunction("identity",
            List.of(new Param("x", I32)), I32);
        builder.appendBlock("entry");
        builder.ret(params.get(0));
        builder.finalizeFunction();

        org.kgen.ir.Module module = builder.build();
        var verifier = new IrVerifier();
        VerificationResult result = verifier.verify(module);
        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void printModule() {
        var builder = new IrBuilder("print_test", Target.x86_64());
        var params = builder.createFunction("square",
            List.of(new Param("x", I32)), I32);
        builder.appendBlock("entry");
        Value squared = builder.mul(params.get(0), params.get(0));
        builder.ret(squared);
        builder.finalizeFunction();

        org.kgen.ir.Module module = builder.build();
        var printer = new IrPrinter();
        String ir = printer.print(module);
        assertNotNull(ir);
        assertTrue(ir.contains("square"));
        assertTrue(ir.contains("i32"));
    }

    @Test
    void arithmeticInstructions() {
        var builder = new IrBuilder("arith", Target.x86_64());
        var params = builder.createFunction("compute",
            List.of(new Param("a", I32), new Param("b", I32)), I32);
        builder.appendBlock("entry");

        Value a = params.get(0);
        Value b = params.get(1);

        Value sum = builder.add(a, b);
        Value diff = builder.sub(sum, b);
        Value prod = builder.mul(diff, new Constant.I32(2));
        builder.ret(prod);
        builder.finalizeFunction();

        org.kgen.ir.Module module = builder.build();
        IrFunction fn = module.getFunctions().get(0);
        assertFalse(fn.getBlocks().isEmpty());
    }

    @Test
    void controlFlow() {
        var builder = new IrBuilder("cf", Target.x86_64());
        var params = builder.createFunction("abs",
            List.of(new Param("x", I32)), I32);

        builder.appendBlock("entry");
        Value cmp = builder.icmp(ICmpPredicate.SGE, params.get(0), new Constant.I32(0));
        builder.condBr(cmp, new BlockRef("positive"), new BlockRef("negative"));

        builder.appendBlock("positive");
        builder.ret(params.get(0));

        builder.appendBlock("negative");
        Value negated = builder.neg(params.get(0));
        builder.ret(negated);

        builder.finalizeFunction();
        org.kgen.ir.Module module = builder.build();
        IrFunction fn = module.getFunctions().get(0);
        assertEquals(3, fn.getBlocks().size());
    }

    @Test
    void declareFunctionExternal() {
        var builder = new IrBuilder("extern", Target.x86_64());
        builder.declareFunction("printf",
            List.of(new Param("fmt", Type.pointer(I8))), I32);

        var params = builder.createFunction("main", List.of(), I32);
        builder.appendBlock("entry");
        builder.ret(new Constant.I32(0));
        builder.finalizeFunction();

        org.kgen.ir.Module module = builder.build();
        assertEquals(2, module.getFunctions().size());
        assertTrue(module.getFunctions().get(0).isExternal());
    }

    @Test
    void passPipeline() {
        var builder = new IrBuilder("pipeline", Target.x86_64());
        var params = builder.createFunction("test",
            List.of(new Param("x", I32)), I32);
        builder.appendBlock("entry");
        Value result = builder.add(params.get(0), new Constant.I32(0));
        builder.ret(result);
        builder.finalizeFunction();

        org.kgen.ir.Module module = builder.build();
        var pipeline = new PassPipeline();
        pipeline.add(new ConstantFolding());
        pipeline.add(new DeadCodeElimination());
        org.kgen.ir.Module optimized = pipeline.execute(module);
        assertNotNull(optimized);
    }

    @Test
    void vectorTypes() {
        Type vecI32x4 = Type.vector(I32, 4);
        assertTrue(vecI32x4 instanceof Type.Vector);
        var vec = (Type.Vector) vecI32x4;
        assertEquals(4, vec.getLanes());
        assertEquals(I32, vec.getElement());
        assertFalse(vec.getScalable());

        Type scalableVec = Type.vector(F32, 4, true);
        assertTrue(((Type.Vector) scalableVec).getScalable());
    }

    @Test
    void functionAttributes() {
        var builder = new IrBuilder("attrs", Target.x86_64());
        var params = builder.createFunction("hot_func",
            List.of(new Param("x", I32)), I32,
            Linkage.EXTERNAL, Visibility.DEFAULT, CallingConvention.C,
            Set.of(FnAttribute.HOT, FnAttribute.NOUNWIND));
        builder.appendBlock("entry");
        builder.ret(params.get(0));
        builder.finalizeFunction();

        org.kgen.ir.Module module = builder.build();
        IrFunction fn = module.getFunctions().get(0);
        assertTrue(fn.getAttributes().contains(FnAttribute.HOT));
        assertTrue(fn.getAttributes().contains(FnAttribute.NOUNWIND));
    }

    @Test
    void icmpPredicates() {
        assertNotNull(ICmpPredicate.EQ);
        assertNotNull(ICmpPredicate.NE);
        assertNotNull(ICmpPredicate.SGT);
        assertNotNull(ICmpPredicate.SGE);
        assertNotNull(ICmpPredicate.SLT);
        assertNotNull(ICmpPredicate.SLE);
        assertNotNull(ICmpPredicate.UGT);
        assertNotNull(ICmpPredicate.UGE);
        assertNotNull(ICmpPredicate.ULT);
        assertNotNull(ICmpPredicate.ULE);
    }

    @Test
    void callInstruction() {
        var builder = new IrBuilder("call_test", Target.x86_64());
        builder.declareFunction("helper", List.of(new Param("x", I32)), I32);

        var params = builder.createFunction("caller",
            List.of(new Param("x", I32)), I32);
        builder.appendBlock("entry");
        // @JvmOverloads allows omitting CallingConvention default
        Value result = builder.call("helper", List.of((Value) params.get(0)), I32);
        assertNotNull(result);
        builder.ret(result);
        builder.finalizeFunction();

        org.kgen.ir.Module module = builder.build();
        assertEquals(2, module.getFunctions().size());
    }

    @Test
    void floatingPointInstructions() {
        var builder = new IrBuilder("fp", Target.x86_64());
        var params = builder.createFunction("fma",
            List.of(new Param("a", F64), new Param("b", F64), new Param("c", F64)), F64);
        builder.appendBlock("entry");

        // @JvmOverloads allows omitting FastMathFlags default
        Value prod = builder.fmul(params.get(0), params.get(1));
        Value result = builder.fadd(prod, params.get(2));
        builder.ret(result);
        builder.finalizeFunction();

        org.kgen.ir.Module module = builder.build();
        var verifier = new IrVerifier();
        VerificationResult vr = verifier.verify(module);
        assertTrue(vr.isValid());
    }

    @Test
    void memoryInstructions() {
        var builder = new IrBuilder("mem", Target.x86_64());
        var params = builder.createFunction("loadStore",
            List.of(new Param("ptr", Type.pointer(I32))), I32);
        builder.appendBlock("entry");

        // @JvmOverloads allows omitting align/volatile/ordering defaults
        Value loaded = builder.load(I32, params.get(0));
        Value incremented = builder.add(loaded, new Constant.I32(1));
        builder.store(incremented, params.get(0));
        builder.ret(loaded);
        builder.finalizeFunction();

        org.kgen.ir.Module module = builder.build();
        assertFalse(module.getFunctions().isEmpty());
    }
}
