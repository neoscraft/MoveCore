package fr.neoscraft.movecore;

public interface DataStore {
    StorageState load();

    void save(StorageState state);

    void close();
}