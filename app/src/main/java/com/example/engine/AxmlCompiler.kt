package com.example.engine

import com.example.model.AndroidProject
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Compiles a minimal Android project into a valid Android Binary XML (AXML) format.
 * Android OS package manager expects AndroidManifest.xml inside the APK to be in AXML binary format.
 */
object AxmlCompiler {

    // Chunk types
    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_RESOURCE_MAP_TYPE = 0x0180
    private const val RES_XML_START_NAMESPACE_TYPE = 0x0100
    private const val RES_XML_END_NAMESPACE_TYPE = 0x0101
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103

    // Common Android resource IDs
    private const val ATTR_LABEL = 0x01010001
    private const val ATTR_ICON = 0x01010002
    private const val ATTR_NAME = 0x01010003
    private const val ATTR_THEME = 0x01010000
    private const val ATTR_EXPORTED = 0x01010010
    private const val ATTR_ALLOW_BACKUP = 0x01010280
    private const val ATTR_VERSION_CODE = 0x0101021b
    private const val ATTR_VERSION_NAME = 0x0101021c
    private const val ATTR_MIN_SDK_VERSION = 0x0101020c
    private const val ATTR_TARGET_SDK_VERSION = 0x01010270

    // Value types
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_BOOLEAN = 0x12

    fun compileManifest(project: AndroidProject): ByteArray {
        val strings = mutableListOf<String>()

        fun addString(s: String): Int {
            val idx = strings.indexOf(s)
            if (idx != -1) return idx
            strings.add(s)
            return strings.size - 1
        }

        // Add standard strings
        val sAndroidUri = addString("http://schemas.android.com/apk/res/android")
        val sAndroidPrefix = addString("android")
        val sManifest = addString("manifest")
        val sPackage = addString("package")
        val sVersionCode = addString("versionCode")
        val sVersionName = addString("versionName")
        val sUsesSdk = addString("uses-sdk")
        val sMinSdkVersion = addString("minSdkVersion")
        val sTargetSdkVersion = addString("targetSdkVersion")
        val sUsesPermission = addString("uses-permission")
        val sName = addString("name")
        val sApplication = addString("application")
        val sLabel = addString("label")
        val sIcon = addString("icon")
        val sAllowBackup = addString("allowBackup")
        val sActivity = addString("activity")
        val sExported = addString("exported")
        val sIntentFilter = addString("intent-filter")
        val sAction = addString("action")
        val sCategory = addString("category")
        val sActionMain = addString("android.intent.action.MAIN")
        val sCategoryLauncher = addString("android.intent.category.LAUNCHER")

        // Values
        val sPackageVal = addString(project.packageName)
        val sVersionNameVal = addString(project.versionName)
        val sLabelVal = addString(project.appLabel)
        val sIconVal = addString("@drawable/ic_launcher")
        val sActivityNameVal = addString(".${project.launcherActivity}")

        val permValIndices = project.permissions.map { addString(it) }

        // Build String Pool
        val stringPoolBytes = buildStringPool(strings)

        // Build Resource Map
        val resMapAttrs = intArrayOf(
            ATTR_VERSION_CODE,
            ATTR_VERSION_NAME,
            ATTR_MIN_SDK_VERSION,
            ATTR_TARGET_SDK_VERSION,
            ATTR_NAME,
            ATTR_LABEL,
            ATTR_ICON,
            ATTR_ALLOW_BACKUP,
            ATTR_EXPORTED
        )
        val resMapBytes = buildResourceMap(resMapAttrs)

        // Build XML nodes body
        val nodesOut = ByteArrayOutputStream()

        // Namespace start
        writeNamespace(nodesOut, RES_XML_START_NAMESPACE_TYPE, sAndroidPrefix, sAndroidUri, 1)

        // <manifest ...>
        writeElementStart(
            nodesOut, sAndroidUri, sManifest, 2,
            attrs = listOf(
                RawAttr(sEmpty(), sPackage, sPackageVal, TYPE_STRING, sPackageVal),
                RawAttr(sAndroidUri, sVersionCode, -1, TYPE_INT_DEC, project.versionCode),
                RawAttr(sAndroidUri, sVersionName, sVersionNameVal, TYPE_STRING, sVersionNameVal)
            )
        )

        // <uses-sdk ...>
        writeElementStart(
            nodesOut, sAndroidUri, sUsesSdk, 3,
            attrs = listOf(
                RawAttr(sAndroidUri, sMinSdkVersion, -1, TYPE_INT_DEC, project.minSdk),
                RawAttr(sAndroidUri, sTargetSdkVersion, -1, TYPE_INT_DEC, project.targetSdk)
            )
        )
        writeElementEnd(nodesOut, sAndroidUri, sUsesSdk, 3)

        // <uses-permission ...>
        for (pIndex in permValIndices) {
            writeElementStart(
                nodesOut, sAndroidUri, sUsesPermission, 4,
                attrs = listOf(
                    RawAttr(sAndroidUri, sName, pIndex, TYPE_STRING, pIndex)
                )
            )
            writeElementEnd(nodesOut, sAndroidUri, sUsesPermission, 4)
        }

        // <application ...>
        writeElementStart(
            nodesOut, sAndroidUri, sApplication, 5,
            attrs = listOf(
                RawAttr(sAndroidUri, sLabel, sLabelVal, TYPE_STRING, sLabelVal),
                RawAttr(sAndroidUri, sIcon, sIconVal, TYPE_STRING, sIconVal),
                RawAttr(sAndroidUri, sAllowBackup, -1, TYPE_INT_BOOLEAN, 1)
            )
        )

        // <activity ...>
        writeElementStart(
            nodesOut, sAndroidUri, sActivity, 6,
            attrs = listOf(
                RawAttr(sAndroidUri, sName, sActivityNameVal, TYPE_STRING, sActivityNameVal),
                RawAttr(sAndroidUri, sLabel, sLabelVal, TYPE_STRING, sLabelVal),
                RawAttr(sAndroidUri, sExported, -1, TYPE_INT_BOOLEAN, 1)
            )
        )

        // <intent-filter>
        writeElementStart(nodesOut, sAndroidUri, sIntentFilter, 7, emptyList())

        // <action android:name="android.intent.action.MAIN" />
        writeElementStart(
            nodesOut, sAndroidUri, sAction, 8,
            attrs = listOf(RawAttr(sAndroidUri, sName, sActionMain, TYPE_STRING, sActionMain))
        )
        writeElementEnd(nodesOut, sAndroidUri, sAction, 8)

        // <category android:name="android.intent.category.LAUNCHER" />
        writeElementStart(
            nodesOut, sAndroidUri, sCategory, 9,
            attrs = listOf(RawAttr(sAndroidUri, sName, sCategoryLauncher, TYPE_STRING, sCategoryLauncher))
        )
        writeElementEnd(nodesOut, sAndroidUri, sCategory, 9)

        writeElementEnd(nodesOut, sAndroidUri, sIntentFilter, 7)
        writeElementEnd(nodesOut, sAndroidUri, sActivity, 6)
        writeElementEnd(nodesOut, sAndroidUri, sApplication, 5)
        writeElementEnd(nodesOut, sAndroidUri, sManifest, 2)

        // Namespace end
        writeNamespace(nodesOut, RES_XML_END_NAMESPACE_TYPE, sAndroidPrefix, sAndroidUri, 10)

        val xmlBody = nodesOut.toByteArray()
        val totalSize = 8 + stringPoolBytes.size + resMapBytes.size + xmlBody.size

        val totalBuf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        totalBuf.putShort(RES_XML_TYPE.toShort())
        totalBuf.putShort(8.toShort()) // header size
        totalBuf.putInt(totalSize)

        totalBuf.put(stringPoolBytes)
        totalBuf.put(resMapBytes)
        totalBuf.put(xmlBody)

        return totalBuf.array()
    }

    private fun sEmpty(): Int = -1

    private data class RawAttr(
        val ns: Int,
        val name: Int,
        val rawValue: Int,
        val type: Int,
        val data: Int
    )

    private fun buildStringPool(strings: List<String>): ByteArray {
        val stringCount = strings.size
        val styleCount = 0
        val flags = 1 shl 8 // UTF-8 flag

        val offsets = IntArray(stringCount)
        val dataOut = ByteArrayOutputStream()

        for (i in strings.indices) {
            offsets[i] = dataOut.size()
            val utf8 = strings[i].toByteArray(Charsets.UTF_8)
            val charLen = strings[i].length
            val byteLen = utf8.size

            dataOut.write(charLen.coerceAtMost(127))
            dataOut.write(byteLen.coerceAtMost(127))
            dataOut.write(utf8)
            dataOut.write(0) // null terminator
        }

        // Align string data to 4 bytes
        while (dataOut.size() % 4 != 0) {
            dataOut.write(0)
        }

        val headerSize = 28
        val stringsStart = headerSize + (stringCount * 4)
        val stringBytes = dataOut.toByteArray()
        val totalSize = stringsStart + stringBytes.size

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(RES_STRING_POOL_TYPE.toShort())
        buf.putShort(headerSize.toShort())
        buf.putInt(totalSize)
        buf.putInt(stringCount)
        buf.putInt(styleCount)
        buf.putInt(flags)
        buf.putInt(stringsStart)
        buf.putInt(0) // stylesStart

        for (off in offsets) {
            buf.putInt(off)
        }
        buf.put(stringBytes)

        return buf.array()
    }

    private fun buildResourceMap(attrIds: IntArray): ByteArray {
        val headerSize = 8
        val totalSize = headerSize + (attrIds.size * 4)
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(RES_XML_RESOURCE_MAP_TYPE.toShort())
        buf.putShort(headerSize.toShort())
        buf.putInt(totalSize)
        for (id in attrIds) {
            buf.putInt(id)
        }
        return buf.array()
    }

    private fun writeNamespace(out: ByteArrayOutputStream, type: Int, prefixIdx: Int, uriIdx: Int, lineNum: Int) {
        val size = 24
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(type.toShort())
        buf.putShort(16.toShort())
        buf.putInt(size)
        buf.putInt(lineNum)
        buf.putInt(0xFFFFFFFF.toInt()) // comment
        buf.putInt(prefixIdx)
        buf.putInt(uriIdx)
        out.write(buf.array())
    }

    private fun writeElementStart(
        out: ByteArrayOutputStream,
        nsIdx: Int,
        nameIdx: Int,
        lineNum: Int,
        attrs: List<RawAttr>
    ) {
        val headerSize = 16
        val attrSize = 20
        val totalSize = headerSize + 20 + (attrs.size * attrSize)

        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(RES_XML_START_ELEMENT_TYPE.toShort())
        buf.putShort(headerSize.toShort())
        buf.putInt(totalSize)
        buf.putInt(lineNum)
        buf.putInt(0xFFFFFFFF.toInt()) // comment

        buf.putInt(nsIdx)
        buf.putInt(nameIdx)
        buf.putShort(attrSize.toShort()) // attributeStart
        buf.putShort(attrSize.toShort()) // attributeSize
        buf.putShort(attrs.size.toShort()) // attributeCount
        buf.putShort(0.toShort()) // idIndex
        buf.putShort(0.toShort()) // classIndex
        buf.putShort(0.toShort()) // styleIndex

        for (a in attrs) {
            buf.putInt(a.ns)
            buf.putInt(a.name)
            buf.putInt(a.rawValue)
            buf.putShort(8.toShort()) // size
            buf.put((0).toByte()) // res0
            buf.put(a.type.toByte()) // dataType
            buf.putInt(a.data) // data
        }

        out.write(buf.array())
    }

    private fun writeElementEnd(out: ByteArrayOutputStream, nsIdx: Int, nameIdx: Int, lineNum: Int) {
        val size = 24
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(RES_XML_END_ELEMENT_TYPE.toShort())
        buf.putShort(16.toShort())
        buf.putInt(size)
        buf.putInt(lineNum)
        buf.putInt(0xFFFFFFFF.toInt()) // comment
        buf.putInt(nsIdx)
        buf.putInt(nameIdx)
        out.write(buf.array())
    }
}
