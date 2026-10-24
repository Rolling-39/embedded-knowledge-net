package com.rolling.learningguider.data.merge

import com.rolling.learningguider.data.model.BuiltinData
import com.rolling.learningguider.data.model.Job
import com.rolling.learningguider.data.model.JobTier
import com.rolling.learningguider.data.model.LearnPath
import com.rolling.learningguider.data.model.ResourceCategory
import com.rolling.learningguider.data.model.ResourceItem
import com.rolling.learningguider.data.model.Skill
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 合并层语义测试 —— 逐条对齐网页版 buildEffective() 行为 */
class BuildEffectiveTest {

    private fun builtin() = BuiltinData(
        version = "test",
        skills = linkedMapOf(
            "c" to Skill(name = "C语言", cat = "语言基础", diff = 2, content = "c-content", res = listOf("r1"), pre = emptyList()),
            "stm32" to Skill(name = "STM32", cat = "硬件", diff = 3, content = "s-content", pre = listOf("c")),
        ),
        paths = listOf(
            LearnPath(id = "p1", name = "P1", job = "j1", color = "#111111", skills = listOf("c", "stm32", "ghost")),
            LearnPath(id = "p3", name = "P3", job = "j1", color = "#333333", skills = listOf("c")),
        ),
        jobs = linkedMapOf(
            "j1" to Job(id = "j1", name = "J1", level = "基础", diff = 2, demand = 5, salary = "¥10-20K",
                tiers = listOf(JobTier("初级", "¥10-12K", "d"), JobTier("高级", "¥15-20K", "d"))),
            "j2" to Job(id = "j2", name = "J2", level = "高阶", diff = 4, demand = 2, salary = "¥20-40K"),
        ),
        resources = linkedMapOf(
            "books" to ResourceCategory("书", listOf(ResourceItem(n = "书A", d = "dA"))),
        ),
    )

    @Test
    fun `部分覆盖按字段级合并_与网页版assign语义一致`() {
        val user = UserState(
            skillPayloads = mapOf(
                "c" to buildJsonObject { put("name", "C改"); put("user", true) },
            ),
        )
        val e = Merger.buildEffective(builtin(), user)
        val c = e.skills["c"]!!
        assertEquals("C改", c.name)                 // 覆盖字段生效
        assertEquals("c-content", c.content)        // 未覆盖字段保留内置值
        assertEquals(2, c.diff)
        assertEquals(listOf("r1"), c.res)
    }

    @Test
    fun `墓碑隐藏内置项且不当作自定义追加`() {
        val user = UserState(
            skillPayloads = mapOf(
                "stm32" to buildJsonObject { put("deleted", true); put("name", "STM32") },
                "us9" to buildJsonObject { put("name", "自定义"); put("cat", "系统"); put("user", true) },
            ),
        )
        val e = Merger.buildEffective(builtin(), user)
        assertFalse(e.skills.containsKey("stm32"))
        assertTrue(e.skills.containsKey("us9"))
        assertEquals(listOf("c", "us9"), e.skills.keys.toList()) // 自定义追加在内置之后
    }

    @Test
    fun `路径覆盖_技能过滤到存在的技能_墓碑隐藏_自定义追加`() {
        val user = UserState(
            pathPayloads = mapOf(
                // 覆盖 p1：乱序 + 含不存在技能
                "p1" to buildJsonObject {
                    put("user", true); put("skills", kotlinx.serialization.json.JsonArray(listOf(
                        kotlinx.serialization.json.JsonPrimitive("zzz"),
                        kotlinx.serialization.json.JsonPrimitive("stm32"),
                        kotlinx.serialization.json.JsonPrimitive("c"),
                    )))
                },
                // 墓碑 p3
                "p3" to buildJsonObject { put("deleted", true) },
                // 自定义路径（job 存在 → 追加；含不存在技能 → 过滤）
                "up1" to buildJsonObject {
                    put("id", "up1"); put("name", "自定义路线"); put("job", "j1"); put("user", true)
                    put("skills", kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive("c"), kotlinx.serialization.json.JsonPrimitive("us9"))))
                },
            ),
            skillPayloads = mapOf(
                "us9" to buildJsonObject { put("name", "自定义"); put("cat", "系统"); put("user", true) },
            ),
        )
        val e = Merger.buildEffective(builtin(), user)
        assertEquals(2, e.paths.size)
        val p1 = e.paths.first { it.id == "p1" }
        assertEquals(listOf("stm32", "c"), p1.skills) // 顺序保持 payload，ghost/zzz 被过滤
        assertFalse(e.paths.any { it.id == "p3" })
        val up1 = e.paths.first { it.id == "up1" }
        assertEquals(listOf("c", "us9"), up1.skills)
    }

    @Test
    fun `岗位被墓碑后其路径一并隐藏`() {
        val user = UserState(
            jobPayloads = mapOf("j1" to buildJsonObject { put("deleted", true); put("name", "J1") }),
        )
        val e = Merger.buildEffective(builtin(), user)
        assertFalse(e.jobs.containsKey("j1"))
        assertFalse(e.paths.any { it.job == "j1" }) // p1/p3 都挂 j1
    }

    @Test
    fun `自定义资源追加_未知分类归videos`() {
        val user = UserState(
            userResources = listOf(
                ResourceItem(id = "ur1", cat = "books", n = "书B", d = "dB"),
                ResourceItem(id = "ur2", cat = "不存在分类", n = "X", d = "y"),
            ),
        )
        val e = Merger.buildEffective(builtin(), user)
        assertEquals(2, e.resources["books"]?.items?.size)
        assertEquals("书B", e.resources["books"]?.items?.last()?.n)
        assertEquals("视频课程（B站等）", e.resources["videos"]?.title)
        assertEquals(listOf("X"), e.resources["videos"]?.items?.map { it.n })
    }

    @Test
    fun `自定义岗位追加且字段兜底生效`() {
        val user = UserState(
            jobPayloads = mapOf(
                "uj1" to buildJsonObject { put("id", "uj1"); put("name", "我的岗位"); put("user", true); put("diff", 0); put("level", "乱写") },
            ),
        )
        val e = Merger.buildEffective(builtin(), user)
        val j = e.jobs["uj1"]
        assertEquals("我的岗位", j?.name)
        assertEquals("进阶", j?.level)  // 非法 level 兜底
        assertEquals(3, j?.diff)        // diff 越界兜底
        assertEquals(3, j?.demand)      // 缺省兜底
    }

    @Test
    fun `无用户数据时原样返回内置`() {
        val e = Merger.buildEffective(builtin(), UserState())
        assertEquals(2, e.skills.size)
        assertEquals(2, e.paths.size)
        assertEquals(2, e.jobs.size)
        assertNull(e.resources["videos"])
        assertEquals(1, e.resources["books"]?.items?.size)
    }
}
