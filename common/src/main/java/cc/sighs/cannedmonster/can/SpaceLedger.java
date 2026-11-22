package cc.sighs.cannedmonster.can;

import cc.sighs.cannedmonster.platform.EntityStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent ledger for spatial cans. A space owns its room data and a set of
 * real residents; the external anchor is only a reference used to locate a
 * live exit. Anchors are resolved from carriers instead of item stack identity
 * so item copies, pickups, containers and logouts all go through the same
 * ambiguity rules.
 */
public final class SpaceLedger {
    public static final int MAX_RESIDENT_BYTES = 262144;
    private static final int MAX_ROOMS = 1024;

    public record Anchor(String carrier, String dimension, double x, double y, double z, UUID parent) {
        public Anchor {
            if (carrier == null || carrier.isEmpty()) throw new IllegalArgumentException("Anchor carrier is required");
            if (dimension == null || dimension.isEmpty()) throw new IllegalArgumentException("Anchor dimension is required");
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Carrier", carrier);
            tag.putString("Dimension", dimension);
            tag.putDouble("X", x);
            tag.putDouble("Y", y);
            tag.putDouble("Z", z);
            tag.putString("Parent", parent == null ? "" : parent.toString());
            return tag;
        }

        public static Anchor load(CompoundTag tag) {
            String parent = EntityStorage.string(tag, "Parent");
            return new Anchor(EntityStorage.string(tag, "Carrier"), EntityStorage.string(tag, "Dimension"),
                    EntityStorage.decimal(tag, "X"), EntityStorage.decimal(tag, "Y"), EntityStorage.decimal(tag, "Z"),
                    parent.isEmpty() ? null : parseUuid(parent, "anchor parent"));
        }
    }

    public record Origin(String dimension, double x, double y, double z) {
        public Origin {
            if (dimension == null || dimension.isEmpty()) throw new IllegalArgumentException("Room dimension is required");
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", dimension);
            tag.putDouble("X", x);
            tag.putDouble("Y", y);
            tag.putDouble("Z", z);
            return tag;
        }

        public static Origin load(CompoundTag tag) {
            return new Origin(EntityStorage.string(tag, "Dimension"),
                    EntityStorage.decimal(tag, "X"), EntityStorage.decimal(tag, "Y"), EntityStorage.decimal(tag, "Z"));
        }
    }

    public static final class Room {
        private final UUID id;
        private Anchor anchor;
        private Anchor entryAnchor;
        private Origin origin;
        private final Map<UUID, CompoundTag> residents = new LinkedHashMap<>();
        private boolean ambiguous;

        private Room(UUID id) {
            this.id = id;
        }

        public UUID id() { return id; }
        public Anchor anchor() { return anchor; }
        public void anchor(Anchor anchor) { this.anchor = anchor; }
        public Anchor entryAnchor() { return entryAnchor; }
        public void entryAnchor(Anchor anchor) { this.entryAnchor = anchor; }
        public Origin origin() { return origin; }
        public void origin(Origin origin) { this.origin = origin; }
        public boolean ambiguous() { return ambiguous; }
        public Map<UUID, CompoundTag> residents() { return Collections.unmodifiableMap(residents); }
        public int residentCount() { return residents.size(); }

        public CompoundTag resident(UUID resident) {
            CompoundTag data = residents.get(resident);
            return data == null ? null : data.copy();
        }

        public void addResident(UUID resident, CompoundTag data) {
            if (resident == null) throw new IllegalArgumentException("Resident id is required");
            if (data == null) throw new IllegalArgumentException("Resident data is required");
            if (data.sizeInBytes() > MAX_RESIDENT_BYTES) throw new IllegalArgumentException("Resident data exceeds space capacity");
            residents.put(resident, data.copy());
        }

        public boolean removeResident(UUID resident) { return residents.remove(resident) != null; }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id.toString());
            if (anchor != null) tag.put("Anchor", anchor.save());
            if (entryAnchor != null) tag.put("Entry", entryAnchor.save());
            if (origin != null) tag.put("Origin", origin.save());
            ListTag list = new ListTag();
            for (Map.Entry<UUID, CompoundTag> entry : residents.entrySet()) {
                CompoundTag resident = new CompoundTag();
                resident.putString("Id", entry.getKey().toString());
                resident.put("Data", entry.getValue().copy());
                list.add(resident);
            }
            tag.put("Residents", list);
            return tag;
        }
    }

    private final Map<UUID, Room> rooms = new LinkedHashMap<>();
    private final Map<UUID, List<Anchor>> observations = new HashMap<>();
    private final Map<UUID, Anchor> live = new HashMap<>();
    private boolean scanning;
    private boolean dirty;

    public Room create() {
        if (rooms.size() >= MAX_ROOMS) throw new IllegalStateException("Space ledger is full");
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (rooms.containsKey(id));
        Room room = new Room(id);
        rooms.put(id, room);
        dirty = true;
        return room;
    }

    public Room get(UUID id) { return id == null ? null : rooms.get(id); }
    public Collection<Room> rooms() { return Collections.unmodifiableCollection(rooms.values()); }
    public boolean remove(UUID id) {
        if (rooms.remove(id) == null) return false;
        observations.remove(id);
        live.remove(id);
        dirty = true;
        return true;
    }

    public boolean ambiguous(UUID id) {
        Room room = get(id);
        return room != null && room.ambiguous;
    }

    public void beginScan() {
        observations.clear();
        live.clear();
        scanning = true;
    }

    public void observe(UUID space, Anchor anchor) {
        if (!scanning || space == null || anchor == null || !rooms.containsKey(space)) return;
        List<Anchor> list = observations.computeIfAbsent(space, ignored -> new ArrayList<>());
        for (Anchor existing : list) {
            if (existing.carrier().equals(anchor.carrier())) return;
        }
        list.add(anchor);
    }

    public void endScan() {
        scanning = false;
        Map<UUID, Anchor> single = new HashMap<>();
        Map<UUID, UUID> parentOf = new HashMap<>();
        for (Map.Entry<UUID, List<Anchor>> entry : observations.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            parentOf.put(entry.getKey(), entry.getValue().get(0).parent());
            if (entry.getValue().size() == 1) single.put(entry.getKey(), entry.getValue().get(0));
        }
        Set<UUID> cyclic = new HashSet<>();
        for (UUID id : observations.keySet()) {
            Set<UUID> seen = new HashSet<>();
            UUID cursor = id;
            while (cursor != null) {
                if (!seen.add(cursor)) {
                    cyclic.addAll(seen);
                    break;
                }
                cursor = parentOf.get(cursor);
            }
        }
        for (Room room : rooms.values()) {
            List<Anchor> found = observations.get(room.id());
            boolean duplicate = found != null && found.size() > 1;
            boolean cycle = cyclic.contains(room.id());
            room.ambiguous = duplicate || cycle;
            if (found != null && !found.isEmpty()) {
                Anchor anchor = found.get(0);
                if (room.anchor == null || !room.anchor.equals(anchor)) {
                    room.anchor = anchor;
                    dirty = true;
                }
            }
            if (found != null && found.size() == 1 && !cycle) {
                live.put(room.id(), found.get(0));
            }
        }
        if (!observations.isEmpty()) dirty = true;
        observations.clear();
    }

    public Anchor liveAnchor(UUID id) { return live.get(id); }
    public Anchor lastAnchor(UUID id) {
        Room room = get(id);
        return room == null ? null : room.anchor;
    }

    public boolean consumeDirty() {
        boolean value = dirty;
        dirty = false;
        return value;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Room room : rooms.values()) list.add(room.save());
        tag.put("Rooms", list);
        return tag;
    }

    public static SpaceLedger load(CompoundTag tag) {
        SpaceLedger ledger = new SpaceLedger();
        ListTag list = EntityStorage.list(tag, "Rooms");
        Set<UUID> ids = new HashSet<>();
        Set<UUID> residents = new HashSet<>();
        for (Tag child : list) {
            if (!(child instanceof CompoundTag roomTag)) continue;
            UUID id = parseUuid(EntityStorage.string(roomTag, "Id"), "space id");
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate space id: " + id);
            Room room = new Room(id);
            if (roomTag.contains("Anchor")) room.anchor = Anchor.load(EntityStorage.compound(roomTag, "Anchor"));
            if (roomTag.contains("Entry")) room.entryAnchor = Anchor.load(EntityStorage.compound(roomTag, "Entry"));
            if (roomTag.contains("Origin")) room.origin = Origin.load(EntityStorage.compound(roomTag, "Origin"));
            ListTag residentList = EntityStorage.list(roomTag, "Residents");
            for (Tag residentTag : residentList) {
                if (!(residentTag instanceof CompoundTag resident)) continue;
                UUID residentId = parseUuid(EntityStorage.string(resident, "Id"), "resident id");
                if (!residents.add(residentId)) throw new IllegalArgumentException("Duplicate resident id: " + residentId);
                if (!resident.contains("Data")) throw new IllegalArgumentException("Resident data is missing: " + residentId);
                CompoundTag data = EntityStorage.compound(resident, "Data");
                if (data.sizeInBytes() > MAX_RESIDENT_BYTES) throw new IllegalArgumentException("Resident data exceeds space capacity");
                room.residents.put(residentId, data.copy());
            }
            ledger.rooms.put(id, room);
        }
        return ledger;
    }

    private static UUID parseUuid(String value, String what) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException(what + " is required");
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid " + what + ": " + value, exception);
        }
    }
}
