package fr.neoscraft.movecore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StorageState {
    public Map<UUID, Map<String, StoredLocation>> homes = new HashMap<>();
    public Map<String, StoredLocation> warps = new HashMap<>();
    public Map<UUID, Map<String, StoredLocation>> playerWarps = new HashMap<>();
    public Map<UUID, StoredLocation> previousLocations = new HashMap<>();
    public StoredLocation spawn;
    public Set<UUID> voidSafePlayers = new HashSet<>();
    public List<TeleportLog> logs = new ArrayList<>();
}