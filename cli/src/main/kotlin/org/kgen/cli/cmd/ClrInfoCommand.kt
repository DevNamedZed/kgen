package org.kgen.cli.cmd

import org.kgen.binary.pe.PeReader
import org.kgen.binary.pe.clr.*
import org.kgen.cli.*

object ClrInfoCommand {
    fun run(args: List<String>) {
        val parsed = parseArgs(args)
        val path = parsed.requireFile("kgen clrinfo <file>")
        val data = readFileOrExit(path)

        if (detectFormat(data) != BinaryFormat.PE) {
            err("not a PE file: $path")
            return
        }

        val pe = PeReader.read(data)
        val clr = pe.clrMetadata
        if (clr == null) {
            err("no CLR metadata found in $path")
            return
        }

        val showAll = parsed.has("all")
        val showTypes = showAll || parsed.has("types")
        val showMethods = showAll || parsed.has("methods")
        val showRefs = showAll || parsed.has("refs")
        val showTables = showAll || parsed.has("tables")
        val showNone = !showTypes && !showMethods && !showRefs && !showTables

        // Always show summary
        printSummary(clr)

        if (showNone) {
            // Default: show types summary
            printTypeDefs(clr)
            return
        }

        if (showTypes) printTypeDefs(clr)
        if (showMethods) printMethodDefinitions(clr)
        if (showRefs) printReferences(clr)
        if (showTables) printAllTables(clr)
    }

    private fun printSummary(clr: ClrMetadata) {
        println("CLR Metadata")
        println("  Runtime:        ${clr.majorRuntimeVersion}.${clr.minorRuntimeVersion}")
        println("  Version:        ${clr.metadataVersion}")
        println("  Flags:          ${formatClrFlags(clr.flags)}")
        if (clr.entryPointToken != 0)
            println("  Entry point:    ${hex(clr.entryPointToken)}")

        val t = clr.tables
        if (t.assemblies.isNotEmpty()) {
            val asm = t.assemblies[0]
            val name = clr.strings.get(asm.name)
            println("  Assembly:       $name ${asm.majorVersion}.${asm.minorVersion}.${asm.buildNumber}.${asm.revisionNumber}")
        }

        println("  Types:          ${t.typeDefs.size}")
        println("  Methods:        ${t.methodDefs.size}")
        println("  Fields:         ${t.fields.size}")
        println("  MemberRefs:     ${t.memberRefs.size}")
        println("  AssemblyRefs:   ${t.assemblyRefs.size}")
        println()
    }

    private fun printTypeDefs(clr: ClrMetadata) {
        val t = clr.tables
        if (t.typeDefs.isEmpty()) return

        println("Type Definitions (${t.typeDefs.size}):")
        for ((i, td) in t.typeDefs.withIndex()) {
            val ns = clr.strings.get(td.namespace)
            val name = clr.strings.get(td.name)
            val fullName = if (ns.isNotEmpty()) "$ns.$name" else name
            val flags = formatTypeFlags(td.flags)
            println("  [${i + 1}] $flags $fullName")

            // Show fields for this type
            val fieldEnd = if (i + 1 < t.typeDefs.size) t.typeDefs[i + 1].fieldList else t.fields.size + 1
            for (fi in td.fieldList until fieldEnd) {
                if (fi < 1 || fi > t.fields.size) continue
                val f = t.fields[fi - 1]
                val fn = clr.strings.get(f.name)
                val ff = formatFieldFlags(f.flags)
                println("      $ff $fn")
            }

            // Show methods for this type
            val methodEnd = if (i + 1 < t.typeDefs.size) t.typeDefs[i + 1].methodList else t.methodDefs.size + 1
            for (mi in td.methodList until methodEnd) {
                if (mi < 1 || mi > t.methodDefs.size) continue
                val m = t.methodDefs[mi - 1]
                val mn = clr.strings.get(m.name)
                val mf = formatMethodFlags(m.flags)
                println("      $mf $mn()")
            }
        }
        println()
    }

    private fun printMethodDefinitions(clr: ClrMetadata) {
        val t = clr.tables
        if (t.methodDefs.isEmpty()) return

        println("Method Definitions (${t.methodDefs.size}):")
        for ((i, m) in t.methodDefs.withIndex()) {
            val name = clr.strings.get(m.name)
            val flags = formatMethodFlags(m.flags)
            val rva = if (m.rva != 0) " RVA=${hex(m.rva)}" else ""
            println("  [${i + 1}] $flags $name()$rva")

            // Show params for this method
            val paramEnd = if (i + 1 < t.methodDefs.size) t.methodDefs[i + 1].paramList else t.params.size + 1
            for (pi in m.paramList until paramEnd) {
                if (pi < 1 || pi > t.params.size) continue
                val p = t.params[pi - 1]
                val pn = clr.strings.get(p.name)
                println("      param[${p.sequence}]: $pn")
            }
        }
        println()
    }

    private fun printReferences(clr: ClrMetadata) {
        val t = clr.tables

        if (t.assemblyRefs.isNotEmpty()) {
            println("Assembly References (${t.assemblyRefs.size}):")
            for ((i, ar) in t.assemblyRefs.withIndex()) {
                val name = clr.strings.get(ar.name)
                val ver = "${ar.majorVersion}.${ar.minorVersion}.${ar.buildNumber}.${ar.revisionNumber}"
                println("  [${i + 1}] $name $ver")
            }
            println()
        }

        if (t.typeRefs.isNotEmpty()) {
            println("Type References (${t.typeRefs.size}):")
            for ((i, tr) in t.typeRefs.withIndex()) {
                val ns = clr.strings.get(tr.namespace)
                val name = clr.strings.get(tr.name)
                val fullName = if (ns.isNotEmpty()) "$ns.$name" else name
                println("  [${i + 1}] $fullName")
            }
            println()
        }

        if (t.memberRefs.isNotEmpty()) {
            println("Member References (${t.memberRefs.size}):")
            for ((i, mr) in t.memberRefs.withIndex()) {
                val name = clr.strings.get(mr.name)
                println("  [${i + 1}] $name")
            }
            println()
        }

        if (t.moduleRefs.isNotEmpty()) {
            println("Module References (${t.moduleRefs.size}):")
            for ((i, mr) in t.moduleRefs.withIndex()) {
                val name = clr.strings.get(mr.name)
                println("  [${i + 1}] $name")
            }
            println()
        }
    }

    private fun printAllTables(clr: ClrMetadata) {
        val t = clr.tables

        println("Metadata Tables:")
        printTableCount("Module", t.modules.size)
        printTableCount("TypeRef", t.typeRefs.size)
        printTableCount("TypeDef", t.typeDefs.size)
        printTableCount("Field", t.fields.size)
        printTableCount("MethodDefinition", t.methodDefs.size)
        printTableCount("Param", t.params.size)
        printTableCount("InterfaceImpl", t.interfaceImpls.size)
        printTableCount("MemberRef", t.memberRefs.size)
        printTableCount("Constant", t.constants.size)
        printTableCount("CustomAttribute", t.customAttributes.size)
        printTableCount("StandAloneSig", t.standAloneSigs.size)
        printTableCount("ClassLayout", t.classlayouts.size)
        printTableCount("FieldLayout", t.fieldLayouts.size)
        printTableCount("ModuleRef", t.moduleRefs.size)
        printTableCount("TypeSpec", t.typeSpecs.size)
        printTableCount("ImplMap", t.implMaps.size)
        printTableCount("FieldRVA", t.fieldRVAs.size)
        printTableCount("Assembly", t.assemblies.size)
        printTableCount("AssemblyRef", t.assemblyRefs.size)
        printTableCount("NestedClass", t.nestedClasses.size)
        printTableCount("GenericParam", t.genericParams.size)
        printTableCount("MethodSpec", t.methodSpecs.size)
        printTableCount("GenericParamConstraint", t.genericParamConstraints.size)

        if (t.nestedClasses.isNotEmpty()) {
            println()
            println("Nested Classes:")
            for (nc in t.nestedClasses) {
                val nested = resolveTypeName(clr, nc.nestedClass)
                val enclosing = resolveTypeName(clr, nc.enclosingClass)
                println("  $nested in $enclosing")
            }
        }

        if (t.interfaceImpls.isNotEmpty()) {
            println()
            println("Interface Implementations:")
            for (ii in t.interfaceImpls) {
                val typeName = resolveTypeName(clr, ii.classIndex)
                println("  $typeName implements [${hex(ii.interfaceIndex)}]")
            }
        }

        if (t.genericParams.isNotEmpty()) {
            println()
            println("Generic Parameters:")
            for (gp in t.genericParams) {
                val name = clr.strings.get(gp.name)
                println("  $name (index ${gp.number}, owner=${hex(gp.owner)})")
            }
        }

        if (t.implMaps.isNotEmpty()) {
            println()
            println("P/Invoke Mappings:")
            for (im in t.implMaps) {
                val importName = clr.strings.get(im.importName)
                val scope = if (im.importScope in 1..t.moduleRefs.size)
                    clr.strings.get(t.moduleRefs[im.importScope - 1].name) else "?"
                println("  $importName from $scope")
            }
        }
        println()
    }

    private fun printTableCount(name: String, count: Int) {
        if (count > 0) println("  %-25s %d".format(name, count))
    }

    private fun resolveTypeName(clr: ClrMetadata, index: Int): String {
        if (index < 1 || index > clr.tables.typeDefs.size) return "[${hex(index)}]"
        val td = clr.tables.typeDefs[index - 1]
        val ns = clr.strings.get(td.namespace)
        val name = clr.strings.get(td.name)
        return if (ns.isNotEmpty()) "$ns.$name" else name
    }

    private fun formatClrFlags(flags: Int): String {
        val parts = mutableListOf<String>()
        if (flags and 0x01 != 0) parts.add("IL_ONLY")
        if (flags and 0x02 != 0) parts.add("32BIT_REQUIRED")
        if (flags and 0x04 != 0) parts.add("IL_LIBRARY")
        if (flags and 0x08 != 0) parts.add("STRONG_NAME_SIGNED")
        if (flags and 0x10 != 0) parts.add("NATIVE_ENTRYPOINT")
        if (flags and 0x10000 != 0) parts.add("TRACK_DEBUG_DATA")
        if (flags and 0x20000 != 0) parts.add("PREFER_32BIT")
        return if (parts.isEmpty()) hex(flags) else parts.joinToString(" | ")
    }

    private fun formatTypeFlags(flags: Int): String {
        val parts = mutableListOf<String>()
        val vis = flags and 0x07
        when (vis) {
            0x00 -> {} // NotPublic
            0x01 -> parts.add("public")
            0x02 -> parts.add("nested public")
            0x03 -> parts.add("nested private")
            0x04 -> parts.add("nested family")
            0x05 -> parts.add("nested assembly")
        }
        if (flags and 0x80 != 0) parts.add("abstract")
        if (flags and 0x100 != 0) parts.add("sealed")
        if (flags and 0x20 != 0) parts.add("interface")
        else parts.add("class")
        return parts.joinToString(" ")
    }

    private fun formatFieldFlags(flags: Int): String {
        val parts = mutableListOf<String>()
        val access = flags and 0x07
        when (access) {
            0x01 -> parts.add("private")
            0x02 -> parts.add("family+assembly")
            0x03 -> parts.add("assembly")
            0x04 -> parts.add("family")
            0x05 -> parts.add("family|assembly")
            0x06 -> parts.add("public")
        }
        if (flags and 0x10 != 0) parts.add("static")
        if (flags and 0x20 != 0) parts.add("initonly")
        if (flags and 0x40 != 0) parts.add("literal")
        return if (parts.isEmpty()) "field" else parts.joinToString(" ")
    }

    private fun formatMethodFlags(flags: Int): String {
        val parts = mutableListOf<String>()
        val access = flags and 0x07
        when (access) {
            0x01 -> parts.add("private")
            0x02 -> parts.add("family+assembly")
            0x03 -> parts.add("assembly")
            0x04 -> parts.add("family")
            0x05 -> parts.add("family|assembly")
            0x06 -> parts.add("public")
        }
        if (flags and 0x10 != 0) parts.add("static")
        if (flags and 0x20 != 0) parts.add("final")
        if (flags and 0x40 != 0) parts.add("virtual")
        if (flags and 0x400 != 0) parts.add("abstract")
        return if (parts.isEmpty()) "method" else parts.joinToString(" ")
    }
}
