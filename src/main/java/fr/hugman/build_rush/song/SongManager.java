package fr.hugman.build_rush.song;

import net.minecraft.util.Identifier;
import nota.model.Song;
import nota.player.RadioSongPlayer;
import nota.player.SongPlayer;
import nota.utils.NBSDecoder;
import xyz.nucleoid.plasmid.game.GameSpace;

import java.io.IOException;
import java.util.HashMap;

public class SongManager {
    public static final HashMap<Identifier, Song> CACHED_SONGS = new HashMap<>();

    private final GameSpace space;
    private SongPlayer songPlayer;

    public SongManager(GameSpace space) {
        this.space = space;
    }

    private Song get(Identifier identifier) throws IOException {
        if (CACHED_SONGS.containsKey(identifier)) {
            return CACHED_SONGS.get(identifier);
        }
        var path = new Identifier(identifier.getNamespace(), "songs/" + identifier.getPath() + ".nbs");

        var resourceManager = this.space.getServer().getResourceManager();
        var resource = resourceManager.getResource(path);

        if (resource.isEmpty()) {
            throw new IOException("No resource found for " + identifier);
        }

        var song = NBSDecoder.parse(resource.get().getInputStream());
        CACHED_SONGS.put(identifier, song);
        return song;
    }

    public void addSongs(Identifier... identifier) throws IOException {
        Song[] songs = new Song[identifier.length];
        for (int i = 0; i < identifier.length; i++) {
            songs[i] = this.get(identifier[i]);
        }
        this.addSongs(songs);
    }

    public void addSongs(Song... songs) {
        if (this.songPlayer == null) {
            this.songPlayer = new RadioSongPlayer(songs[0]);
            for (int i = 1; i < songs.length; i++) {
                this.songPlayer.getPlaylist().add(songs[i]);
            }
        } else {
            for (Song song : songs) {
                this.songPlayer.getPlaylist().add(song);
            }
        }
    }

    public void refreshPlayers() {
        if(this.songPlayer == null) return;
        for(var player : this.space.getPlayers()) {
            if(this.songPlayer.getPlayerUUIDs().contains(player.getUuid())) continue;
            this.songPlayer.addPlayer(player);
        }
    }

    public void setPlaying(boolean playing) {
        this.songPlayer.setPlaying(playing);
    }
}
