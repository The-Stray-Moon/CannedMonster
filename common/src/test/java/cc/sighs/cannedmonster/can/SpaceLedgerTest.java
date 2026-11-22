package cc.sighs.cannedmonster.can;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpaceLedgerTest {
    private SpaceLedger.Anchor anchor(String carrier, java.util.UUID parent) {
        return new SpaceLedger.Anchor(carrier, "minecraft:overworld", 1, 70, 2, parent);
    }

    @Test void duplicateReferencesCannotResolveAnExit() {
        SpaceLedger ledger = new SpaceLedger();
        var room = ledger.create();
        ledger.beginScan();
        ledger.observe(room.id(), anchor("player:1:0", null));
        ledger.observe(room.id(), anchor("player:1:1", null));
        ledger.endScan();
        assertTrue(room.ambiguous());
        assertNull(ledger.liveAnchor(room.id()));
        ledger.beginScan();
        ledger.observe(room.id(), anchor("item:2", null));
        ledger.endScan();
        assertEquals("item:2", ledger.liveAnchor(room.id()).carrier());
    }

    @Test void missingCarrierRetainsDataButNotALiveExit() {
        SpaceLedger ledger = new SpaceLedger();
        var room = ledger.create();
        ledger.beginScan();
        ledger.observe(room.id(), anchor("block:1", null));
        ledger.endScan();
        ledger.beginScan();
        ledger.endScan();
        assertNotNull(room.anchor());
        assertNull(ledger.liveAnchor(room.id()));
        SpaceLedger restored = SpaceLedger.load(ledger.save());
        assertNotNull(restored.get(room.id()));
        assertEquals(room.anchor(), restored.get(room.id()).anchor());
        assertNull(restored.liveAnchor(room.id()));
    }

    @Test void rejectsSelfAndIndirectContainmentCycles() {
        SpaceLedger ledger = new SpaceLedger();
        var first = ledger.create();
        var second = ledger.create();
        ledger.beginScan();
        ledger.observe(first.id(), anchor("first", second.id()));
        ledger.observe(second.id(), anchor("second", first.id()));
        ledger.endScan();
        assertNull(ledger.liveAnchor(first.id()));
        assertNull(ledger.liveAnchor(second.id()));
        ledger.beginScan();
        ledger.observe(first.id(), anchor("first", first.id()));
        ledger.endScan();
        assertNull(ledger.liveAnchor(first.id()));
    }

    @Test void duplicateSaveEntriesAreRejected() {
        SpaceLedger ledger = new SpaceLedger();
        ledger.create();
        var tag = ledger.save();
        var rooms = cc.sighs.cannedmonster.platform.EntityStorage.list(tag, "Rooms");
        rooms.add(rooms.get(0).copy());
        assertThrows(IllegalArgumentException.class, () -> SpaceLedger.load(tag));
    }
}
