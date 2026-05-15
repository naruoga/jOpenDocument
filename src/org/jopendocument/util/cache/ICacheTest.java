/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2008-2013 jOpenDocument, by ILM Informatique. All rights reserved.
 *
 * The contents of this file are subject to the terms of the GNU
 * General Public License Version 3 only ("GPL").
 * You may not use this file except in compliance with the License.
 * You can obtain a copy of the License at http://www.gnu.org/licenses/gpl-3.0.html
 * See the License for the specific language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each file.
 *
 */

package org.jopendocument.util.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.jopendocument.util.CollectionUtils;
import org.jopendocument.util.cache.CacheItem.RemovalType;
import org.jopendocument.util.cache.CacheResult.State;
import org.jopendocument.util.cache.ICache.ItemEvent;
import org.jopendocument.util.cc.IClosure;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ICacheTest {

    private final ThreadGroup thg = new ThreadGroup("testjunit") {
        public void uncaughtException(Thread t, Throwable e) {
            ICacheTest.this.threadException = e;
        }
    };

    private Thread launch(String name, Runnable runnable) {
        final Thread res = new Thread(this.thg, runnable, name);
        res.start();
        // really let res start...
        Thread.yield();
        return res;
    }

    private void join(final Thread t) throws Throwable {
        t.join();
        if (this.threadException != null) {
            final Throwable thw = this.threadException;
            this.threadException = null;
            throw thw;
        }
    }

    private static final int delay = 1;

    private ICache<String, Object, Object> cache;
    protected Throwable threadException;

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterAll
    public static void tearDownAfterClass() throws Exception {
    }

    @BeforeEach
    public void setUp() throws Exception {
        this.cache = new ICache<String, Object, Object>(delay);
    }

    @AfterEach
    public void tearDown() throws Exception {
        this.cache.getSupp().die();
        this.cache = null;
    }

    static class BogusCacheWatcher<D> extends CacheWatcher<D> {

        public BogusCacheWatcher(D d) {
            super(d);
        }

        public void clear() {
            this.dataChanged(null);
        }

    }

    @Test
    public void testWatcher() throws NoSuchMethodException {
        final ICache<String, List<Object>, String> cache = new ICache<String, List<Object>, String>(delay);
        cache.getSupp().setWatcherFactory(new CacheWatcherFactory<String>() {
            public CacheWatcher<String> createWatcher(String obj) throws Exception {
                return new BogusCacheWatcher<String>(obj);
            }
        });

        final String sel = "SELECT * FROM CONTACT";
        final String sel2 = "SELECT NOM FROM CONTACT";
        final List<Object> r = new ArrayList<Object>();
        final String data = "CONTACT_TABLE";
        assertFalse(cache.getSupp().dependsOn(data));
        assertEquals(0, cache.getSupp().purgeWatchers());

        // put both in cache
        final Collection<CacheWatcher<? super String>> put = cache.put(sel, r, Collections.singleton(data)).getWatchers().values();
        final BogusCacheWatcher<?> cw = (BogusCacheWatcher<?>) CollectionUtils.getSole(put);
        assertNotNull(cw);
        assertEquals(1, cache.getSupp().purgeWatchers());

        cache.put(sel2, r, Collections.singleton(data));
        // now cache depends on data
        assertTrue(cache.getSupp().dependsOn(data));
        assertEquals(1, cache.getSupp().purgeWatchers());
        cache.clear(sel2);
        // still depends on data because of sel
        assertTrue(cache.getSupp().dependsOn(data));
        assertSame(r, cache.get(sel).getRes());
        // apres une modif, le cache est invalidé
        cw.clear();
        assertSame(CacheResult.State.NOT_IN_CACHE, cache.get(sel).getState());
        assertFalse(cache.getSupp().dependsOn(data));
        assertEquals(1, cache.getSupp().getWatchersCount());
        assertEquals(0, cache.getSupp().purgeWatchers());

        // test replace
        {
            assertEquals(0, cache.size());
            final String data2 = "OTHER_TABLE";
            final String data3 = "data3";
            final List<CacheWatcher<? super String>> cws1 = new ArrayList<CacheWatcher<? super String>>(
                    cache.put(sel, r, new LinkedHashSet<String>(Arrays.asList(data, data2))).getWatchers().values());
            assertEquals(2, cws1.size());
            final CacheWatcher<? super String> cw1 = cws1.get(0);
            final CacheWatcher<? super String> cw2 = cws1.get(1);
            // order is kept
            assertEquals(data, cw1.getData());
            assertEquals(data2, cw2.getData());
            for (final CacheWatcher<? super String> w : cws1)
                assertFalse(w.isEmpty());

            // replace the key with different data
            final List<CacheWatcher<? super String>> cws2 = new ArrayList<CacheWatcher<? super String>>(
                    cache.put(sel, r, new LinkedHashSet<String>(Arrays.asList(data2, data3))).getWatchers().values());
            assertEquals(2, cws2.size());
            // order is kept and watcher re-used
            assertSame(cw2, cws2.get(0));
            final CacheWatcher<? super String> cw3 = cws2.get(1);
            assertEquals(data3, cw3.getData());

            // the first watcher was removed
            assertTrue(cw1.isEmpty());
            for (final CacheWatcher<? super String> w : cws2)
                assertFalse(w.isEmpty());
        }
    }

    @Test
    public void testInterrupt() throws Throwable {
        // test that we can interrupt a waiting thread
        final CacheResult<Object> res = this.getCache().check("SELECT *");
        final Thread tInt = launch("test interrupt", new Runnable() {
            public void run() {
                final CacheResult<Object> res = getCache().get("SELECT *");
                assertSame(CacheResult.State.INTERRUPTED, res.getState());
            }
        });
        tInt.interrupt();
        join(tInt);
        this.getCache().removeRunning(res);
    }

    @Test
    public void testGet() throws Throwable {
        // cache vide
        assertEquals(0, this.getCache().size());
        assertSame(CacheResult.State.NOT_IN_CACHE, this.getCache().get("SELECT 1").getState());

        for (final boolean replace : new boolean[] { false, true }) {
            final int replaceDelay = 400;
            final CacheItem<String, Object, Object> item1 = this.getCache().put("SELECT 1", new Integer(1), Collections.singleton(1));
            final long remainingTime = this.getCache().getRemainingTime("SELECT 1");
            assertTrue(delay * 1000 * 0.8 < remainingTime && remainingTime <= delay * 1000);
            // test timeout
            final Thread t = launch("test timeout", new Runnable() {
                public void run() {
                    try {
                        Thread.sleep((long) (delay * 1000 * 1.1));
                        assertSame(replace ? CacheResult.State.VALID : CacheResult.State.NOT_IN_CACHE, getCache().get("SELECT 1").getState());
                        if (replace) {
                            Thread.sleep((long) (replaceDelay * 1.1));
                            assertSame(CacheResult.State.NOT_IN_CACHE, getCache().get("SELECT 1").getState());
                        }
                        assertTrue(getCache().getRemainingTime("SELECT 1") <= 0);
                        assertEquals(replace ? RemovalType.DATA_CHANGE : RemovalType.TIMEOUT, item1.getRemovalType());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        fail(e.getMessage());
                    }
                }
            });
            assertEquals(new Integer(1), this.getCache().get("SELECT 1").getRes());
            if (replace) {
                Thread.sleep(replaceDelay);
                this.getCache().put("SELECT 1", new Integer(1), Collections.singleton(1));
            }
            join(t);
        }

        // cache <- cache2 <- cache3
        final ICache<String, Object, Object> cache2 = new ICache<String, Object, Object>();
        final ICache<String, Object, Object> cache3 = new ICache<String, Object, Object>();
        cache3.setParent(cache2);
        cache2.setParent(getCache());

        try {
            getCache().setParent(cache3);
            fail("Would create a cycle");
        } catch (Exception e) {
            // OK
        }
        assertNull(getCache().getParent());

        getCache().put("SELECT 1", 1);
        cache2.put("SELECT 2", 2);
        cache3.put("SELECT 3", 3);

        assertEquals(Integer.valueOf(1), this.getCache().get("SELECT 1").getRes());
        assertEquals(CacheResult.State.NOT_IN_CACHE, this.getCache().get("SELECT 2").getState());
        assertEquals(CacheResult.State.NOT_IN_CACHE, this.getCache().get("SELECT 3").getState());

        assertEquals(Integer.valueOf(1), cache2.get("SELECT 1").getRes());
        assertEquals(Integer.valueOf(2), cache2.get("SELECT 2").getRes());
        assertEquals(CacheResult.State.NOT_IN_CACHE, cache2.get("SELECT 3").getState());

        assertEquals(Integer.valueOf(1), cache3.get("SELECT 1").getRes());
        assertEquals(Integer.valueOf(2), cache3.get("SELECT 2").getRes());
        assertEquals(Integer.valueOf(3), cache3.get("SELECT 3").getRes());
    }

    @Test
    public void testRunning() throws Throwable {
        final String sel = "SELECT 2";
        assertFalse(this.getCache().isRunning(sel));
        final CacheResult<Object> checkRes = this.getCache().check(sel);
        assertEquals(State.NOT_IN_CACHE, checkRes.getState());
        assertTrue(checkRes.getVal().getState() == CacheItem.State.RUNNING);
        assertTrue(this.getCache().isRunning(sel));

        final Vector<Object> t2Res = new Vector<Object>();
        final CountDownLatch latch = new CountDownLatch(1);
        final Thread t2 = launch("test running", new Runnable() {
            public void run() {
                t2Res.add("begun");
                latch.countDown();
                t2Res.add(getCache().get(sel).getRes());
            }
        });

        // be sure that t2 started
        latch.await();
        assertEquals(1, t2Res.size());
        // and that it is waiting (give t2 some time to get to "get(sel)")
        Thread.sleep(20);
        assertEquals(Thread.State.WAITING, t2.getState());

        final Object two = new Object();
        assertTrue(getCache().isRunning(sel));
        this.getCache().put(sel, two, Collections.emptySet());
        assertFalse(getCache().isRunning(sel));
        // even though we didn't put checkRes it was stopped
        assertTrue(checkRes.getVal().getState() == CacheItem.State.INVALID);

        join(t2);
        // assert that t2 has been given our two
        assertSame(two, t2Res.get(1));

        // test data change while running
        {
            this.getCache().getSupp().setWatcherFactory(new CacheWatcherFactory<Object>() {
                public CacheWatcher<Object> createWatcher(Object obj) throws Exception {
                    return new BogusCacheWatcher<Object>(obj);
                }
            });

            final CacheResult<Object> check = this.getCache().check("SELECT 3", Collections.singleton("MATH"));
            final CacheItem<String, Object, Object> checkItem = this.getCache().getRunningValFromRes(check);
            assertNull(checkItem.getRemovalType());
            final BogusCacheWatcher<?> watcher = (BogusCacheWatcher<?>) checkItem.getWatchers().values().iterator().next();
            assertEquals("MATH", watcher.getData());

            final Thread otherThread = launch("test running", new Runnable() {
                @Override
                public void run() {
                    assertTrue(getCache().isRunning("SELECT 3"));
                    // even though we put a value in the cache, it wasn't given to this thread as
                    // the item was already invalid
                    assertEquals(State.NOT_IN_CACHE, getCache().get("SELECT 3").getState());
                }
            });

            Thread.sleep(10);
            // the cache item begun listening while the result was computed
            watcher.clear();
            assertEquals(RemovalType.DATA_CHANGE, checkItem.getRemovalType());

            // even though we put a value in the cache, it wasn't given to this thread as
            // the item was already invalid
            this.getCache().put(check, 3);
            assertEquals(State.NOT_IN_CACHE, this.getCache().get("SELECT 3").getState());

            join(otherThread);
        }

        // test already in cache
        {
            final Object nul = new Object();
            this.getCache().put("SELECT NULL", nul, Collections.emptySet());
            assertSame(nul, this.getCache().check("SELECT NULL").getRes());
            // it should be in cache
            assertFalse(this.getCache().isRunning("SELECT NULL"));
        }
    }

    @Test
    public void testSizeAndClear() {
        final ICache<String, String, Object> cache = new ICache<String, String, Object>(600, 1);
        final CacheItem<String, String, Object> aItem = cache.put("a", "A", Collections.emptySet());
        assertEquals(CacheItem.State.VALID, aItem.getState());
        assertNull(aItem.getRemovalType());
        assertEquals(1, cache.size());
        final CacheItem<String, String, Object> bItem = cache.put("b", "B", Collections.emptySet());
        assertEquals(CacheItem.State.VALID, bItem.getState());
        assertEquals(CacheItem.State.INVALID, aItem.getState());
        assertEquals(RemovalType.SIZE_LIMIT, aItem.getRemovalType());
        assertEquals(1, cache.size());
        assertSame(CacheResult.State.NOT_IN_CACHE, cache.get("a").getState());
    }

    @Test
    public void testClear() {
        final List<ItemEvent<String, Object, Object>> events = new ArrayList<>();
        final PropertyChangeListener listener = this.getCache().addItemListener(new IClosure<ItemEvent<String, Object, Object>>() {
            @Override
            public void executeChecked(ItemEvent<String, Object, Object> evt) {
                events.add(evt);
            }
        });
        final CacheItem<String, Object, Object> aItem = this.getCache().put("a", "A", Collections.emptySet());
        assertNull(aItem.getRemovalType());
        assertEquals(1, this.getCache().size());
        ItemEvent<String, Object, Object> evt = events.remove(0);
        assertEquals(ICache.ITEM_ADDED, evt.getPropertyName());
        assertNull(evt.getOldValue());
        assertSame(aItem, evt.getNewValue());
        assertTrue(events.isEmpty());

        this.getCache().clear("a");
        assertEquals(RemovalType.EXPLICIT, aItem.getRemovalType());
        assertEquals(0, this.getCache().size());
        evt = events.remove(0);
        assertEquals(ICache.ITEM_REMOVED, evt.getPropertyName());
        assertNull(evt.getNewValue());
        assertSame(aItem, evt.getOldValue());
        assertTrue(events.isEmpty());

        // we can remove something that isn't there
        this.getCache().clear("b");
        assertEquals(0, this.getCache().size());
        assertTrue(events.isEmpty());

        this.getCache().put("a", "A", Collections.emptySet());
        this.getCache().put("b", "B", Collections.emptySet());
        this.getCache().put("c", "C", Collections.emptySet());
        assertEquals(3, this.getCache().size());
        assertEquals(3, events.size());
        events.clear();

        this.getCache().clear();
        assertEquals(0, this.getCache().size());
        assertEquals("a", events.remove(0).getOldValue().getKey());
        assertEquals("b", events.remove(0).getOldValue().getKey());
        assertEquals("c", events.remove(0).getOldValue().getKey());
        assertTrue(events.isEmpty());

        this.getCache().removeListener(listener);

        // clear() is not final
        this.getCache().put("a", "A", Collections.emptySet());
        assertEquals(1, this.getCache().size());
        // listener removed
        assertTrue(events.isEmpty());
    }

    @Test
    public void testDie() throws Exception {
        this.getCache().getSupp().setWatcherFactory(new CacheWatcherFactory<Object>() {
            public CacheWatcher<Object> createWatcher(Object obj) throws Exception {
                return new BogusCacheWatcher<Object>(obj);
            }
        });

        final CacheItem<String, Object, Object> check = this.getCache().getRunningValFromRes(this.getCache().check("SELECT 4", Collections.singleton("MATH")));
        final CacheItem<String, Object, Object> cacheItem = this.getCache().put("SELECT 5", 5);
        final ICache<String, Object, Object> copy = this.getCache().copy("testDie 2", true);

        // even items without watchers are cleared
        assertTrue(cacheItem.getWatchers().isEmpty());
        assertEquals(CacheItem.State.RUNNING, check.getState());
        assertTrue(this.getCache().size() > 0);
        assertTrue(this.getCache().getRunning().size() > 0);
        assertTrue(copy.size() > 0);

        copy.getSupp().die();

        assertEquals(CacheItem.State.INVALID, check.getState());
        assertEquals(CacheItem.RemovalType.CACHE_DEATH, check.getRemovalType());
        assertEquals(CacheItem.RemovalType.CACHE_DEATH, cacheItem.getRemovalType());
        assertFalse(this.getCache().size() > 0);
        assertFalse(this.getCache().getRunning().size() > 0);
        assertFalse(copy.size() > 0);

        // immediately dead
        assertNull(copy.put("SELECT 5", 5));
        final CacheResult<Object> deadCheck = copy.check("SELECT 5", Collections.singleton("MATH"));
        assertFalse(copy.getRunning().size() > 0);
        assertFalse(copy.size() > 0);
        // the user of ICache doesn't need to know whether it is dead : it can always return the
        // result from check() (which obviously won't be added to the dead cache)
        assertNull(copy.put(deadCheck, 5));
    }

    private ICache<String, Object, Object> getCache() {
        return this.cache;
    }

}
