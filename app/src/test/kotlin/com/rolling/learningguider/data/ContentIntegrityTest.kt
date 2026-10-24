package com.rolling.learningguider.data

import com.rolling.learningguider.data.backup.BackupEngine
import com.rolling.learningguider.data.model.BuiltinData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 内置内容完整性校验 —— 移植网页版 tools/verify.js 的内容校验段。
 * builtin.json 由网页版导出，本测试保证进入 App 的内容同样通过网页版口径的检查。
 * 注意：数量断言随内容版本更新（网页版改内容 → 重新导出 → 同步更新这里的数字）。
 */
class ContentIntegrityTest {

    private val builtin: BuiltinData by lazy {
        val file = File("src/main/assets/builtin.json")
        BackupEngine.json.decodeFromString(BuiltinData.serializer(), file.readText(Charsets.UTF_8))
    }

    @Test
    fun `数量与版本`() {
        assertEquals("v9.21", builtin.version)
        assertEquals(81, builtin.skills.size)
        assertEquals(16, builtin.paths.size)
        assertEquals(16, builtin.jobs.size)
        assertEquals(62, builtin.resources.values.sumOf { it.items.size })
    }

    @Test
    fun `路径引用合法且前置不断链`() {
        builtin.paths.forEach { p ->
            val seen = mutableSetOf<String>()
            p.skills.forEach { sid ->
                val skill = builtin.skills[sid]
                assertTrue("路径 ${p.id} 引用了不存在的技能 $sid", skill != null)
                skill!!.pre.forEach { pre ->
                    if (builtin.skills.containsKey(pre)) {
                        assertTrue(
                            "断链：${p.id} 中 [${skill.name}] 的前置 [${builtin.skills[pre]!!.name}] 未在路径更早位置",
                            seen.contains(pre),
                        )
                    }
                }
                seen.add(sid)
            }
        }
    }

    @Test
    fun `无孤立技能`() {
        val inPath = builtin.paths.flatMap { it.skills }.toSet()
        val orphans = builtin.skills.keys.filter { it !in inPath }
        assertEquals("孤立技能（不在任何路径）: $orphans", 0, orphans.size)
    }

    @Test
    fun `岗位薪资上限不低于高级档`() {
        builtin.jobs.values.forEach { j ->
            val top = j.tiers.lastOrNull() ?: return@forEach
            val jobTop = salaryTop(j.salary)
            val tierTop = salaryTop(top.s)
            assertTrue("岗位 ${j.name} salary=${j.salary} 低于高级档 ${top.s}", jobTop >= tierTop)
        }
    }

    @Test
    fun `难度与需求星级范围合法`() {
        builtin.skills.values.forEach { assertTrue("技能 ${it.name} diff 越界: ${it.diff}", it.diff in 1..5) }
        builtin.jobs.values.forEach {
            assertTrue("岗位 ${it.name} diff 越界: ${it.diff}", it.diff in 1..5)
            assertTrue("岗位 ${it.name} demand 越界: ${it.demand}", it.demand in 1..5)
        }
    }

    /** "¥12-55K" → 55 */
    private fun salaryTop(s: String): Int =
        Regex("\\d+").findAll(s).map { it.value.toInt() }.lastOrNull() ?: 0
}
