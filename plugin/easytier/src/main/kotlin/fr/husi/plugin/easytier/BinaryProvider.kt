package fr.husi.plugin.easytier

import android.net.Uri
import android.os.ParcelFileDescriptor
import fr.husi.plugin.NativePluginProvider
import fr.husi.plugin.PathProvider
import java.io.File
import java.io.FileNotFoundException

class BinaryProvider : NativePluginProvider() {
    override fun populateFiles(provider: PathProvider) {
        provider.addPath("easytier-plugin", 0b111101101)
    }

    override fun getExecutable(): String =
        File(context!!.applicationInfo.nativeLibraryDir).resolve("libeasytier.so").absolutePath
    override fun openFile(uri: Uri): ParcelFileDescriptor = when (uri.path) {
        "/easytier-plugin" -> ParcelFileDescriptor.open(
            File(getExecutable()),
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
        else -> throw FileNotFoundException()
    }
}
