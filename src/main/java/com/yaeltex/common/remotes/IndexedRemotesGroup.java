package com.yaeltex.common.remotes;

import java.util.List;
import java.util.function.Function;

import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extensions.framework.values.IntValueObject;

public class IndexedRemotesGroup extends RemotesGroup {
    
    private final List<IntValueObject> indexes;
    private int numberOfPages;
    
    public IndexedRemotesGroup(final String name, final Function<Integer, CursorRemoteControlsPage> creator,
        final List<IntValueObject> indexes) {
        super(name, creator, indexes.size());
        this.indexes = indexes;
        final CursorRemoteControlsPage firstRemote = getRemotes(0);
        firstRemote.pageCount().addValueObserver(this::updatePages);
        
        for (int i = 0; i < pages; i++) {
            final int index = i;
            final IntValueObject indexValue = indexes.get(i);
            indexValue.addValueObserver((oldValue, newValue) -> {
                applyChanges();
            });
        }
    }
    
    protected void updatePages(final int pages) {
        this.numberOfPages = pages;
        applyChanges();
    }
    
    private void applyChanges() {
        for (int i = 0; i < remotes.size(); i++) {
            final int index = i;
            final CursorRemoteControlsPage remote = remotes.get(i);
            final IntValueObject indexValue = indexes.get(i);
            pageExists.get(i).set(indexValue.get() < numberOfPages);
            remote.selectedPageIndex().set(indexValue.get());
        }
    }
    
    public void selectPage(final int rowIndex, final int pageIndex) {
        if (rowIndex < indexes.size()) {
            final IntValueObject indexValue = indexes.get(rowIndex);
            indexValue.set(pageIndex);
        }
    }
    
    public boolean isActive(final int rowIndex, final int pageIndex) {
        return indexes.get(rowIndex).get() == pageIndex;
    }
}
