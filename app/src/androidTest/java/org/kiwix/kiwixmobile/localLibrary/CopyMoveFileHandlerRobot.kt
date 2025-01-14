/*
 * Kiwix Android
 * Copyright (c) 2024 Kiwix <android.kiwix.org>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.kiwix.kiwixmobile.localLibrary

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.web.sugar.Web
import androidx.test.espresso.web.webdriver.DriverAtoms
import androidx.test.espresso.web.webdriver.Locator
import applyWithViewHierarchyPrinting
import com.adevinta.android.barista.interaction.BaristaSleepInteractions
import junit.framework.AssertionFailedError
import org.kiwix.kiwixmobile.BaseRobot
import org.kiwix.kiwixmobile.Findable
import org.kiwix.kiwixmobile.Findable.StringId.TextId
import org.kiwix.kiwixmobile.R.id
import org.kiwix.kiwixmobile.core.R
import org.kiwix.kiwixmobile.testutils.TestUtils
import org.kiwix.kiwixmobile.testutils.TestUtils.testFlakyView

fun copyMoveFileHandler(func: CopyMoveFileHandlerRobot.() -> Unit) =
  CopyMoveFileHandlerRobot().applyWithViewHierarchyPrinting(func)

class CopyMoveFileHandlerRobot : BaseRobot() {

  fun assertCopyMovePermissionDialogDisplayed() {
    isVisible(TextId(R.string.move_files_permission_dialog_title))
  }

  fun assertCopyMoveDialogDisplayed() {
    isVisible(TextId(R.string.copy_move_files_dialog_description))
  }

  fun clickOnCopy() {
    testFlakyView({
      onView(withText(R.string.copy)).perform(click())
    })
  }

  fun clickOnMove() {
    testFlakyView({
      onView(withText(R.string.move)).perform(click())
    })
  }

  fun assertZimFileCopiedAndShowingIntoTheReader() {
    pauseForBetterTestPerformance()
    isVisible(Findable.ViewId(id.readerFragment))
    testFlakyView({
      Web.onWebView()
        .withElement(
          DriverAtoms.findElement(
            Locator.XPATH,
            "//*[contains(text(), 'Android_(operating_system)')]"
          )
        )
    })
  }

  fun assertZimFileAddedInTheLocalLibrary() {
    try {
      onView(ViewMatchers.withId(id.file_management_no_files)).check(
        ViewAssertions.matches(
          ViewMatchers.isDisplayed()
        )
      )
      throw RuntimeException("ZimFile not added in the local library")
    } catch (e: AssertionFailedError) {
      // do nothing zim file is added in the local library
    }
  }

  fun pauseForBetterTestPerformance() {
    BaristaSleepInteractions.sleep(TestUtils.TEST_PAUSE_MS_FOR_SEARCH_TEST.toLong())
  }
}
