package com.yaeltex.seqarp168mk2.device;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extensions.framework.values.BooleanValueObject;

public abstract class RemotesGroup {
    protected final List<CursorRemoteControlsPage> remotes = new ArrayList<>();
    protected final String name;
    protected int pages = 0;
    protected final List<BooleanValueObject> pageExists = new ArrayList<>();
    
    public RemotesGroup(final String name, final Function<Integer, CursorRemoteControlsPage> creator, final int pages) {
        this.name = name;
        this.pages = pages;
        
        for (int i = 0; i < pages; i++) {
            final int index = i;
            final CursorRemoteControlsPage remote = creator.apply(i);
            pageExists.add(new BooleanValueObject());
            remote.selectedPageIndex().markInterested();
            remotes.add(remote);
        }
    }
    
    protected abstract void updatePages(int numberOfPages);
    
    public CursorRemoteControlsPage getRemotes(final int index) {
        return remotes.get(index);
    }
    
    public BooleanValueObject getPageExists(final int index) {
        return pageExists.get(index);
    }
    
}
