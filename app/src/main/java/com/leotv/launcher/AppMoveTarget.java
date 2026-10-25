package com.leotv.launcher;

/** Reordering never crosses the boundary between Dock and the application grid. */
final class AppMoveTarget {
    static int target(boolean dock,int from,int count,int columns,int key) {
        if (from<0 || from>=count || columns<=0) return from;
        switch(key) {
            case 21: return from>0 && (dock || from%columns>0) ? from-1 : from;
            case 22: return from+1<count && (dock || from%columns<columns-1) ? from+1 : from;
            case 19: return !dock && from>=columns ? from-columns : from;
            case 20: return !dock && from+columns<count ? from+columns : from;
            default: return from;
        }
    }
}
