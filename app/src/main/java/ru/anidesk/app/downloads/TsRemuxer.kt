package ru.anidesk.app.downloads

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.muxer.AnnexBToAvccConverter
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Mp4Muxer
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Перепаковка MPEG-TS в MP4 через собственный TS-парсер Media3 (TsExtractor)
 * и чистый Java-муксер Mp4Muxer.
 *
 * Платформенный MediaExtractor не используется: на ряде устройств
 * (например, MediaTek) его TS-демуксер находит треки, но не отдаёт ни одного
 * сэмпла, из-за чего получались пустые mp4 размером ~3 КБ.
 */
@OptIn(UnstableApi::class)
object TsRemuxer {

    private const val MIN_VALID_OUTPUT_BYTES = 200_000L

    /** @return готовый mp4 или null, если перепаковка не удалась. */
    fun remux(input: File): File? {
        val output = File(input.parentFile, "remux_${System.currentTimeMillis()}.mp4")
        var muxer: Mp4Muxer? = null
        try {
            muxer = Mp4Muxer.Builder(FileOutputStream(output))
                .setAnnexBToAvccConverter(AnnexBToAvccConverter.DEFAULT)
                .build()
            val trackOutput = MuxerTrackOutput(muxer)
            val extractor = TsExtractor()
            extractor.init(object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput = trackOutput
                override fun endTracks() {}
                override fun seekMap(seekMap: SeekMap) {}
            })

            var dataSource = FileDataSource()
            try {
                var position = 0L
                var seeks = 0
                while (true) {
                    dataSource.open(DataSpec(Uri.fromFile(input), position, input.length() - position))
                    val extractorInput: ExtractorInput = DefaultExtractorInput(dataSource, position, input.length())
                    val positionHolder = PositionHolder()
                    var result = Extractor.RESULT_CONTINUE
                    while (result != Extractor.RESULT_END_OF_INPUT && result != Extractor.RESULT_SEEK) {
                        result = extractor.read(extractorInput, positionHolder)
                    }
                    dataSource.close()
                    if (result == Extractor.RESULT_END_OF_INPUT) break
                    // Extractor просит перечитать с другой позиции — переоткрываем поток.
                    position = positionHolder.position.coerceAtLeast(0L)
                    if (position >= input.length() || ++seeks > 8) break
                }
            } finally {
                runCatching { dataSource.close() }
            }

            muxer.close()
            muxer = null
            return if (output.length() >= MIN_VALID_OUTPUT_BYTES) {
                output
            } else {
                runCatching { output.delete() }
                null
            }
        } catch (e: Exception) {
            runCatching { output.delete() }
            return null
        } finally {
            runCatching { muxer?.close() }
        }
    }

    private class MuxerTrackOutput(private val muxer: Mp4Muxer) : TrackOutput {

        private var trackIndex: Int = -1
        private val sampleBytes = ByteArrayOutputStream()

        override fun format(format: Format) {
            val mime = format.sampleMimeType ?: return
            if (MimeTypes.getTrackType(mime) == C.TRACK_TYPE_UNKNOWN) return
            trackIndex = muxer.addTrack(format)
        }

        override fun sampleData(
            input: DataReader,
            length: Int,
            allowEndOfInput: Boolean,
            sampleDataPart: Int,
        ): Int {
            val buffer = ByteArray(minOf(length, 64 * 1024))
            var remaining = length
            while (remaining > 0) {
                val n = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (n == C.RESULT_END_OF_INPUT) {
                    if (allowEndOfInput && remaining == length) return C.RESULT_END_OF_INPUT
                    throw EOFException("Неожиданный конец данных")
                }
                sampleBytes.write(buffer, 0, n)
                remaining -= n
            }
            return 0
        }

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
            sampleBytes.write(data.getData(), data.getPosition(), length)
            data.skipBytes(length)
        }

        override fun sampleMetadata(
            timeUs: Long,
            flags: Int,
            size: Int,
            offset: Int,
            cryptoData: TrackOutput.CryptoData?,
        ) {
            val bytes = sampleBytes.toByteArray()
            val total = bytes.size
            val start = total - offset - size
            if (start < 0 || size <= 0 || start + size > total) {
                sampleBytes.reset()
                return
            }
            if (trackIndex >= 0) {
                val buffer = ByteBuffer.wrap(bytes, start, size)
                muxer.writeSampleData(trackIndex, buffer, BufferInfo(timeUs.coerceAtLeast(0L), size, flags))
            }
            // Сохраняем «хвост» (байты следующих сэмплов), отбрасываем всё до конца этого сэмпла.
            sampleBytes.reset()
            if (offset > 0) {
                sampleBytes.write(bytes, start + size, offset)
            }
        }
    }
}
