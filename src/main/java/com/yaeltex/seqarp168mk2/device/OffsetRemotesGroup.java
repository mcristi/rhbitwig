package com.yaeltex.seqarp168mk2.device;

import java.util.function.Function;

import com.bitwig.extension.controller.api.CursorRemoteControlsPage;
import com.bitwig.extension.controller.api.SettableIntegerValue;

public class OffsetRemotesGroup extends RemotesGroup {
    
    private int numberOfPages;
    
    public OffsetRemotesGroup(final String name, final Function<Integer, CursorRemoteControlsPage> creator,
        final int pages) {
        super(name, creator, pages);
        final CursorRemoteControlsPage firstRemote = getRemotes(0);
        final SettableIntegerValue pageIndex = firstRemote.selectedPageIndex();
        pageIndex.addValueObserver(this::changeFromFirstIndex);
        firstRemote.pageCount().addValueObserver(this::updatePages);
    }
    
    protected void updatePages(final int pages) {
        this.numberOfPages = pages;
        final int page1Index = remotes.get(0).selectedPageIndex().get();
        changeFromFirstIndex(page1Index);
    }
    
    private void changeFromFirstIndex(final int index) {
        pageExists.get(0).set(index < pages);
        for (int i = 1; i < remotes.size(); i++) {
            final int selected = i + index;
            if (selected < pages) {
                remotes.get(i).selectedPageIndex().set(selected);
            }
            pageExists.get(i).set(selected < numberOfPages);
        }
    }
    
    
}
