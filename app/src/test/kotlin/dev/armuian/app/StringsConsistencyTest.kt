package dev.armuian.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Защита от «забыли перевести»: все языки имеют одинаковые ключи, а каждый ключ журнала из кода есть в ресурсах. */
class StringsConsistencyTest {
    private val moduleDir = File(".").canonicalFile
    private val projectDir = moduleDir.parentFile

    private fun keys(dir: String): Set<String> =
        Regex("""name="([^"]+)"""").findAll(File(moduleDir, "src/main/res/$dir/strings.xml").readText()).map { it.groupValues[1] }.toSet() - "app_name"

    @Test fun allLanguagesHaveSameKeys() {
        val base = keys("values")
        for (lang in listOf("values-ru", "values-de", "values-zh")) {
            assertEquals("Ключи $lang отличаются от английских", base, keys(lang))
        }
    }

    @Test fun everyLogKeyUsedInCodeHasAString() {
        val have = keys("values")
        val used = projectDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.path.contains("src/main") }
            .flatMap { f -> Regex("""ArmLog\.[diwe]\(\s*[^,()]+,\s*"([a-z_]+)"""").findAll(f.readText()).map { it.groupValues[1] } }
            .toSet() + setOf("compat_item", "compat_item_req")
        val missing = used.filter { "log_$it" !in have }
        assertTrue("Нет строк для ключей журнала: $missing", missing.isEmpty())
    }
}
