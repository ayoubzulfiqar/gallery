package com.ayoubzulfiqar.gallery.vault

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The vault directory boundary must hold even for crafted relative paths. */
class FileVaultStorageTest {

    @Test
    fun pathCannotEscapeIntoASiblingWithTheSamePrefix() {
        val base = Files.createTempDirectory("vault-test").toFile()
        try {
            val root = java.io.File(base, "vault").apply { mkdirs() }
            val sibling = java.io.File(base, "vault-evil").apply { mkdirs() }
            val storage = FileVaultStorage(root)
            // "vault-evil" shares the string prefix of "vault" but is outside the boundary.
            assertFailsWith<IllegalArgumentException> {
                storage.writeAtomically("../vault-evil/pwn", byteArrayOf(1))
            }
            assertTrue(sibling.listFiles().isNullOrEmpty(), "nothing may be written outside the vault root")
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun normalNestedPathsStillWork() {
        val base = Files.createTempDirectory("vault-test").toFile()
        try {
            val root = java.io.File(base, "vault").apply { mkdirs() }
            val storage = FileVaultStorage(root)
            storage.writeAtomically("blobs/abc123", byteArrayOf(1, 2, 3))
            assertTrue(storage.read("blobs/abc123")!!.contentEquals(byteArrayOf(1, 2, 3)))
        } finally {
            base.deleteRecursively()
        }
    }
}