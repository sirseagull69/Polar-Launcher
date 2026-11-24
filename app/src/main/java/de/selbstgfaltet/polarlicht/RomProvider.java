package de.selbstgfaltet.polarlicht;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Gibt einem Emulator Lesezugriff auf genau die Spieldatei, die gestartet wird. */
public class RomProvider extends ContentProvider {
    static final String AUTHORITY = "de.selbstgfaltet.polarlicht.roms";
    /** Nur Dateien unter diesen Ordnern werden herausgegeben. */
    static final Set<String> ROOTS = ConcurrentHashMap.newKeySet();

    static Uri uriFor(String path) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("f").appendPath(path).build();
    }

    private static File file(Uri uri) throws FileNotFoundException {
        if (uri.getPathSegments().size() != 2) {
            throw new FileNotFoundException();
        }
        File f;
        try {
            f = new File(uri.getPathSegments().get(1)).getCanonicalFile();
        } catch (java.io.IOException e) {
            throw new FileNotFoundException();
        }
        for (String root : ROOTS) {
            if (f.getPath().startsWith(root + File.separator) && f.isFile()) {
                return f;
            }
        }
        throw new FileNotFoundException("nicht freigegeben");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        try {
            File f = file(uri);
            c.addRow(new Object[] {f.getName(), f.length()});
        } catch (FileNotFoundException ignored) {
            // leere Antwort
        }
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return "application/octet-stream";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
