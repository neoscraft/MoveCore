package fr.neoscraft.movecore;

import java.time.Instant;
import java.util.UUID;

public record TeleportLog(UUID actor, String actorName, UUID target, String targetName,
                          StoredLocation from, StoredLocation to, Instant at, String reason) {
}