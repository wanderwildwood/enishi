package com.wanderwildwood.enishi.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackToAllTest {
    @Test
    fun backFromAllLeavesTheApp() {
        assertFalse(backGoesToAll(TAB_ALL, hasGroups = true))
        assertFalse(backGoesToAll(TAB_ALL, hasGroups = false))
    }

    @Test
    fun backFromFavoritesOrGroupsGoesToAll() {
        assertTrue(backGoesToAll(TAB_FAVOURITES, hasGroups = false))
        assertTrue(backGoesToAll(TAB_GROUPS, hasGroups = true))
    }

    @Test
    fun aGroupsTabWithNoGroupsLeftAlreadyShowsAllSoBackLeaves() {
        assertFalse(backGoesToAll(TAB_GROUPS, hasGroups = false))
    }
}
