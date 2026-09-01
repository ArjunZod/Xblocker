package com.antigravity.shieldx.classifier

import com.antigravity.shieldx.classifier.ai.AIResponseValidator
import com.antigravity.shieldx.core.model.Category
import com.antigravity.shieldx.core.model.PolicyDecision
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ClassifierTests {

    private lateinit var ruleClassifier: RuleClassifier
    private lateinit var urlClassifier: UrlClassifier
    private lateinit var localContentClassifier: LocalContentClassifier
    private lateinit var aiResponseValidator: AIResponseValidator

    @Before
    fun setUp() {
        ruleClassifier = RuleClassifier()
        urlClassifier = UrlClassifier(ruleClassifier)
        localContentClassifier = LocalContentClassifier(ruleClassifier, urlClassifier)
        aiResponseValidator = AIResponseValidator()
    }

    @Test
    fun testRuleClassifierExplicitText() {
        val result = ruleClassifier.classifyText("Watch free hardcore porn videos")
        assertTrue(result.isExplicit)
        assertEquals(Category.PORNOGRAPHY, result.category)
        assertTrue(result.confidence > 0.8f)
    }

    @Test
    fun testRuleClassifierCleanText() {
        val result = ruleClassifier.classifyText("Quantum computing algorithms in physics")
        assertFalse(result.isExplicit)
        assertEquals(Category.SAFE, result.category)
    }

    @Test
    fun testUrlClassifierPathAndQuery() {
        val explicitPath = urlClassifier.classifyUrl("https://example.com/videos/hentai/play.mp4")
        assertEquals(PolicyDecision.BLOCK, explicitPath.decision)
        assertEquals(Category.PORNOGRAPHY, explicitPath.category)

        val explicitQuery = urlClassifier.classifyUrl("https://searchengine.com/search?q=free+pornography")
        assertEquals(PolicyDecision.BLOCK, explicitQuery.decision)
    }

    @Test
    fun testAIResponseValidatorValidJson() {
        val json = """
            {
              "decision": "BLOCK",
              "category": "EXPLICIT",
              "confidence": 0.95,
              "reason_code": "PORNOGRAPHIC_TEXT_DETECTED"
            }
        """.trimIndent()

        val validated = aiResponseValidator.validateJson(json)
        assertNotNull(validated)
        assertEquals(PolicyDecision.BLOCK, validated?.decision)
        assertEquals(Category.PORNOGRAPHY, validated?.category)
        assertEquals(0.95f, validated?.confidence)
        assertEquals("PORNOGRAPHIC_TEXT_DETECTED", validated?.reasonCode)
    }

    @Test
    fun testAIResponseValidatorMarkdownWrappedJson() {
        val markdownJson = """
            ```json
            {
              "decision": "ALLOW",
              "category": "SAFE",
              "confidence": 0.99,
              "reason_code": "EDUCATIONAL_CONTENT"
            }
            ```
        """.trimIndent()

        val validated = aiResponseValidator.validateJson(markdownJson)
        assertNotNull(validated)
        assertEquals(PolicyDecision.ALLOW, validated?.decision)
        assertEquals(Category.SAFE, validated?.category)
    }

    @Test
    fun testAIResponseValidatorMalformedJsonRejected() {
        val invalidJson = "This is not JSON, it is arbitrary text."
        val validated = aiResponseValidator.validateJson(invalidJson)
        assertNull(validated)
    }

    @Test
    fun testAIResponseValidatorInvalidConfidenceBounds() {
        val outOfBounds = """
            {
              "decision": "BLOCK",
              "category": "EXPLICIT",
              "confidence": 1.5,
              "reason_code": "INVALID_CONFIDENCE"
            }
        """.trimIndent()
        val validated = aiResponseValidator.validateJson(outOfBounds)
        assertNull(validated)
    }
}
