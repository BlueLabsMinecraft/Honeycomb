package dev.bluelabs.honeycomb.panel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The naming rules behind the "Create new" button.
 */
class ProfileIdsTest {

    @Test
    void plainNamesSurvive() {
        assertEquals("nether", ProfilesPanel.sanitiseId("nether"));
        assertEquals("staff-board", ProfilesPanel.sanitiseId("Staff Board"));
    }

    @Test
    void spacesBecomeSingleDashes() {
        assertEquals("pvp-arena", ProfilesPanel.sanitiseId("  pvp   arena  "));
    }

    @Test
    void symbolsAreStrippedInsteadOfBreakingTheSectionName() {
        assertEquals("sky-block", ProfilesPanel.sanitiseId("sky/block!"));
        assertEquals("a-b", ProfilesPanel.sanitiseId("a.b"));
    }

    @Test
    void nothingUsableComesBackEmptySoThePlayerIsAskedAgain() {
        assertEquals("", ProfilesPanel.sanitiseId(""));
        assertEquals("", ProfilesPanel.sanitiseId("   "));
        assertEquals("", ProfilesPanel.sanitiseId("///"));
        assertEquals("", ProfilesPanel.sanitiseId(null));
    }

    @Test
    void edgesAreTrimmed() {
        assertEquals("mid", ProfilesPanel.sanitiseId("--mid--"));
    }
}
