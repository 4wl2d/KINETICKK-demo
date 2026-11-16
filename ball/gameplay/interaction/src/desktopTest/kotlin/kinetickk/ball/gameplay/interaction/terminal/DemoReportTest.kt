// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.gameplay.interaction.terminal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kinetickk.ball.gameplay.interaction.input.GameplayInput
import kinetickk.ball.gameplay.interaction.overlay.TerminalContent
import kinetickk.ball.gameplay.interaction.overlay.TerminalPresentation
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.foundation.design.LocalAppLanguage
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DemoReportTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun victoryExplainsTheDemoBoundaryAndAllowsReplay() {
        val victory = mutableStateOf(false)
        val actions = mutableListOf<GameplayInput>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalAppLanguage provides AppLanguage.English) {
                Box(Modifier.requiredSize(1000.dp, 720.dp)) {
                    TerminalContent(
                        TerminalPresentation(
                            victory = victory.value,
                            reason = "Core fractured",
                            time = "12:01",
                            kills = "400",
                            matter = "1200",
                            weapon = "Flux Wake",
                            combat = emptyList(),
                            collection = emptyList(),
                        ),
                        textScale = 1f,
                        statisticsOnLeft = false,
                        elapsed = { 3f },
                        enabled = true,
                        onInput = actions::add,
                    )
                }
            }
        }
        val notice = compose.onNodeWithTag("kinetickk.gameplay.results.demo-complete")
        notice.assertDoesNotExist()

        compose.runOnIdle { victory.value = true }

        notice.assertIsDisplayed().assertTextEquals("The demo ends here. Continue in the full game.")
        val replay = compose.onNodeWithTag("kinetickk.gameplay.restart")
        replay.assertIsDisplayed().performClick()
        replay.performClick()
        val home = compose.onNodeWithTag("kinetickk.gameplay.exit")
        home.assertIsDisplayed().performClick()

        compose.runOnIdle {
            assertEquals(listOf(GameplayInput.RestartRun, GameplayInput.RestartRun, GameplayInput.ExitToHome), actions)
        }
    }
}
