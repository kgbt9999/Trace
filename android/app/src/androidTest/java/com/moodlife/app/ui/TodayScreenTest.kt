package com.moodlife.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moodlife.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TodayScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun todayTab_showsRussianTitle() {
        composeRule.onNodeWithText("Сегодня").assertIsDisplayed()
    }

    @Test
    fun bottomNav_showsAllTabs() {
        composeRule.onNodeWithText("Календарь").assertIsDisplayed()
        composeRule.onNodeWithText("Отчёты").assertIsDisplayed()
        composeRule.onNodeWithText("Прогноз").assertIsDisplayed()
        composeRule.onNodeWithText("Настройки").assertIsDisplayed()
        composeRule.onNodeWithText("Источники").assertIsDisplayed()
    }
}
