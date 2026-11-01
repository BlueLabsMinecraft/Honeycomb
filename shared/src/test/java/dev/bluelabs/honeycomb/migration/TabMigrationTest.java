package dev.bluelabs.honeycomb.migration;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TabMigrationTest {

    @Test
    void convertsColours() {
        assertEquals("<gold><bold>MY SERVER</bold></gold>", TabMigration.convert("&6&lMY SERVER"));
    }

    @Test
    void keepsTextAfterColourChange() {
        assertEquals("<gray>Welcome, </gray><white>steve</white>", TabMigration.convert("&7Welcome, &fsteve"));
    }

    @Test
    void convertsHex() {
        assertEquals("<color:#FFAA00>Money: </color><white>10</white>", TabMigration.convert("&#FFAA00Money: &f10"));
    }

    @Test
    void dropsTrailingColourCodes() {
        assertEquals("<dark_red>[OWNER]</dark_red>", TabMigration.convert("&4[OWNER]&4"));
    }

    @Test
    void mapsTabPlaceholders() {
        assertEquals("<gray>%player_name%</gray>", TabMigration.convert("&7%player%"));
        assertEquals("<gray>%server_online%</gray>", TabMigration.convert("&7%online%"));
        assertEquals("<gray>%luckperms_primary_group_name%</gray>", TabMigration.convert("&7%group%"));
        assertEquals("<gray>%server_online%</gray>", TabMigration.convert("&7%server_online%"));
    }

    @Test
    void outputParsesAsMiniMessage() {
        MiniMessage mini = MiniMessage.miniMessage();
        for (String line : new String[]{
                "&6&lMY SERVER", "&7Welcome, &f%player%", "&#FFAA00Money: &f%vault_eco_balance%",
                "&4&l[OWNER] &4", "&c&l[ADMIN] &c", "&a[MOD] &a", "&#12ab34&lHex &cRed"}) {
            assertDoesNotThrow(() -> mini.deserialize(TabMigration.convert(line)), line);
        }
    }

    @Test
    void handlesEmptyInput() {
        assertEquals("", TabMigration.convert(""));
        assertEquals("", TabMigration.convert(null));
        assertEquals("", TabMigration.convert("&c"));
    }
}
