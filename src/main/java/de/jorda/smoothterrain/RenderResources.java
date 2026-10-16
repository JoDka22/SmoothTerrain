package de.jorda.smoothterrain;

import java.util.ArrayList;
import java.util.List;

/** Owns only newly built mesh results until the compiler hands them to its caller. */
public final class RenderResources implements AutoCloseable {
    private final List<AutoCloseable> owned=new ArrayList<>();
    private boolean committed,closed;
    public <T extends AutoCloseable> T track(T resource) {
        if(resource!=null) try {
            if(closed||committed)throw new IllegalStateException("Terrain mesh ownership is already closed or transferred");
            owned.add(resource);
        }
        catch(RuntimeException|Error failure) {
            // The mesh already owns native slices even if the ledger cannot grow.
            try {resource.close();} catch(Throwable cleanup) {suppress(failure,cleanup);}
            throw failure;
        }
        return resource;
    }
    public void commit() {
        if(closed)throw new IllegalStateException("Cannot transfer an already closed terrain mesh ledger");
        committed=true;owned.clear();
    }
    @Override public void close() {
        if(closed) return;closed=true;
        Throwable first=null;
        try {
            if(!committed) for(var resource:owned) {
                try { resource.close(); }
                catch(Throwable failure) {
                    if(first==null) first=failure;else suppress(first,failure);
                }
            }
        } finally { owned.clear(); }
        if(first!=null) throw new IllegalStateException("Failed to release an unfinished terrain mesh",first);
    }
    private static void suppress(Throwable original,Throwable extra) {
        if(original==extra) return;
        try {original.addSuppressed(extra);} catch(Throwable unavailable) {/* Continue releasing resources under memory pressure. */}
    }
}
