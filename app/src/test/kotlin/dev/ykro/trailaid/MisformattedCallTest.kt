package dev.ykro.trailaid

import dev.ykro.trailaid.agent.parseMisformattedCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** LiteRT-LM rejects tool calls whose strings use plain quotes; the runtime recovers them from the error text. */
class MisformattedCallTest {
  @Test
  fun `recovers a double-quoted skill call from the runtime error`() {
    val msg = "Failed to call nativeSendMessage: INVALID_ARGUMENT: Failed to parse tool calls from code block: call:load_skill{skill_name=\"bleeding\"}\nfull response: <|tool_call>call:load_skill{skill_name=\"bleeding\"}<tool_call|>"
    val (name, args) = parseMisformattedCall(msg)!!
    assertEquals("load_skill", name)
    assertEquals(mapOf("skill_name" to "bleeding"), args)
  }

  @Test
  fun `handles single quotes, markers, colons and several arguments`() {
    val (name, args) = parseMisformattedCall("call:load_skill_resource{skill_name:'bleeding', path:<|\"|>assets/tourniquet.md<|\"|>}")!!
    assertEquals("load_skill_resource", name)
    assertEquals("bleeding", args["skill_name"])
    assertEquals("assets/tourniquet.md", args["path"])
    val (n2, a2) = parseMisformattedCall("call:start_named_timer{name=<|\"|>tourniquet<|\"|>}")!!
    assertEquals("start_named_timer", n2)
    assertEquals("tourniquet", a2["name"])
  }

  @Test
  fun `ignores unrelated errors`() {
    assertNull(parseMisformattedCall("DEADLINE_EXCEEDED: Session 1 did not complete"))
  }
}
