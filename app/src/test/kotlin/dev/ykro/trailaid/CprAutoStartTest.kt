package dev.ykro.trailaid

import dev.ykro.trailaid.agent.isCprSkillLoad
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CprAutoStartTest {
  @Test fun cprSkillLoadIsDetected() = assertTrue(isCprSkillLoad("load_skill", mapOf("skill_name" to "cpr-adult")))

  @Test fun quotedSkillNameFromRecoveryPathIsDetected() = assertTrue(isCprSkillLoad("load_skill", mapOf("skill_name" to "\"cpr-adult\"")))

  @Test fun otherSkillsDoNotStartTheMetronome() = assertFalse(isCprSkillLoad("load_skill", mapOf("skill_name" to "bleeding")))

  @Test fun otherToolsDoNotStartTheMetronome() = assertFalse(isCprSkillLoad("start_named_timer", mapOf("name" to "cpr-adult")))
}
