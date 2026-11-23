package org.mediashelf.tv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Read-only content URI for external players; never expose file:// paths. */
public final class LocalVideoProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File resolve(Uri uri) throws FileNotFoundException{
        try{return LocalVideoStore.resolve(getContext(),uri);}
        catch(IOException e){throw new FileNotFoundException(e.getMessage());}
    }
    @Override public String getType(Uri uri){
        try{return LocalVideoStore.mime(resolve(uri));}
        catch(Exception e){return "video/*";}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)
        throws FileNotFoundException{
        if(!"r".equals(mode))throw new FileNotFoundException("Только чтение");
        return ParcelFileDescriptor.open(resolve(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,
        String[] selectionArgs,String sortOrder){
        File file;
        try{file=resolve(uri);}catch(Exception e){return null;}
        String[] cols=projection!=null?projection:
            new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};
        MatrixCursor out=new MatrixCursor(cols);
        Object[] row=new Object[cols.length];
        for(int i=0;i<cols.length;i++){
            if(OpenableColumns.DISPLAY_NAME.equals(cols[i]))row[i]=file.getName();
            if(OpenableColumns.SIZE.equals(cols[i]))row[i]=file.length();
        }
        out.addRow(row);
        return out;
    }
    @Override public Uri insert(Uri uri,ContentValues values){
        throw new UnsupportedOperationException("Только чтение");
    }
    @Override public int update(Uri uri,ContentValues values,String selection,
        String[] selectionArgs){return 0;}
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){return 0;}
}