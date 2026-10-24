package com.rolling.learningguider.data.backup

import com.rolling.learningguider.data.merge.Merger
import com.rolling.learningguider.data.merge.UserState
import com.rolling.learningguider.data.model.BuiltinData
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 与网页版的双向互通验收：
 * 1) 网页版真实导出（夹具由 tools/gen-web-fixture.js 用网页版源码生成）→ App 导入 → 语义断言
 * 2) App 导出 → 再导入（round-trip）→ 数据无损
 * 3) 旧版格式（裸数组 progress + dirId + 字符串 skillDone）兼容
 * 4) 合并模式语义（本地保留 / 新增非墓碑 / 资源按 id 去重）
 * App 导出文件写入 build/test-exports/ 供 tools/check-web-import.js 做网页版吃回验证。
 */
class BackupInteropTest {

    private val builtin: BuiltinData by lazy {
        BackupEngine.json.decodeFromString(
            BuiltinData.serializer(),
            File("src/main/assets/builtin.json").readText(Charsets.UTF_8),
        )
    }

    private fun fixture(): String =
        javaClass.classLoader.getResource("web-export-sample.json")!!.readText(Charsets.UTF_8)

    @Test
    fun `网页版导出导入_语义完全对齐`() {
        val backup = BackupEngine.decode(fixture())
        assertEquals("embed-kb-v9.21", backup.type)
        val (snapshot, _) = BackupEngine.applyImport(builtin, LocalSnapshot(), backup, merge = false)

        // 记录原样落库（覆盖模式保留墓碑）
        assertEquals(setOf("c", "us1001", "dc"), snapshot.skillRecords.keys)
        assertEquals(setOf("path-mcu", "path-fpga"), snapshot.pathRecords.keys)
        assertEquals(setOf("job-x1"), snapshot.jobRecords.keys)
        assertEquals(1, snapshot.userResources.size)

        // 合并成有效数据后的口径 = 网页版仿真报告的口径（81/15/17/63）
        val e = Merger.buildEffective(builtin, snapshot.toUserState())
        assertEquals(81, e.skills.size)
        assertEquals(15, e.paths.size)
        assertEquals(17, e.jobs.size)
        assertEquals(63, e.resources.values.sumOf { it.items.size })
        assertEquals(11, e.resources["books"]?.items?.size)

        // 部分覆盖：只改了 name/content，其余保留内置
        assertEquals("C语言(定制版)", e.skills["c"]?.name)
        assertEquals("按自己的节奏过了一遍", e.skills["c"]?.content)
        assertTrue("部分覆盖不应清空内置 res", e.skills["c"]!!.res.isNotEmpty())
        assertEquals("我的独门技能", e.skills["us1001"]?.name)
        assertFalse("dc 墓碑应隐藏", e.skills.containsKey("dc"))

        // 路径覆盖生效
        assertEquals(listOf("c", "rust", "stm32", "gpio", "uart"), e.paths.first { it.id == "path-mcu" }.skills)
        assertFalse("path-fpga 墓碑应隐藏", e.paths.any { it.id == "path-fpga" })

        // 学习状态与进度
        assertEquals("done", snapshot.skillDone["c"]?.st)
        assertEquals("基础打完", snapshot.skillDone["c"]?.note)
        assertEquals("2026-01-05", snapshot.skillDone["c"]?.startDate)
        assertEquals("2026-06-01", snapshot.skillDone["c"]?.goalDate)
        assertEquals("learning", snapshot.skillDone["rust"]?.st)
        assertEquals(1, snapshot.progress.size)
        assertEquals("job-mcu", snapshot.progress.first().jobId)
    }

    @Test
    fun `App导出再导入_round_trip无损`() {
        val backup = BackupEngine.decode(fixture())
        val (snapshot, _) = BackupEngine.applyImport(builtin, LocalSnapshot(), backup, merge = false)
        val exported = BackupEngine.export("embed-kb-" + builtin.version, snapshot)

        val re = BackupEngine.decode(exported)
        val (snapshot2, _) = BackupEngine.applyImport(builtin, LocalSnapshot(), re, merge = false)
        assertEquals(snapshot.skillRecords.keys, snapshot2.skillRecords.keys)
        assertEquals(snapshot.pathRecords.keys, snapshot2.pathRecords.keys)
        assertEquals(snapshot.jobRecords.keys, snapshot2.jobRecords.keys)
        assertEquals(snapshot.userResources, snapshot2.userResources)
        assertEquals("done", snapshot2.skillDone["c"]?.st)
        assertEquals("2026-01-05", snapshot2.skillDone["c"]?.startDate)
        assertEquals(1, snapshot2.progress.size)

        // 导出文件留给网页版吃回验证（tools/check-web-import.js）
        File("build/test-exports").mkdirs()
        File("build/test-exports/app-export.json").writeText(exported, Charsets.UTF_8)
    }

    @Test
    fun `旧版格式兼容_裸数组dirId与字符串skillDone`() {
        val legacy = """[{"dirId":"mcu","mastery":20,"done":false,"note":"旧格式"},{"dirId":"fpga","mastery":50,"done":true,"note":""}]"""
        val backup = BackupEngine.decode(legacy)
        val (snapshot, _) = BackupEngine.applyImport(builtin, LocalSnapshot(), backup, merge = false)
        assertEquals(listOf("job-mcu" to 20, "job-fpga" to 50), snapshot.progress.map { it.jobId to it.mastery })

        val legacy2 = """{"progress":[{"dirId":"mcu","mastery":20,"done":false,"note":""}],"skillDone":{"c":"done","rust":"learning"},"jobState":{"job-mcu":"learning"}}"""
        val backup2 = BackupEngine.decode(legacy2)
        val (snapshot2, _) = BackupEngine.applyImport(builtin, LocalSnapshot(), backup2, merge = false)
        assertEquals("done", snapshot2.skillDone["c"]?.st)
        assertEquals(0L, snapshot2.skillDone["c"]?.updated)
        assertEquals("learning", snapshot2.skillDone["rust"]?.st)
        assertEquals("learning", snapshot2.jobState["job-mcu"]?.st)
    }

    @Test
    fun `合并模式_本地保留_新增非墓碑_资源按id去重`() {
        val local = LocalSnapshot(
            skillRecords = mapOf("c" to buildJsonObject { put("name", "本地版本"); put("user", true) }),
            userResources = listOf(com.rolling.learningguider.data.model.ResourceItem(id = "ur2001", cat = "books", n = "本地书", d = "d")),
            skillDone = mapOf("c" to com.rolling.learningguider.data.model.SkillDone(st = "learning")),
        )
        val backup = BackupEngine.decode(fixture())
        val (merged, stats) = BackupEngine.applyImport(builtin, local, backup, merge = true)

        // 本地已有 c → 保留本地版本
        assertTrue(merged.skillRecords["c"]!!.toString().contains("本地版本"))
        // 新增：us1001（非墓碑）；dc 是墓碑 → 不合并
        assertTrue(merged.skillRecords.containsKey("us1001"))
        assertFalse("墓碑不应被合并导入", merged.skillRecords.containsKey("dc"))
        assertTrue(merged.pathRecords.containsKey("path-mcu"))
        assertFalse(merged.pathRecords.containsKey("path-fpga"))
        assertEquals(1, stats.skills)
        assertEquals(1, stats.paths)
        // 资源按 id 去重（ur2001 本地已有）
        assertEquals(1, merged.userResources.size)
        assertEquals("本地书", merged.userResources.first().n)
        // 进度与状态以导入为准
        assertEquals("done", merged.skillDone["c"]?.st)
        assertEquals(1, merged.progress.size)
    }

    @Test
    fun `非法id被白名单过滤`() {
        val malicious = """
            {"progress":[],"skillDone":{},
             "userSkills":{"<img>": {"name":"x","user":true},"ok_1": {"name":"合法","user":true}},
             "userPaths":{},"userJobs":{},
             "userRes":[{"id":"a b","cat":"books","n":"x","d":"y"},{"id":"ok9","cat":"books","n":"y","d":"z"}]}
        """.trimIndent()
        val backup = BackupEngine.decode(malicious)
        val (snapshot, _) = BackupEngine.applyImport(builtin, LocalSnapshot(), backup, merge = false)
        assertEquals(setOf("ok_1"), snapshot.skillRecords.keys)
        assertEquals(1, snapshot.userResources.size)
        assertEquals("y", snapshot.userResources.first().n)
    }
}
