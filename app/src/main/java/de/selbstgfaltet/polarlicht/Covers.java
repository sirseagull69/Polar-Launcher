package de.selbstgfaltet.polarlicht;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holt Spielbilder aus dem offenen Bilderarchiv von libretro (dasselbe, das RetroArch nutzt).
 * Gesucht wird nach dem Dateinamen des Spiels. Reines Java, damit es sich am PC prüfen lässt.
 */
final class Covers {
    private static final Map<String, String> SYS = new HashMap<>();

    private static void put(String repo, String... ids) {
        for (String id : ids) {
            SYS.put(id, repo);
        }
    }

    static {
        put("Nintendo_-_Nintendo_DS", "nds");
        put("Nintendo_-_Nintendo_DSi", "dsi");
        put("Nintendo_-_Nintendo_3DS", "n3ds");
        put("Nintendo_-_Game_Boy_Advance", "gba");
        put("Nintendo_-_Game_Boy", "gb", "sgb");
        put("Nintendo_-_Game_Boy_Color", "gbc");
        put("Nintendo_-_Nintendo_64", "n64");
        put("Nintendo_-_Nintendo_Entertainment_System", "nes", "famicom");
        put("Nintendo_-_Family_Computer_Disk_System", "fds");
        put("Nintendo_-_Super_Nintendo_Entertainment_System", "snes", "snesna", "sfc");
        put("Nintendo_-_GameCube", "gc");
        put("Nintendo_-_Wii", "wii");
        put("Nintendo_-_Wii_U", "wiiu");
        put("Nintendo_-_Virtual_Boy", "virtualboy");
        put("Nintendo_-_Pokemon_Mini", "pokemini");
        put("Sony_-_PlayStation", "psx");
        put("Sony_-_PlayStation_2", "ps2");
        put("Sony_-_PlayStation_3", "ps3");
        put("Sony_-_PlayStation_Portable", "psp");
        put("Sony_-_PlayStation_Vita", "psvita");
        put("Sega_-_Mega_Drive_-_Genesis", "genesis", "megadrive", "megadrivejp");
        put("Sega_-_Master_System_-_Mark_III", "mastersystem", "mark3");
        put("Sega_-_Game_Gear", "gamegear");
        put("Sega_-_Dreamcast", "dreamcast");
        put("Sega_-_Saturn", "saturn", "saturnjp");
        put("Sega_-_Mega-CD_-_Sega_CD", "segacd", "megacd", "megacdjp");
        put("Sega_-_32X", "sega32x", "sega32xjp", "sega32xna");
        put("NEC_-_PC_Engine_-_TurboGrafx_16", "pcengine", "tg16");
        put("SNK_-_Neo_Geo_Pocket_Color", "ngpc");
        put("SNK_-_Neo_Geo", "neogeo");
        put("Bandai_-_WonderSwan_Color", "wonderswancolor");
        put("Atari_-_2600", "atari2600");
        put("Atari_-_Lynx", "atarilynx");
        put("Microsoft_-_Xbox", "xbox");
        put("MAME", "mame", "arcade");
        put("FBNeo_-_Arcade_Games", "fbneo", "fba");
    }

    private Covers() {
    }

    static boolean knows(String sys) {
        return SYS.containsKey(sys);
    }

    /** Dateiname ohne Endung. */
    static String base(String path) {
        String n = new File(path).getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    static File file(File dir, String sys, String path) {
        return new File(dir, sys + "/" + base(path) + ".png");
    }

    static File miss(File dir, String sys, String path) {
        return new File(dir, sys + "/" + base(path) + ".fehlt2"); // neue Endung: alte Fehlversuche werden mit den neuen Namen nochmal probiert
    }

    /** Namen, unter denen das Bild im Archiv liegen kann. Erst genau der Dateiname, dann mit den üblichen Regionen. */
    static List<String> names(String path) {
        String b = base(path).replaceAll("[&*/:`<>?\\\\|]", "_");
        List<String> out = new ArrayList<>();
        out.add(b);
        String bare = b.replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", "").trim();
        List<String> bases = new ArrayList<>();
        if (!bare.isEmpty()) bases.add(bare);
        // Archiv schreibt Reihen mit Bindestrich: "Pokemon - Black Version 2", "Mario Kart - ..."
        int sp = bare.indexOf(' ');
        if (sp > 0 && !bare.contains(" - ")) bases.add(bare.substring(0, sp) + " -" + bare.substring(sp));
        for (String base0 : bases) {
            for (String region : new String[] {" (USA)", " (Europe)", " (USA, Europe)", " (World)", " (Germany)", " (Japan)", " (USA, Europe) (NDSi Enhanced)", " (Europe) (NDSi Enhanced)", " (USA) (NDSi Enhanced)"}) {
                if (!out.contains(base0 + region)) {
                    out.add(base0 + region);
                }
            }
        }
        return out;
    }

    static String url(String sys, String name) throws Exception {
        return "https://raw.githubusercontent.com/libretro-thumbnails/" + SYS.get(sys) + "/master/Named_Boxarts/"
            + URLEncoder.encode(name, "UTF-8").replace("+", "%20") + ".png";
    }

    /** Lädt das Bild für ein Spiel. Gibt die Datei zurück oder null, wenn es keins gibt. */
    static File fetch(File dir, String sys, String path) {
        if (!knows(sys)) {
            return null;
        }
        File target = file(dir, sys, path);
        File miss = miss(dir, sys, path);
        if (target.isFile() && target.length() > 0) {
            return target;
        }
        if (miss.isFile() && System.currentTimeMillis() - miss.lastModified() < 7L * 24 * 3600 * 1000) {
            return null;
        }
        target.getParentFile().mkdirs();
        boolean answered = false;
        for (String name : names(path)) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url(sys, name)).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(15000);
                int code = c.getResponseCode();
                answered = true;
                if (code != 200) {
                    continue;
                }
                File tmp = new File(target.getPath() + ".teil");
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[16384];
                    int n;
                    long total = 0;
                    while ((n = in.read(buf)) > 0 && total < 8L * 1024 * 1024) {
                        out.write(buf, 0, n);
                        total += n;
                    }
                }
                if (tmp.length() > 0 && tmp.renameTo(target)) {
                    return target;
                }
            } catch (Exception e) {
                return null; // kein Netz: später noch einmal versuchen, nichts merken
            } finally {
                if (c != null) {
                    c.disconnect();
                }
            }
        }
        if (answered) {
            try {
                new FileOutputStream(miss).close();
                miss.setLastModified(System.currentTimeMillis());
            } catch (Exception ignored) {
                // dann wird beim nächsten Mal wieder gesucht
            }
        }
        return null;
    }
}
