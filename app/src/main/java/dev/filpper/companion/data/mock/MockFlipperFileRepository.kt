package dev.filpper.companion.data.mock

import com.flipperdevices.protobuf.storage.StorageProto
import dev.filpper.companion.domain.model.FlipperFile
import dev.filpper.companion.domain.model.FlipperFileCategory
import dev.filpper.companion.domain.repository.FlipperFileRepository
import javax.inject.Inject

/** Test catalog modeled as PB_Storage.ListResponse batches; never reads actual storage. */
class MockFlipperFileRepository @Inject constructor() : FlipperFileRepository {
    override suspend fun loadFiles(): List<FlipperFile> {
        val responsesByDirectory = mapOf(
            "/ext/nfc" to response("Office Badge.nfc" to 512, "Transport Card.nfc" to 780),
            "/ext/lfrfid" to response("Garage.rfid" to 128, "Entry Tag.rfid" to 128),
            "/ext/subghz" to response("Gate.sub" to 2064, "Signal 433.sub" to 1500),
            "/ext/infrared" to response("TV.ir" to 340, "Audio.ir" to 250),
            "/ext/ibutton" to response("Front Door.ibtn" to 96),
            "/ext/badusb" to response("Hello World.txt" to 88),
            "/ext/misc" to response("notes.log" to 490),
        )

        return responsesByDirectory.flatMap { (directory, response) ->
            response.fileList.map { proto ->
                val path = "$directory/${proto.name}"
                FlipperFile(
                    path = path,
                    filename = proto.name,
                    sizeBytes = proto.size,
                    category = FlipperFileCategory.fromPath(path),
                )
            }
        }
    }

    private fun response(vararg entries: Pair<String, Int>): StorageProto.ListResponse =
        StorageProto.ListResponse.newBuilder().apply {
            entries.forEach { (name, size) ->
                addFile(
                    StorageProto.File.newBuilder()
                        .setType(StorageProto.File.FileType.FILE)
                        .setName(name)
                        .setSize(size)
                        .build(),
                )
            }
        }.build()
}
