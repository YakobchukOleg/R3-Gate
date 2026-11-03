package com.r3gate.app.backup

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/** Stable mapping of original photo URIs to portable binary archive entries. */
object PhotoArchiveIndex {
    const val ENTRY = "photos/index.tsv"
    private const val HEADER = "WORKMETER-PHOTOS/1"
    const val MAX_PHOTOS = 2000
    private const val MAX_INDEX_BYTES = 1024 * 1024
    data class Photo(val uri: String, val path: String)

    fun pathFor(uri: String): String {
        require(uri.isNotBlank() && !uri.any { it.code < 32 }) { "Invalid photo URI" }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return "photos/$hash.bin"
    }
    fun build(uris: Iterable<String>): List<Photo> {
        val unique = LinkedHashSet<String>()
        for (uri in uris) {
            require(uri.isNotBlank() && !uri.any { it.code < 32 }) { "Invalid photo URI" }
            unique.add(uri)
            require(unique.size <= MAX_PHOTOS) { "Too many photos" }
        }
        return unique.map { Photo(it, pathFor(it)) }
    }
    fun encode(photos: List<Photo>): ByteArray {
        require(photos.size <= MAX_PHOTOS && photos.distinctBy { it.uri }.size == photos.size)
        val lines = photos.map { photo ->
            require(photo.path == pathFor(photo.uri)) { "Photo index path mismatch" }
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(photo.uri.toByteArray(Charsets.UTF_8)) + "\t" + photo.path
        }
        val output = (listOf(HEADER) + lines).joinToString("\n", postfix = "\n")
            .toByteArray(Charsets.UTF_8)
        require(output.size <= MAX_INDEX_BYTES) { "Photo index too large" }
        return output
    }
    fun decode(input: ByteArray): List<Photo> {
        require(input.size <= MAX_INDEX_BYTES) { "Photo index too large" }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try { decoder.decode(ByteBuffer.wrap(input)).toString() }
        catch (e: CharacterCodingException) { throw IllegalArgumentException("Invalid UTF-8 photo index", e) }
        require(text.endsWith('\n')) { "Truncated photo index" }
        val lines = text.dropLast(1).split('\n')
        require(lines.firstOrNull() == HEADER) { "Unsupported photo index" }
        require(lines.size - 1 <= MAX_PHOTOS) { "Too many photos" }
        val photos = lines.drop(1).map { line ->
            val fields = line.split('\t')
            require(fields.size == 2 && fields[0].isNotEmpty()) { "Malformed photo index" }
            val uriBytes = Base64.getUrlDecoder().decode(fields[0])
            val uri = try { decoder.reset().decode(ByteBuffer.wrap(uriBytes)).toString() }
            catch (e: CharacterCodingException) { throw IllegalArgumentException("Invalid UTF-8 photo URI", e) }
            val photo = Photo(uri, fields[1])
            require(photo.path == pathFor(photo.uri)) { "Photo index path mismatch" }
            photo
        }
        require(photos.map { it.uri }.toSet().size == photos.size) { "Duplicate photo URI" }
        require(photos.map { it.path }.toSet().size == photos.size) { "Duplicate photo entry" }
        return photos
    }
}
