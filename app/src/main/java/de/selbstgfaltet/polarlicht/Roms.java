package de.selbstgfaltet.polarlicht;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spiele aus ROM-Ordnern: findet sie, ordnet sie Systemen zu und baut den Startbefehl für den Emulator.
 * Welches System welche Dateien und welche Emulatoren kennt, steht in den Regeldateien von ES-DE
 * (assets/es_systems.xml und es_find_rules.xml, MIT-Lizenz). Diese Klasse braucht kein Android,
 * damit man sie am PC testen kann.
 */
final class Roms {
    static final class Cmd {
        String label;
        String emulator;
        String template;
    }

    static final class Sys {
        String name;
        String full;
        final Set<String> ext = new HashSet<>();
        final List<Cmd> cmds = new ArrayList<>();
    }

    static final class Game {
        String label;
        String path;
        String root;
        String sys;
        String sysFull;
        String cover;
        String art;

        String key() {
            return "g:" + path;
        }
    }

    /** Ein fertiger Startbefehl, noch ohne Android-Klassen. */
    static final class Launch {
        String label;
        String component;
        String action;
        String category;
        String mime;
        String data;
        boolean dataIsProvider;
        boolean clearTask;
        boolean clearTop;
        final Map<String, String> extras = new LinkedHashMap<>();
        final Map<String, Boolean> bools = new LinkedHashMap<>();
        final Map<String, Integer> ints = new LinkedHashMap<>();
        final Map<String, String[]> arrays = new LinkedHashMap<>();
    }

    final Map<String, List<String>> emulators = new HashMap<>();
    final Map<String, Sys> systems = new LinkedHashMap<>();

    private static String tag(String xml, String name) {
        Matcher m = Pattern.compile("<" + name + ">([\\s\\S]*?)</" + name + ">").matcher(xml);
        return m.find() ? unescape(m.group(1).trim()) : "";
    }

    private static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'");
    }

    void load(String findRules, String systemsXml) {
        emulators.clear();
        systems.clear();
        Matcher e = Pattern.compile("<emulator name=\"([^\"]+)\">([\\s\\S]*?)</emulator>").matcher(findRules);
        while (e.find()) {
            List<String> list = new ArrayList<>();
            Matcher entry = Pattern.compile("<entry>([^<]+)</entry>").matcher(e.group(2));
            while (entry.find()) {
                String c = entry.group(1).trim();
                if (c.contains("/")) {
                    list.add(c);
                }
            }
            emulators.put(e.group(1), list);
        }
        Matcher s = Pattern.compile("<system>([\\s\\S]*?)</system>").matcher(systemsXml);
        while (s.find()) {
            String body = s.group(1);
            Sys sys = new Sys();
            sys.name = tag(body, "name");
            sys.full = tag(body, "fullname");
            for (String x : tag(body, "extension").split("\\s+")) {
                if (x.startsWith(".")) {
                    sys.ext.add(x.substring(1).toLowerCase(Locale.ROOT));
                }
            }
            Matcher c = Pattern.compile("<command(?: label=\"([^\"]*)\")?>([^<]+)</command>").matcher(body);
            while (c.find()) {
                String template = unescape(c.group(2).trim());
                Matcher em = Pattern.compile("^%EMULATOR_([^%]+)%").matcher(template);
                if (!em.find() || template.contains("%INJECT%") || template.contains("%FILEINJECT%") || template.contains("%ANDROIDAPP%")) {
                    continue;
                }
                Cmd cmd = new Cmd();
                cmd.label = c.group(1) == null ? em.group(1) : unescape(c.group(1));
                cmd.emulator = em.group(1);
                cmd.template = template;
                sys.cmds.add(cmd);
            }
            if (!sys.name.isEmpty() && !sys.ext.isEmpty() && !sys.cmds.isEmpty()) {
                systems.put(sys.name, sys);
            }
        }
    }

    /* ---------- Suchen ---------- */

    private static final String[] ROOT_NAMES = {"ROMs", "Roms", "roms", "ROMS", "Games", "Spiele"};
    private static final Set<String> NOT_GAMES = new HashSet<>(java.util.Arrays.asList(
            "sav", "srm", "state", "txt", "xml", "png", "jpg", "jpeg", "webp", "cfg", "ini", "nfo", "dat", "db", "mp4", "pdf", "json", "bak", "dsv"));

    /** Die Ordner, in denen Spiele liegen können: ein Ordner "ROMs" (oder ähnlich) direkt auf einem Speicher oder eine Ebene tiefer. */
    static List<File> findRoots(List<File> volumes, String chosen) {
        List<File> roots = new ArrayList<>();
        if (chosen != null && !chosen.isEmpty() && new File(chosen).isDirectory()) {
            roots.add(new File(chosen));
        }
        for (File v : volumes) {
            for (String n : ROOT_NAMES) {
                File f = new File(v, n);
                if (f.isDirectory() && !contains(roots, f)) {
                    roots.add(f);
                }
            }
            File[] kids = v.listFiles();
            if (kids == null) {
                continue;
            }
            for (File k : kids) {
                if (!k.isDirectory() || k.getName().startsWith(".") || k.getName().equals("Android")) {
                    continue;
                }
                for (String n : ROOT_NAMES) {
                    File f = new File(k, n);
                    if (f.isDirectory() && !contains(roots, f)) {
                        roots.add(f);
                    }
                }
            }
        }
        return roots;
    }

    private static boolean contains(List<File> list, File f) {
        for (File x : list) {
            if (x.getAbsolutePath().equalsIgnoreCase(f.getAbsolutePath())) {
                return true;
            }
        }
        return false;
    }

    List<Game> scan(List<File> roots, List<File> mediaDirs) {
        List<Game> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (File root : roots) {
            File[] dirs = root.listFiles();
            if (dirs == null) {
                continue;
            }
            for (File dir : dirs) {
                Sys sys = systems.get(dir.getName().toLowerCase(Locale.ROOT));
                if (sys == null || !dir.isDirectory()) {
                    continue;
                }
                collect(dir, sys, root, mediaDirs, out, seen, 0);
            }
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    private void collect(File dir, Sys sys, File root, List<File> mediaDirs, List<Game> out, Set<String> seen, int depth) {
        File[] files = dir.listFiles();
        if (files == null || out.size() > 5000) {
            return;
        }
        boolean hasPlaylist = false;
        for (File f : files) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".m3u") || n.endsWith(".cue") || n.endsWith(".gdi")) {
                hasPlaylist = true;
            }
        }
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith(".")) {
                continue;
            }
            if (f.isDirectory()) {
                if (depth < 2 && !name.equalsIgnoreCase("media") && !name.equalsIgnoreCase("saves")) {
                    collect(f, sys, root, mediaDirs, out, seen, depth + 1);
                }
                continue;
            }
            int dot = name.lastIndexOf('.');
            if (dot <= 0) {
                continue;
            }
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (!sys.ext.contains(ext) || NOT_GAMES.contains(ext)) {
                continue;
            }
            // Zu einer .cue oder .m3u gehören .bin/.img, die sind kein eigenes Spiel
            if (hasPlaylist && (ext.equals("bin") || ext.equals("img") || ext.equals("raw") || (ext.equals("chd") && hasM3u(files)))) {
                continue;
            }
            String base = name.substring(0, dot);
            if (!seen.add(sys.name + "/" + base.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Game g = new Game();
            g.path = f.getAbsolutePath();
            g.root = root.getAbsolutePath();
            g.sys = sys.name;
            g.sysFull = sys.full;
            g.label = clean(base);
            g.cover = media(f, base, sys.name, mediaDirs, "covers", "miximages", "3dboxes", "screenshots", "titlescreens");
            g.art = media(f, base, sys.name, mediaDirs, "fanart", "screenshots", "titlescreens", "miximages");
            out.add(g);
        }
    }

    private static boolean hasM3u(File[] files) {
        for (File f : files) {
            if (f.getName().toLowerCase(Locale.ROOT).endsWith(".m3u")) {
                return true;
            }
        }
        return false;
    }

    static String clean(String base) {
        String s = base.replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", "").replace('_', ' ').replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? base : s;
    }

    private static final String[] IMG = {".png", ".jpg", ".jpeg", ".webp"};

    private static String media(File rom, String base, String sys, List<File> mediaDirs, String... kinds) {
        for (File m : mediaDirs) {
            for (String kind : kinds) {
                for (String e : IMG) {
                    File f = new File(m, sys + "/" + kind + "/" + base + e);
                    if (f.isFile()) {
                        return f.getAbsolutePath();
                    }
                }
            }
        }
        for (String e : IMG) {
            File f = new File(rom.getParentFile(), base + e);
            if (f.isFile()) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }

    /* ---------- Starten ---------- */

    /** Alle Befehle für ein System, deren Emulator installiert ist. Eigenständige Emulatoren zuerst, RetroArch danach. */
    List<Launch> options(Game g, Set<String> installedPackages, String volumeId) {
        List<Launch> own = new ArrayList<>();
        List<Launch> retro = new ArrayList<>();
        Sys sys = systems.get(g.sys);
        if (sys == null) {
            return own;
        }
        for (Cmd c : sys.cmds) {
            List<String> comps = emulators.get(c.emulator);
            if (comps == null) {
                continue;
            }
            for (String comp : comps) {
                String pkg = comp.substring(0, comp.indexOf('/'));
                if (installedPackages.contains(pkg)) {
                    Launch l = build(c, comp, g, volumeId);
                    if (l != null) {
                        ("RETROARCH".equals(c.emulator) ? retro : own).add(l);
                    }
                    break;
                }
            }
        }
        own.addAll(retro);
        return own;
    }

    /** SAF-Adresse einer Datei, so wie Emulatoren sie von ES-DE erwarten. */
    static String saf(String path, String root, String volumeId) {
        String volRoot = path;
        int cut = "primary".equals(volumeId) ? path.indexOf("/0/") + 2 : path.indexOf('/', "/storage/".length());
        if (cut <= 0) {
            return "file://" + path;
        }
        volRoot = path.substring(0, cut);
        String rel = path.substring(volRoot.length() + 1);
        String treeRel = root.length() > volRoot.length() ? root.substring(volRoot.length() + 1) : "";
        return "content://com.android.externalstorage.documents/tree/" + enc(volumeId + ":" + treeRel) + "/document/" + enc(volumeId + ":" + rel);
    }

    static String enc(String s) {
        StringBuilder b = new StringBuilder();
        for (byte x : s.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int c = x & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') {
                b.append((char) c);
            } else {
                b.append('%').append(String.format(Locale.ROOT, "%02X", c));
            }
        }
        return b.toString();
    }

    Launch build(Cmd c, String component, Game g, String volumeId) {
        Launch l = new Launch();
        l.label = c.label;
        l.component = component;
        String pkg = component.substring(0, component.indexOf('/'));
        File rom = new File(g.path);
        String base = rom.getName().contains(".") ? rom.getName().substring(0, rom.getName().lastIndexOf('.')) : rom.getName();
        for (String token : c.template.split(" ")) {
            if (token.isEmpty() || token.startsWith("%EMULATOR_")) {
                continue;
            }
            if (token.equals("%ACTIVITY_CLEAR_TASK%")) {
                l.clearTask = true;
                continue;
            }
            if (token.equals("%ACTIVITY_CLEAR_TOP%")) {
                l.clearTop = true;
                continue;
            }
            int eq = token.indexOf("%=");
            if (eq < 0) {
                continue;
            }
            String name = token.substring(1, eq);
            String raw = token.substring(eq + 2);
            boolean provider = raw.contains("%ROMPROVIDER%");
            String value = raw.replace("%ROMPROVIDER%", g.path).replace("%ROMSAF%", saf(g.path, g.root, volumeId))
                    .replace("%ROMRAW%", g.path).replace("%ROMPATHRAW%", g.root).replace("%ROMPATH%", g.root)
                    .replace("%GAMEDIRRAW%", rom.getParent() == null ? "" : rom.getParent()).replace("%ROM%", g.path).replace("%BASENAME%", base)
                    .replace("%EXTERNALDATA%", "/storage/emulated/0").replace("%INTERNALDATA%", "/data/user/0").replace("%ANDROIDPACKAGE%", pkg);
            if (value.contains("%")) {
                // unbekannter Platzhalter: nur echte Prozentzeichen aus SAF-Adressen sind erlaubt
                if (Pattern.compile("%[A-Z_]+%").matcher(value).find()) {
                    return null;
                }
            }
            if (name.equals("ACTION")) {
                l.action = value;
            } else if (name.equals("CATEGORY")) {
                l.category = value;
            } else if (name.equals("MIMETYPE")) {
                l.mime = value;
            } else if (name.equals("DATA")) {
                l.data = value;
                l.dataIsProvider = provider;
            } else if (name.startsWith("EXTRABOOL_")) {
                l.bools.put(name.substring(10), Boolean.parseBoolean(value));
            } else if (name.startsWith("EXTRAINTEGER_")) {
                try {
                    l.ints.put(name.substring(13), Integer.parseInt(value));
                } catch (NumberFormatException ex) {
                    return null;
                }
            } else if (name.startsWith("EXTRAARRAY_")) {
                l.arrays.put(name.substring(11), value.split(","));
            } else if (name.startsWith("EXTRA_")) {
                l.extras.put(name.substring(6), value);
            }
        }
        return l;
    }
}
