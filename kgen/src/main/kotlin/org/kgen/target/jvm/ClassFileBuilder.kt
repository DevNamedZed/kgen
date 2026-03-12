package org.kgen.target.jvm

import org.kgen.target.jvm.asm.JvmAssembler

/**
 * Fluent builder for constructing JVM class files programmatically.
 *
 * Wraps [ConstantPoolBuilder] and [JvmAssembler] to provide an ergonomic API
 * for generating .class bytes without touching raw constant pool indices.
 *
 * ```java
 * var builder = new ClassFileBuilder("com/example/Hello");
 * builder.method("greet", "()Ljava/lang/String;", AccessFlags.PUBLIC | AccessFlags.STATIC, code -> {
 *     code.ldc("Hello, World!");
 *     code.areturn();
 * });
 * byte[] classBytes = builder.toBytes();
 * ```
 */
class ClassFileBuilder(private val className: String) {

    private val cp = ConstantPoolBuilder()
    private val methods = mutableListOf<MethodInfo>()
    private val fields = mutableListOf<FieldInfo>()
    private val interfaces = mutableListOf<Int>()
    private var superClassName: String = "java/lang/Object"
    private var classFlags: Int = AccessFlags.PUBLIC or AccessFlags.SUPER
    private var majorVersion: Int = 50 // Java 6 (no StackMapTable required)
    private var minorVersion: Int = 0

    private val thisClassIdx by lazy { cp.classEntry(className) }
    private val superClassIdx by lazy { cp.classEntry(superClassName) }
    private val codeAttrName by lazy { cp.utf8("Code") }

    /**
     * Set the superclass. Defaults to "java/lang/Object".
     */
    fun superClass(name: String): ClassFileBuilder {
        superClassName = name
        return this
    }

    /**
     * Set class access flags. Defaults to PUBLIC | SUPER.
     */
    fun flags(flags: Int): ClassFileBuilder {
        classFlags = flags
        return this
    }

    /**
     * Set the class file version. Defaults to Java 21 (65.0).
     */
    fun version(major: Int, minor: Int = 0): ClassFileBuilder {
        majorVersion = major
        minorVersion = minor
        return this
    }

    /**
     * Add an interface to the class.
     */
    fun implement(interfaceName: String): ClassFileBuilder {
        interfaces.add(cp.classEntry(interfaceName))
        return this
    }

    /**
     * Add a field to the class.
     */
    fun field(name: String, descriptor: String, flags: Int = AccessFlags.PRIVATE): ClassFileBuilder {
        val nameIdx = cp.utf8(name)
        val descIdx = cp.utf8(descriptor)
        fields.add(FieldInfo(flags, nameIdx, descIdx, emptyList()))
        return this
    }

    /**
     * Add a method with bytecode emitted via the [CodeEmitter] callback.
     *
     * The callback receives a [CodeEmitter] that wraps [JvmAssembler] with
     * ergonomic helpers for field/method references using strings instead of
     * raw constant pool indices.
     *
     * ```java
     * builder.method("add", "(II)I", AccessFlags.PUBLIC | AccessFlags.STATIC, code -> {
     *     code.iload(0);
     *     code.iload(1);
     *     code.iadd();
     *     code.ireturn();
     * });
     * ```
     */
    fun method(name: String, descriptor: String, flags: Int, body: (CodeEmitter) -> Unit): ClassFileBuilder {
        val nameIdx = cp.utf8(name)
        val descIdx = cp.utf8(descriptor)

        val emitter = CodeEmitter(cp)
        body(emitter)
        val codeBytes = emitter.assembler.toByteArray()

        val codeAttr = buildCodeAttribute(
            maxStack = emitter.maxStack,
            maxLocals = emitter.maxLocals,
            code = codeBytes,
        )

        methods.add(MethodInfo(flags, nameIdx, descIdx, listOf(codeAttr)))
        return this
    }

    /**
     * Add an abstract or native method (no body).
     */
    fun method(name: String, descriptor: String, flags: Int): ClassFileBuilder {
        val nameIdx = cp.utf8(name)
        val descIdx = cp.utf8(descriptor)
        methods.add(MethodInfo(flags, nameIdx, descIdx, emptyList()))
        return this
    }

    /**
     * Build the [ClassFile] model.
     */
    fun build(): ClassFile {
        // Force lazy indices
        thisClassIdx
        superClassIdx

        return ClassFile(
            majorVersion = majorVersion,
            minorVersion = minorVersion,
            accessFlags = classFlags,
            constantPool = cp.build(),
            thisClass = thisClassIdx,
            superClass = superClassIdx,
            interfaces = interfaces,
            fields = fields,
            methods = methods,
            attributes = emptyList(),
        )
    }

    /**
     * Build and serialize to .class bytes.
     */
    fun toBytes(): ByteArray = JvmClassWriter.write(build())

    private fun buildCodeAttribute(maxStack: Int, maxLocals: Int, code: ByteArray): AttributeInfo {
        val data = java.io.ByteArrayOutputStream()
        val dos = java.io.DataOutputStream(data)
        dos.writeShort(maxStack)
        dos.writeShort(maxLocals)
        dos.writeInt(code.size)
        dos.write(code)
        dos.writeShort(0) // exception table length
        dos.writeShort(0) // attributes count
        dos.flush()
        return AttributeInfo(codeAttrName, data.toByteArray())
    }

    /**
     * Bytecode emitter for method bodies. Wraps [JvmAssembler] with
     * ergonomic string-based helpers for field/method references.
     *
     * All raw [JvmAssembler] methods are available via [asm], but prefer
     * the convenience methods which handle constant pool wiring automatically.
     *
     * ```java
     * code.getstatic("java/lang/System", "out", "Ljava/io/PrintStream;");
     * code.ldc("Hello!");
     * code.invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V");
     * code.return_();
     * ```
     */
    class CodeEmitter internal constructor(private val cp: ConstantPoolBuilder) {
        /** The underlying assembler. Use for raw bytecode emission. */
        val assembler = JvmAssembler()

        /** Maximum stack depth (auto-tracked). */
        var maxStack = 0
            private set

        /** Maximum local variable count. Set this to match your method's needs. */
        var maxLocals = 0

        private var currentStack = 0

        private fun stackPush(count: Int = 1) {
            currentStack += count
            if (currentStack > maxStack) maxStack = currentStack
        }

        private fun stackPop(count: Int = 1) {
            currentStack -= count
        }

        // -- Constants --

        fun aconstNull() { assembler.aconstNull(); stackPush() }
        fun iconst(value: Int) {
            when (value) {
                -1 -> assembler.iconstM1()
                0 -> assembler.iconst0()
                1 -> assembler.iconst1()
                2 -> assembler.iconst2()
                3 -> assembler.iconst3()
                4 -> assembler.iconst4()
                5 -> assembler.iconst5()
                in -128..127 -> assembler.bipush(value)
                in -32768..32767 -> assembler.sipush(value)
                else -> assembler.ldc(cp.integer(value))
            }
            stackPush()
        }

        fun lconst(value: Long) {
            when (value) {
                0L -> assembler.lconst0()
                1L -> assembler.lconst1()
                else -> assembler.ldc2w(cp.long(value))
            }
            stackPush(2)
        }

        fun fconst(value: Float) {
            when (value) {
                0.0f -> assembler.fconst0()
                1.0f -> assembler.fconst1()
                2.0f -> assembler.fconst2()
                else -> assembler.ldc(cp.float(value))
            }
            stackPush()
        }

        fun dconst(value: Double) {
            when (value) {
                0.0 -> assembler.dconst0()
                1.0 -> assembler.dconst1()
                else -> assembler.ldc2w(cp.double(value))
            }
            stackPush(2)
        }

        /** Load a string constant. */
        fun ldc(value: String) {
            val idx = cp.string(value)
            assembler.ldc(idx)
            stackPush()
        }

        /** Load an int constant from the constant pool. */
        fun ldcInt(value: Int) { iconst(value) }

        // -- Loads --

        fun iload(slot: Int) { assembler.iload(slot); trackLocal(slot, 1); stackPush() }
        fun lload(slot: Int) { assembler.lload(slot); trackLocal(slot, 2); stackPush(2) }
        fun fload(slot: Int) { assembler.fload(slot); trackLocal(slot, 1); stackPush() }
        fun dload(slot: Int) { assembler.dload(slot); trackLocal(slot, 2); stackPush(2) }
        fun aload(slot: Int) { assembler.aload(slot); trackLocal(slot, 1); stackPush() }

        // -- Stores --

        fun istore(slot: Int) { assembler.istore(slot); trackLocal(slot, 1); stackPop() }
        fun lstore(slot: Int) { assembler.lstore(slot); trackLocal(slot, 2); stackPop(2) }
        fun fstore(slot: Int) { assembler.fstore(slot); trackLocal(slot, 1); stackPop() }
        fun dstore(slot: Int) { assembler.dstore(slot); trackLocal(slot, 2); stackPop(2) }
        fun astore(slot: Int) { assembler.astore(slot); trackLocal(slot, 1); stackPop() }

        // -- Arithmetic --

        fun iadd() { assembler.iadd(); stackPop() }
        fun ladd() { assembler.ladd(); stackPop(2) }
        fun fadd() { assembler.fadd(); stackPop() }
        fun dadd() { assembler.dadd(); stackPop(2) }
        fun isub() { assembler.isub(); stackPop() }
        fun lsub() { assembler.lsub(); stackPop(2) }
        fun fsub() { assembler.fsub(); stackPop() }
        fun dsub() { assembler.dsub(); stackPop(2) }
        fun imul() { assembler.imul(); stackPop() }
        fun lmul() { assembler.lmul(); stackPop(2) }
        fun fmul() { assembler.fmul(); stackPop() }
        fun dmul() { assembler.dmul(); stackPop(2) }
        fun idiv() { assembler.idiv(); stackPop() }
        fun ldiv() { assembler.ldiv(); stackPop(2) }
        fun fdiv() { assembler.fdiv(); stackPop() }
        fun ddiv() { assembler.ddiv(); stackPop(2) }
        fun irem() { assembler.irem(); stackPop() }
        fun lrem() { assembler.lrem(); stackPop(2) }
        fun ineg() { assembler.ineg() }
        fun lneg() { assembler.lneg() }
        fun fneg() { assembler.fneg() }
        fun dneg() { assembler.dneg() }

        // -- Bitwise --

        fun iand() { assembler.iand(); stackPop() }
        fun land() { assembler.land(); stackPop(2) }
        fun ior() { assembler.ior(); stackPop() }
        fun lor() { assembler.lor(); stackPop(2) }
        fun ixor() { assembler.ixor(); stackPop() }
        fun lxor() { assembler.lxor(); stackPop(2) }
        fun ishl() { assembler.ishl(); stackPop() }
        fun lshl() { assembler.lshl(); stackPop() }
        fun ishr() { assembler.ishr(); stackPop() }
        fun lshr() { assembler.lshr(); stackPop() }
        fun iushr() { assembler.iushr(); stackPop() }
        fun lushr() { assembler.lushr(); stackPop() }

        // -- Conversions --

        fun i2l() { assembler.i2l(); stackPush() }
        fun i2f() { assembler.i2f() }
        fun i2d() { assembler.i2d(); stackPush() }
        fun l2i() { assembler.l2i(); stackPop() }
        fun l2f() { assembler.l2f(); stackPop() }
        fun l2d() { assembler.l2d() }
        fun f2i() { assembler.f2i() }
        fun f2l() { assembler.f2l(); stackPush() }
        fun f2d() { assembler.f2d(); stackPush() }
        fun d2i() { assembler.d2i(); stackPop() }
        fun d2l() { assembler.d2l() }
        fun d2f() { assembler.d2f(); stackPop() }
        fun i2b() { assembler.i2b() }
        fun i2c() { assembler.i2c() }
        fun i2s() { assembler.i2s() }

        // -- Stack --

        fun pop() { assembler.pop(); stackPop() }
        fun pop2() { assembler.pop2(); stackPop(2) }
        fun dup() { assembler.dup(); stackPush() }
        fun swap() { assembler.swap() }

        // -- Comparisons --

        fun lcmp() { assembler.lcmp(); stackPop(3) }
        fun fcmpl() { assembler.fcmpl(); stackPop() }
        fun fcmpg() { assembler.fcmpg(); stackPop() }
        fun dcmpl() { assembler.dcmpl(); stackPop(3) }
        fun dcmpg() { assembler.dcmpg(); stackPop(3) }

        // -- Branches --

        fun label(name: String) { assembler.label(name) }
        fun goto(label: String) { assembler.goto(label) }
        fun ifeq(label: String) { assembler.ifeq(label); stackPop() }
        fun ifne(label: String) { assembler.ifne(label); stackPop() }
        fun iflt(label: String) { assembler.iflt(label); stackPop() }
        fun ifge(label: String) { assembler.ifge(label); stackPop() }
        fun ifgt(label: String) { assembler.ifgt(label); stackPop() }
        fun ifle(label: String) { assembler.ifle(label); stackPop() }
        fun ifIcmpeq(label: String) { assembler.ifIcmpeq(label); stackPop(2) }
        fun ifIcmpne(label: String) { assembler.ifIcmpne(label); stackPop(2) }
        fun ifIcmplt(label: String) { assembler.ifIcmplt(label); stackPop(2) }
        fun ifIcmpge(label: String) { assembler.ifIcmpge(label); stackPop(2) }
        fun ifIcmpgt(label: String) { assembler.ifIcmpgt(label); stackPop(2) }
        fun ifIcmple(label: String) { assembler.ifIcmple(label); stackPop(2) }
        fun ifnull(label: String) { assembler.ifnull(label); stackPop() }
        fun ifnonnull(label: String) { assembler.ifnonnull(label); stackPop() }

        // -- Returns --

        fun ireturn() { assembler.ireturn(); stackPop() }
        fun lreturn() { assembler.lreturn(); stackPop(2) }
        fun freturn() { assembler.freturn(); stackPop() }
        fun dreturn() { assembler.dreturn(); stackPop(2) }
        fun areturn() { assembler.areturn(); stackPop() }
        fun return_() { assembler.return_() }

        // -- Field access (string-based, auto-wires constant pool) --

        /** Get a static field value. */
        fun getstatic(owner: String, name: String, descriptor: String) {
            val idx = cp.fieldRef(owner, name, descriptor)
            assembler.getstatic(idx)
            stackPush(descriptorSlots(descriptor))
        }

        /** Put a static field value. */
        fun putstatic(owner: String, name: String, descriptor: String) {
            val idx = cp.fieldRef(owner, name, descriptor)
            assembler.putstatic(idx)
            stackPop(descriptorSlots(descriptor))
        }

        /** Get an instance field value. */
        fun getfield(owner: String, name: String, descriptor: String) {
            val idx = cp.fieldRef(owner, name, descriptor)
            assembler.getfield(idx)
            stackPop() // objectref
            stackPush(descriptorSlots(descriptor))
        }

        /** Put an instance field value. */
        fun putfield(owner: String, name: String, descriptor: String) {
            val idx = cp.fieldRef(owner, name, descriptor)
            assembler.putfield(idx)
            stackPop(descriptorSlots(descriptor) + 1) // value + objectref
        }

        // -- Method invocation (string-based) --

        /** Invoke a static method. */
        fun invokestatic(owner: String, name: String, descriptor: String) {
            val idx = cp.methodRef(owner, name, descriptor)
            assembler.invokestatic(idx)
            stackPop(methodArgSlots(descriptor))
            val retSlots = methodReturnSlots(descriptor)
            if (retSlots > 0) stackPush(retSlots)
        }

        /** Invoke a virtual method. */
        fun invokevirtual(owner: String, name: String, descriptor: String) {
            val idx = cp.methodRef(owner, name, descriptor)
            assembler.invokevirtual(idx)
            stackPop(methodArgSlots(descriptor) + 1) // args + objectref
            val retSlots = methodReturnSlots(descriptor)
            if (retSlots > 0) stackPush(retSlots)
        }

        /** Invoke a special method (constructor, super, private). */
        fun invokespecial(owner: String, name: String, descriptor: String) {
            val idx = cp.methodRef(owner, name, descriptor)
            assembler.invokespecial(idx)
            stackPop(methodArgSlots(descriptor) + 1) // args + objectref
            val retSlots = methodReturnSlots(descriptor)
            if (retSlots > 0) stackPush(retSlots)
        }

        // -- Object creation --

        /** Create a new object (pushes uninitialized reference). */
        fun new_(className: String) {
            val idx = cp.classEntry(className)
            assembler.new_(idx)
            stackPush()
        }

        /** Create a new array of primitives. */
        fun newarray(atype: Int) {
            assembler.newarray(atype)
        }

        /** Create a new array of references. */
        fun anewarray(className: String) {
            val idx = cp.classEntry(className)
            assembler.anewarray(idx)
        }

        /** Check cast. */
        fun checkcast(className: String) {
            val idx = cp.classEntry(className)
            assembler.checkcast(idx)
        }

        /** Instance of check. */
        fun instanceof_(className: String) {
            val idx = cp.classEntry(className)
            assembler.instanceof_(idx)
        }

        // -- Misc --

        fun arraylength() { assembler.arraylength() }
        fun athrow() { assembler.athrow(); stackPop() }
        fun iinc(slot: Int, increment: Int) { assembler.iinc(slot, increment) }

        // -- Helpers --

        private fun trackLocal(slot: Int, size: Int) {
            val needed = slot + size
            if (needed > maxLocals) maxLocals = needed
        }

        private fun descriptorSlots(descriptor: String): Int =
            if (descriptor == "J" || descriptor == "D") 2 else 1

        private fun methodArgSlots(descriptor: String): Int {
            var slots = 0
            var i = 1 // skip '('
            while (i < descriptor.length && descriptor[i] != ')') {
                when (descriptor[i]) {
                    'J', 'D' -> { slots += 2; i++ }
                    'L' -> { slots++; i = descriptor.indexOf(';', i) + 1 }
                    '[' -> { i++; continue }
                    else -> { slots++; i++ }
                }
            }
            return slots
        }

        private fun methodReturnSlots(descriptor: String): Int {
            val retStart = descriptor.indexOf(')') + 1
            return when (descriptor[retStart]) {
                'V' -> 0
                'J', 'D' -> 2
                else -> 1
            }
        }
    }
}
