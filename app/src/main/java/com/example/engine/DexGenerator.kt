package com.example.engine

import com.example.model.AndroidProject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32

object DexGenerator {

    /**
     * Generates a valid Dalvik Executable (classes.dex) with class definitions for the project.
     */
    fun generateDex(project: AndroidProject): ByteArray {
        val activityClassDescriptor = "L${project.packageName.replace('.', '/')}/${project.launcherActivity};"
        val superActivityDescriptor = "Landroid/app/Activity;"
        val bundleDescriptor = "Landroid/os/Bundle;"
        val voidDescriptor = "V"

        // Collect all strings in sorted order (DEX requires string_ids to be sorted lexicographically)
        val rawStrings = listOf(
            "<init>",
            "MainActivity.java",
            activityClassDescriptor,
            bundleDescriptor,
            superActivityDescriptor,
            "onCreate",
            voidDescriptor
        ).distinct().sorted()

        val stringOffsets = mutableListOf<Int>()
        val stringDataOut = ByteArrayOutputStream()

        for (str in rawStrings) {
            stringOffsets.add(stringDataOut.size())
            val utf8 = str.toByteArray(Charsets.UTF_8)
            writeUleb128(stringDataOut, utf8.size)
            stringDataOut.write(utf8)
            stringDataOut.write(0) // null-terminated
        }

        // Align string data
        val stringDataBytes = stringDataOut.toByteArray()

        // Types:
        // 0: activityClassDescriptor
        // 1: bundleDescriptor
        // 2: superActivityDescriptor
        // 3: voidDescriptor
        val types = listOf(
            activityClassDescriptor,
            bundleDescriptor,
            superActivityDescriptor,
            voidDescriptor
        ).sorted()

        val typeIds = types.map { rawStrings.indexOf(it) }

        // Protos:
        // Proto 0: ()V -> shorty "V", return type void, parameters: none
        // Proto 1: (Landroid/os/Bundle;)V -> shorty "VL", return type void, parameters: [bundle]
        val proto0ShortyIdx = rawStrings.indexOf(voidDescriptor)
        val proto0ReturnIdx = types.indexOf(voidDescriptor)

        val proto1ShortyIdx = rawStrings.indexOf(voidDescriptor) // shorty descriptor "V"
        val proto1ReturnIdx = types.indexOf(voidDescriptor)

        // Methods:
        // Method 0: activity.<init>()V
        // Method 1: activity.onCreate(Bundle)V
        // Method 2: super.<init>()V
        // Method 3: super.onCreate(Bundle)V

        // Let's build bytecode:
        // Code item 0: <init>()V -> invoke-direct {v0}, Landroid/app/Activity;-><init>()V; return-void
        // Code item 1: onCreate(Bundle)V -> invoke-super {v0, v1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V; return-void

        val out = ByteArrayOutputStream()

        // DEX Header placeholder (112 bytes)
        val header = ByteArray(112)
        out.write(header)

        // String IDs table: offset in file -> string_data_item
        val stringIdsOff = out.size()
        val stringIdsBytes = ByteBuffer.allocate(rawStrings.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        // We will fill stringIdsBytes after we know string_data_off

        // Type IDs table: offset = string_id_index
        val typeIdsOff = stringIdsOff + (rawStrings.size * 4)
        val typeIdsBytes = ByteBuffer.allocate(typeIds.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (t in typeIds) {
            typeIdsBytes.putInt(t)
        }

        // Proto IDs table: shorty_idx(4), return_type_idx(4), parameters_off(4) -> 12 bytes each
        val protoIdsOff = typeIdsOff + typeIdsBytes.capacity()
        val protoCount = 2
        val protoIdsBytes = ByteBuffer.allocate(protoCount * 12).order(ByteOrder.LITTLE_ENDIAN)

        // Field IDs table: 0
        val fieldIdsOff = protoIdsOff + (protoCount * 12)

        // Method IDs table: class_idx(2), proto_idx(2), name_idx(4) -> 8 bytes each
        val methodIdsOff = fieldIdsOff
        val methodCount = 4
        val methodIdsBytes = ByteBuffer.allocate(methodCount * 8).order(ByteOrder.LITTLE_ENDIAN)

        // Class defs table: 1 class -> 32 bytes
        val classDefsOff = methodIdsOff + (methodCount * 8)
        val classDefsBytes = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)

        // Now compute data section
        var dataOff = classDefsOff + 32

        // Proto 1 type_list (Bundle)
        val typeListOff = dataOff
        val typeListBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        typeListBytes.putInt(1) // size
        typeListBytes.putShort(types.indexOf(bundleDescriptor).toShort()) // type_idx
        typeListBytes.putShort(0.toShort()) // padding
        dataOff += 8

        // Fill proto IDs
        // Proto 0: ()V
        protoIdsBytes.putInt(proto0ShortyIdx)
        protoIdsBytes.putInt(proto0ReturnIdx)
        protoIdsBytes.putInt(0) // no params

        // Proto 1: (Bundle)V
        protoIdsBytes.putInt(proto1ShortyIdx)
        protoIdsBytes.putInt(proto1ReturnIdx)
        protoIdsBytes.putInt(typeListOff)

        // Fill Method IDs (must be sorted by class_idx, proto_idx, name_idx)
        val actTypeIdx = types.indexOf(activityClassDescriptor).toShort()
        val superTypeIdx = types.indexOf(superActivityDescriptor).toShort()
        val initNameIdx = rawStrings.indexOf("<init>")
        val onCreateNameIdx = rawStrings.indexOf("onCreate")

        // Method 0: activity.<init>()V
        methodIdsBytes.putShort(actTypeIdx)
        methodIdsBytes.putShort(0.toShort()) // proto 0
        methodIdsBytes.putInt(initNameIdx)

        // Method 1: activity.onCreate(Bundle)V
        methodIdsBytes.putShort(actTypeIdx)
        methodIdsBytes.putShort(1.toShort()) // proto 1
        methodIdsBytes.putInt(onCreateNameIdx)

        // Method 2: super.<init>()V
        methodIdsBytes.putShort(superTypeIdx)
        methodIdsBytes.putShort(0.toShort()) // proto 0
        methodIdsBytes.putInt(initNameIdx)

        // Method 3: super.onCreate(Bundle)V
        methodIdsBytes.putShort(superTypeIdx)
        methodIdsBytes.putShort(1.toShort()) // proto 1
        methodIdsBytes.putInt(onCreateNameIdx)

        // Code Item 1: <init>()V
        // registers_size(2), ins_size(2), outs_size(2), tries_size(2), debug_info_off(4), insns_size(4), insns...
        val code1Off = dataOff
        val code1 = ByteBuffer.allocate(16 + 8).order(ByteOrder.LITTLE_ENDIAN)
        code1.putShort(1.toShort()) // registers
        code1.putShort(1.toShort()) // ins
        code1.putShort(1.toShort()) // outs
        code1.putShort(0.toShort()) // tries
        code1.putInt(0) // debug_info
        code1.putInt(3) // insns size (3 x 16-bit)
        // invoke-direct {v0}, Method 2 (super.<init>) -> opcode 0x70, B=0, A=1, G=0, D=0, C=0, F=0, E=0, index=2
        // byte: 70 10 02 00 00 00 -> 0x1070, 0x0002, 0x0000
        code1.putShort(0x1070.toShort())
        code1.putShort(2.toShort())
        code1.putShort(0.toShort())
        // return-void: opcode 0x0e
        code1.putShort(0x000e.toShort())
        val code1Bytes = code1.array()
        dataOff += code1Bytes.size

        // Code Item 2: onCreate(Bundle)V
        val code2Off = dataOff
        val code2 = ByteBuffer.allocate(16 + 10).order(ByteOrder.LITTLE_ENDIAN)
        code2.putShort(2.toShort()) // registers
        code2.putShort(2.toShort()) // ins
        code2.putShort(2.toShort()) // outs
        code2.putShort(0.toShort()) // tries
        code2.putInt(0) // debug_info
        code2.putInt(4) // insns size (4 x 16-bit)
        // invoke-super {v0, v1}, Method 3 (super.onCreate) -> opcode 0x6f, A=2, index=3, C=0, D=1
        // 0x206f, 0x0003, 0x0100
        code2.putShort(0x206f.toShort())
        code2.putShort(3.toShort())
        code2.putShort(0x0100.toShort())
        // return-void
        code2.putShort(0x000e.toShort())
        code2.putShort(0.toShort()) // align
        val code2Bytes = code2.array()
        dataOff += code2Bytes.size

        // Class Data Item
        val classDataOff = dataOff
        val classDataOut = ByteArrayOutputStream()
        writeUleb128(classDataOut, 0) // static_fields_size
        writeUleb128(classDataOut, 0) // instance_fields_size
        writeUleb128(classDataOut, 2) // direct_methods_size: <init>
        writeUleb128(classDataOut, 0) // virtual_methods_size: onCreate

        // direct method 0: <init>()V (method_idx_diff = 0)
        writeUleb128(classDataOut, 0) // method index 0
        writeUleb128(classDataOut, 0x10001) // ACC_PUBLIC | ACC_CONSTRUCTOR
        writeUleb128(classDataOut, code1Off)

        // direct method 1: onCreate(Bundle)V (method_idx_diff = 1)
        writeUleb128(classDataOut, 1) // delta from method 0 is 1
        writeUleb128(classDataOut, 0x0004) // ACC_PROTECTED
        writeUleb128(classDataOut, code2Off)

        val classDataBytes = classDataOut.toByteArray()
        dataOff += classDataBytes.size

        // Fill Class Defs (32 bytes)
        classDefsBytes.putInt(types.indexOf(activityClassDescriptor)) // class_idx
        classDefsBytes.putInt(0x0001) // access_flags: ACC_PUBLIC
        classDefsBytes.putInt(types.indexOf(superActivityDescriptor)) // superclass_idx
        classDefsBytes.putInt(0) // interfaces_off
        classDefsBytes.putInt(rawStrings.indexOf("MainActivity.java")) // source_file_idx
        classDefsBytes.putInt(0) // annotations_off
        classDefsBytes.putInt(classDataOff) // class_data_off
        classDefsBytes.putInt(0) // static_values_off

        // String Data items
        val stringDataStartOff = dataOff
        for (i in stringOffsets.indices) {
            stringIdsBytes.putInt(stringDataStartOff + stringOffsets[i])
        }
        dataOff += stringDataBytes.size

        // Map list section
        while (dataOff % 4 != 0) dataOff++
        val mapListOff = dataOff
        val mapEntries = listOf(
            Pair(0x0000, 1), // TYPE_HEADER_ITEM
            Pair(0x0001, rawStrings.size), // TYPE_STRING_ID_ITEM
            Pair(0x0002, typeIds.size), // TYPE_TYPE_ID_ITEM
            Pair(0x0003, protoCount), // TYPE_PROTO_ID_ITEM
            Pair(0x0005, methodCount), // TYPE_METHOD_ID_ITEM
            Pair(0x0006, 1), // TYPE_CLASS_DEF_ITEM
            Pair(0x1001, 1), // TYPE_TYPE_LIST
            Pair(0x2001, 2), // TYPE_CODE_ITEM
            Pair(0x2000, 1), // TYPE_CLASS_DATA_ITEM
            Pair(0x2002, rawStrings.size), // TYPE_STRING_DATA_ITEM
            Pair(0x1000, 1) // TYPE_MAP_LIST
        )
        val mapBuf = ByteBuffer.allocate(4 + (mapEntries.size * 12)).order(ByteOrder.LITTLE_ENDIAN)
        mapBuf.putInt(mapEntries.size)
        for ((itemType, count) in mapEntries) {
            mapBuf.putShort(itemType.toShort())
            mapBuf.putShort(0.toShort()) // unused
            mapBuf.putInt(count)
            val offset = when (itemType) {
                0x0000 -> 0
                0x0001 -> stringIdsOff
                0x0002 -> typeIdsOff
                0x0003 -> protoIdsOff
                0x0005 -> methodIdsOff
                0x0006 -> classDefsOff
                0x1001 -> typeListOff
                0x2001 -> code1Off
                0x2000 -> classDataOff
                0x2002 -> stringDataStartOff
                0x1000 -> mapListOff
                else -> 0
            }
            mapBuf.putInt(offset)
        }
        val mapBytes = mapBuf.array()
        val totalFileSize = mapListOff + mapBytes.size

        // Build total DEX ByteBuffer
        val totalBuf = ByteBuffer.allocate(totalFileSize).order(ByteOrder.LITTLE_ENDIAN)

        // Magic: dex\n035\0
        val magic = byteArrayOf(0x64, 0x65, 0x78, 0x0a, 0x30, 0x33, 0x35, 0x00)
        totalBuf.put(magic)

        totalBuf.putInt(0) // Checksum placeholder (pos 8)
        totalBuf.put(ByteArray(20)) // SHA-1 signature placeholder (pos 12)

        totalBuf.putInt(totalFileSize) // file_size
        totalBuf.putInt(112) // header_size
        totalBuf.putInt(0x12345678) // endian_tag
        totalBuf.putInt(0) // link_size
        totalBuf.putInt(0) // link_off
        totalBuf.putInt(mapListOff) // map_off

        totalBuf.putInt(rawStrings.size) // string_ids_size
        totalBuf.putInt(stringIdsOff) // string_ids_off

        totalBuf.putInt(typeIds.size) // type_ids_size
        totalBuf.putInt(typeIdsOff) // type_ids_off

        totalBuf.putInt(protoCount) // proto_ids_size
        totalBuf.putInt(protoIdsOff) // proto_ids_off

        totalBuf.putInt(0) // field_ids_size
        totalBuf.putInt(0) // field_ids_off

        totalBuf.putInt(methodCount) // method_ids_size
        totalBuf.putInt(methodIdsOff) // method_ids_off

        totalBuf.putInt(1) // class_defs_size
        totalBuf.putInt(classDefsOff) // class_defs_off

        totalBuf.putInt(totalFileSize - header.size) // data_size
        totalBuf.putInt(header.size) // data_off

        // Tables
        totalBuf.position(stringIdsOff)
        totalBuf.put(stringIdsBytes.array())

        totalBuf.position(typeIdsOff)
        totalBuf.put(typeIdsBytes.array())

        totalBuf.position(protoIdsOff)
        totalBuf.put(protoIdsBytes.array())

        totalBuf.position(methodIdsOff)
        totalBuf.put(methodIdsBytes.array())

        totalBuf.position(classDefsOff)
        totalBuf.put(classDefsBytes.array())

        // Data
        totalBuf.position(typeListOff)
        totalBuf.put(typeListBytes.array())

        totalBuf.position(code1Off)
        totalBuf.put(code1Bytes)

        totalBuf.position(code2Off)
        totalBuf.put(code2Bytes)

        totalBuf.position(classDataOff)
        totalBuf.put(classDataBytes)

        totalBuf.position(stringDataStartOff)
        totalBuf.put(stringDataBytes)

        totalBuf.position(mapListOff)
        totalBuf.put(mapBytes)

        val fullBytes = totalBuf.array()

        // Compute SHA-1 for bytes 32..end
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(fullBytes, 32, fullBytes.size - 32)
        val sig = sha1.digest()
        System.arraycopy(sig, 0, fullBytes, 12, 20)

        // Compute Adler-32 for bytes 12..end
        val adler = Adler32()
        adler.update(fullBytes, 12, fullBytes.size - 12)
        val checksumVal = adler.value.toInt()
        val checkBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        checkBuf.putInt(checksumVal)
        System.arraycopy(checkBuf.array(), 0, fullBytes, 8, 4)

        return fullBytes
    }

    private fun writeUleb128(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (v > 0x7f) {
            out.write((v and 0x7f) or 0x80)
            v = v ushr 7
        }
        out.write(v and 0x7f)
    }
}
