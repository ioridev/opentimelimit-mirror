/*
 * TimeLimit Copyright <C> 2019 - 2022 Jonas Lochmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.timelimit.android.integration.platform.android.foregroundapp

import android.os.Parcel
import java.io.FileInputStream

/**
 * This class implements a clone of Parcel.readBlob() as this is not a public
 * interface in all Android versions that support it.
 *
 * The wire format is (both as integer): length, useSharedMemory
 *
 * useSharedMemory can be 0 or 1
 */
object ParcelBlob {
    fun readBlob(parcel: Parcel): ByteArray {
        val length = parcel.readInt()

        val useSharedMemory = when (parcel.readInt()) {
            0 -> false
            1 -> true
            else -> throw InvalidBooleanException()
        }

        if (useSharedMemory) {
            val fd = parcel.readFileDescriptor()
            val result = ByteArray(length)

            try {
                FileInputStream(fd.fileDescriptor).use { stream ->
                    var cursor = 0

                    while (cursor < length) {
                        val bytesRead = stream.read(result, cursor, length - cursor)

                        if (bytesRead == -1) {
                            throw UnexpectedEndOfSharedMemory()
                        } else {
                            cursor += bytesRead
                        }
                    }
                }
            } finally {
                fd.close()
            }

            return result
        } else {
            val tempParcel = Parcel.obtain()
            val oldPosition = parcel.dataPosition()

            try {
                tempParcel.appendFrom(parcel, oldPosition, length)

                parcel.setDataPosition(oldPosition + length)

                val result = tempParcel.marshall()

                if (result.size != length) {
                    throw WrongReturnedDataSize()
                }

                return result
            } finally {
                tempParcel.recycle()
            }
        }
    }

    open class ParcelBlobException: RuntimeException()
    class InvalidBooleanException: ParcelBlobException()
    class UnexpectedEndOfSharedMemory: ParcelBlobException()
    class WrongReturnedDataSize: ParcelBlobException()
}