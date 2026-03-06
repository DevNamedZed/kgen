# x86-64 Instruction JSON Schema

## Design

Each JSON file contains instruction *forms* — one entry per unique encoding.
A single mnemonic like `mov` has many forms (reg-reg, reg-imm, reg-mem, etc.).

## JSON Structure

```json
{
  "mnemonic": "add",
  "forms": [
    {
      "operands": ["r32", "r/m32"],
      "encoding": { "opcode": [0x03], "modrm": "reg" },
      "flags": { "set": "OSZAPC", "tested": "" },
      "feature": "baseline"
    },
    {
      "operands": ["r/m32", "imm8"],
      "encoding": { "opcode": [0x83], "modrm": "/0" },
      "flags": { "set": "OSZAPC", "tested": "" },
      "feature": "baseline"
    }
  ]
}
```

## Operand Types

### Register operands
- `r8`, `r16`, `r32`, `r64` — GP register of specified width
- `al`, `ax`, `eax`, `rax` — implicit accumulator (used for shorter encodings)
- `cl` — implicit CL (shift count)
- `xmm`, `ymm`, `zmm` — vector registers
- `k` — AVX-512 mask register
- `mm` — MMX register
- `sreg` — segment register
- `cr`, `dr` — control/debug register
- `st0`, `sti` — x87 FPU registers

### Memory operands
- `m8`, `m16`, `m32`, `m64`, `m128`, `m256`, `m512` — memory of specified width
- `r/m8`, `r/m16`, `r/m32`, `r/m64` — register or memory (ModR/M encoded)
- `xmm/m32`, `xmm/m64`, `xmm/m128` — XMM register or memory
- `ymm/m256` — YMM register or memory
- `zmm/m512` — ZMM register or memory
- `m16:16`, `m16:32`, `m16:64` — far pointer
- `moffs8`, `moffs16`, `moffs32`, `moffs64` — memory offset (no ModR/M)

### Immediate operands
- `imm8`, `imm16`, `imm32`, `imm64` — immediate value
- `rel8`, `rel16`, `rel32` — relative displacement (branches)
- `1` — literal constant 1 (shift by 1)

## Encoding Fields

```json
{
  "opcode": [0x0F, 0x38, 0x00],     // opcode bytes (1-3)
  "modrm": "reg" | "/0".."/7",      // ModR/M usage
  "prefix": "0x66" | "0xF2" | "0xF3" | null,  // mandatory prefix
  "rex_w": true | false | null,      // REX.W requirement
  "vex": "128" | "256" | null,       // VEX encoding and vector length
  "evex": "128" | "256" | "512" | null,  // EVEX encoding
  "vex_map": "0F" | "0F38" | "0F3A" | null,  // VEX opcode map
  "vex_w": 0 | 1 | null,            // VEX.W bit
  "default_size": 32 | 64 | null,    // default operand size in 64-bit mode
  "plus_reg": true | false,          // opcode += register encoding
  "valid_modes": ["64"] | ["32", "64"] | ["16", "32", "64"]
}
```

### modrm values
- `"reg"` — ModR/M byte with reg field = actual register operand
- `"/0"` .. `"/7"` — ModR/M byte with reg field = fixed digit (opcode extension)

### plus_reg
When true, the register encoding is added to the last opcode byte.
E.g., `PUSH r64` is `0x50 + reg`, so RAX=0x50, RCX=0x51, etc.

## Feature Flags

- `baseline` — available on all x86-64 processors
- `cmov` — CMOV (always available on x86-64, listed for completeness)
- `sse`, `sse2`, `sse3`, `ssse3`, `sse4.1`, `sse4.2`
- `avx`, `avx2`, `avx-512f`, `avx-512bw`, `avx-512dq`, `avx-512vl`, `avx-512cd`, `avx-512er`, `avx-512pf`
- `fma`
- `bmi1`, `bmi2`
- `popcnt`, `lzcnt`
- `aes`, `pclmulqdq`
- `f16c`, `rdrand`, `rdseed`
- `adx` — ADCX/ADOX
- `sha`
- `cet` — CET shadow stack / IBT
- `movbe`
- `abm` — ABM (LZCNT+POPCNT, AMD)
- `tbm` — TBM (AMD-only)
- `x87` — x87 FPU
- `mmx` — MMX

## Flag Effects

```json
{
  "set": "OSZAPC",    // flags modified (any combination of O/S/Z/A/P/C)
  "tested": "C",      // flags tested
  "undefined": "AP"   // flags left undefined
}
```

- O = Overflow, S = Sign, Z = Zero, A = Auxiliary carry, P = Parity, C = Carry

## File Organization

```
x86/
  instructions.json    # manifest listing all category files
  gp-arithmetic.json   # ADD, SUB, MUL, IMUL, DIV, IDIV, NEG, INC, DEC, ADC, SBB
  gp-logic.json        # AND, OR, XOR, NOT, TEST
  gp-shift.json        # SHL, SHR, SAR, ROL, ROR, RCL, RCR, SHLD, SHRD
  gp-move.json         # MOV, MOVZX, MOVSX, MOVSXD, LEA, XCHG, BSWAP, CMOV*
  gp-compare.json      # CMP, TEST (also in logic), SETcc
  gp-stack.json        # PUSH, POP, ENTER, LEAVE
  gp-control.json      # JMP, Jcc, CALL, RET, LOOP, INT, SYSCALL, NOP
  gp-string.json       # MOVS, CMPS, SCAS, LODS, STOS (with REP)
  gp-flag.json         # CLC, STC, CLD, STD, LAHF, SAHF, PUSHF, POPF, CMC
  gp-bit.json          # BSF, BSR, POPCNT, LZCNT, TZCNT, BT, BTS, BTR, BTC
  bmi.json             # ANDN, BEXTR, BLSI, BLSMSK, BLSR, BZHI, MULX, PDEP, PEXT, RORX, SARX, SHRX, SHLX
  sse.json             # SSE instructions
  sse2.json            # SSE2 instructions
  sse3.json            # SSE3, SSSE3
  sse4.json            # SSE4.1, SSE4.2
  avx.json             # AVX instructions
  avx2.json            # AVX2 instructions
  avx512.json          # AVX-512 (subset — foundation + common)
  fma.json             # FMA instructions
  aes.json             # AES-NI
  cet.json             # CET (ENDBR64, ENDBR32)
  system.json          # CPUID, RDTSC, RDTSCP, RDMSR, WRMSR, etc.
```
