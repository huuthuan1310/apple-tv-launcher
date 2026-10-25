package com.leotv.launcher;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
public class AppMoveTargetTest {
    @Test public void dockCanOnlyMoveHorizontally() {
        assertEquals(1,AppMoveTarget.target(true,2,4,6,21));
        assertEquals(3,AppMoveTarget.target(true,2,4,6,22));
        assertEquals(2,AppMoveTarget.target(true,2,4,6,19));
        assertEquals(2,AppMoveTarget.target(true,2,4,6,20));
        assertEquals(0,AppMoveTarget.target(true,0,4,6,21));
        assertEquals(3,AppMoveTarget.target(true,3,4,6,22));
    }
    @Test public void gridDoesNotEnterDockOrWrapRows() {
        assertEquals(0,AppMoveTarget.target(false,0,9,6,19));
        assertEquals(5,AppMoveTarget.target(false,5,9,6,22));
        assertEquals(6,AppMoveTarget.target(false,6,9,6,21));
        assertEquals(6,AppMoveTarget.target(false,0,9,6,20));
        assertEquals(0,AppMoveTarget.target(false,6,9,6,19));
        assertEquals(3,AppMoveTarget.target(false,3,9,6,20));
        assertEquals(8,AppMoveTarget.target(false,8,9,6,20));
    }
}
