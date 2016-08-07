/*
 * Copyright (C) 2016  Hendrik Borghorst
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package andrompd.org.andrompd.mpdservice.mpdprotocol;

public class MPDCommands {

    public static final String MPD_COMMAND_CLOSE = "close";

    public static final String MPD_COMMAND_PASSWORD = "password ";

    /* Database request commands */
    public static final String MPD_COMMAND_REQUEST_ALBUMS = "list album";
    public static final String MPD_COMMAND_REQUEST_ALBUMS_WITH_MBID = "list album group MUSICBRAINZ_ALBUMID";


    public static String MPD_COMMAND_REQUEST_ARTIST_ALBUMS(String artistName) {
        return "list album \"" + artistName + "\"";
    }

    public static String MPD_COMMAND_REQUEST_ARTIST_ALBUMS_WITH_MBID(String artistName) {
        return "list album artist \"" + artistName + "\" group MUSICBRAINZ_ALBUMID";
    }

    public static String MPD_COMMAND_REQUEST_ALBUM_TRACKS(String albumName) {
        return "find album \"" + albumName + "\"";
    }

    public static final String MPD_COMMAND_REQUEST_ARTISTS = "list artist";

    public static final String MPD_COMMAND_REQUEST_ALL_FILES = "listallinfo";

    /* Control commands */
    public static String MPD_COMMAND_PAUSE(boolean pause) {
        return "pause " + (pause ? "1" : "0");
    }

    public static final String MPD_COMMAND_NEXT = "next";
    public static final String MPD_COMMAND_PREVIOUS = "previous";
    public static final String MPD_COMMAND_STOP = "stop";

    public static final String MPD_COMMAND_GET_CURRENT_STATUS = "status";

    public static final String MPD_COMMAND_GET_CURRENT_PLAYLIST = "playlistinfo";

    public static String MPD_COMMAND_GET_SAVED_PLAYLIST(String playlistName) {
        return "listplaylistinfo \"" + playlistName + "\"";
    }

    public static final String MPD_COMMAND_GET_CURRENT_SONG = "currentsong";

    public static final String MPD_COMMAND_START_IDLE = "idle";
    public static final String MPD_COMMAND_STOP_IDLE = "noidle";

    public static final String MPD_START_COMMAND_LIST = "command_list_begin";
    public static final String MPD_END_COMMAND_LIST = "command_list_end";

    public static  String MPD_COMMAND_ADD_FILE(String url) {
        return "add \"" + url + "\"";
    }

    public static  String MPD_COMMAND_ADD_FILE_AT_INDEX(String url, int index) {
        return "addid \"" + url + "\"  " + String.valueOf(index);
    }

    public static String MPD_COMMAND_REMOVE_SONG_FROM_CURRENT_PLAYLIST(int index) {
        return "delete " + String.valueOf(index);
    }

    public static String MPD_COMMAND_MOVE_SONG_FROM_INDEX_TO_INDEX(int from, int to) {
        return "move " + String.valueOf(from) + ' ' + String.valueOf(to);
    }

    public static final String MPD_COMMAND_CLEAR_PLAYLIST = "clear";

    public static String MPD_COMMAND_SET_RANDOM(boolean random) {
        return "random " + (random ? "1" : "0");
    }

    public static String MPD_COMMAND_SET_REPEAT(boolean repeat) {
        return "repeat " + (repeat ? "1" : "0");
    }

    public static String MPD_COMMAND_SET_SINGLE(boolean single) {
        return "single " + (single ? "1" : "0");
    }

    public static String MPD_COMMAND_SET_CONSUME(boolean consume) {
        return "consume " + (consume ? "1" : "0");
    }


    public static String MPD_COMMAND_PLAY_SONG_INDEX(int index) {
        return "play " + String.valueOf(index);
    }

    public static String MPD_COMMAND_SEEK_SECONDS(int index, int seconds) {
        return "seek " + String.valueOf(index) + ' ' + String.valueOf(seconds);
    }

    public static String MPD_COMMAND_SET_VOLUME(int volume) {
        if ( volume > 100 ) {
            volume = 100;
        } else if ( volume < 0 ) {
            volume = 0;
        }
        return "setvol " + volume;
    }

}
