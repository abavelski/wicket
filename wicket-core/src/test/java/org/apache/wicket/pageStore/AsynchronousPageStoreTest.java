/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wicket.pageStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.RandomUtils;
import org.apache.wicket.MetaDataKey;
import org.apache.wicket.MockPage;
import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.mock.MockPageStore;
import org.apache.wicket.page.IManageablePage;
import org.apache.wicket.serialize.ISerializer;
import org.apache.wicket.serialize.java.DeflatedJavaSerializer;
import org.apache.wicket.util.WicketTestTag;
import org.apache.wicket.util.file.File;
import org.apache.wicket.util.lang.Bytes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;


/**
 * AsynchronousPageStoreTest
 *
 * @author manuelbarzi
 */
@Tag(WicketTestTag.SLOW)
public class AsynchronousPageStoreTest
{
	/** Log for reporting. */
	private static final Logger log = LoggerFactory.getLogger(AsynchronousPageStoreTest.class);

	@SuppressWarnings("serial")
	private static class DummyPage implements IManageablePage
	{

		private int pageId;
		private long writeMillis;
		private long readMillis;
		private String sessionId;

		private DummyPage(int pageId, long writeMillis, long readMillis, String sessionId)
		{
			this.pageId = pageId;
			this.writeMillis = writeMillis;
			this.readMillis = readMillis;
			this.sessionId = sessionId;
		}

		@Override
		public boolean isPageStateless()
		{
			return false;
		}

		@Override
		public int getPageId()
		{
			return pageId;
		}

		@Override
		public void detach()
		{
		}

		@Override
		public boolean setFreezePageId(boolean freeze)
		{
			return false;
		}

		/**
		 * @param s
		 * @throws IOException
		 */
		private void writeObject(java.io.ObjectOutputStream s) throws IOException
		{
			log.debug("serializing page {} for {}ms (session {})", getPageId(), writeMillis,
					sessionId);
			try
			{
				Thread.sleep(writeMillis);
			}
			catch (InterruptedException e)
			{
				throw new RuntimeException(e);
			}

			s.writeInt(pageId);
			s.writeLong(writeMillis);
			s.writeLong(readMillis);
			s.writeObject(sessionId);
		}

		private void readObject(java.io.ObjectInputStream s)
				throws IOException, ClassNotFoundException
		{
			log.debug("deserializing page {} for {}ms (session {})", getPageId(), writeMillis,
					sessionId);
			try
			{
				Thread.sleep(readMillis);
			}
			catch (InterruptedException e)
			{
				throw new RuntimeException(e);
			}

			pageId = s.readInt();
			writeMillis = s.readLong();
			readMillis = s.readLong();
			sessionId = (String)s.readObject();
		}

		public String toString()
		{
			return "DummyPage[pageId = " + pageId + ", writeMillis = " + writeMillis +
					", readMillis = " + readMillis + ", sessionId = " + sessionId + ", hashCode = " +
					hashCode() + "]";
		}
	}

	/**
	 * Store returns the same page instance from queue when there is a close request for it back
	 * again.
	 *
	 * @throws InterruptedException
	 */
	@Test
	void storeReturnsSameInstanceOnClosePageRequest() throws InterruptedException
	{
		final Semaphore semaphore = new Semaphore(0);
		
		IPageStore store = new NoopPageStore() {
			
			@Override
			public synchronized void addPage(IPageContext context, IManageablePage page)
			{
				try
				{
					// wait until the page was get below
					semaphore.acquire();
				}
				catch (InterruptedException e)
				{
				}
				
				super.addPage(context, page);
			}
			
			@Override
			public IManageablePage getPage(IPageContext context, int id)
			{
				fail();
				return null;
			}
		};

		IPageStore asyncPageStore = new AsynchronousPageStore(store, 100);

		int pageId = 0;
		
		String sessionId = "sessionId";
		
		IPageContext context = new DummyPageContext(sessionId);

		DummyPage page = new DummyPage(pageId, 1000, 1000, sessionId);
		asyncPageStore.addPage(context, page);

		IManageablePage pageBack = asyncPageStore.getPage(context, pageId);

		semaphore.release();

		assertEquals(page, pageBack);
		
		store.destroy();
	}

	/**
	 * Store returns the restored page instance from wrapped store when there is a distant request
	 * for it back again.
	 *
	 * @throws InterruptedException
	 */
	@Test
	void storeReturnsRestoredInstanceOnDistantPageRequest() throws InterruptedException
	{
		final Semaphore semaphore = new Semaphore(0);
		
		final AtomicBoolean got = new AtomicBoolean(false);
		
		IPageStore store = new MockPageStore() {
			
			@Override
			public synchronized void addPage(IPageContext context, IManageablePage page)
			{
				super.addPage(context, page);
				
				semaphore.release();
			}
			
			@Override
			public IManageablePage getPage(IPageContext context, int id)
			{
				got.set(true);
				
				return super.getPage(context, id);
			}
		};

		IPageStore asyncPageStore = new AsynchronousPageStore(store, 100);

		int pageId = 0;
		
		String sessionId = "sessionId";
		
		IPageContext context = new DummyPageContext(sessionId);

		DummyPage page = new DummyPage(pageId, 1000, 1000, sessionId);
		asyncPageStore.addPage(context, page);

		try
		{
			semaphore.acquire();
		}
		catch (InterruptedException e)
		{
		}

		IManageablePage pageBack = asyncPageStore.getPage(context, pageId);

		semaphore.release();

		assertEquals(page, pageBack);
		
		store.destroy();
	}

	/**
	 * Store works fully asynchronous when number of pages handled never exceeds the
	 * asynchronous-storage capacity.
	 *
	 * @throws InterruptedException
	 */
	@Test
	void storeBehavesAsyncWhenNotExceedingStoreCapacity() throws InterruptedException
	{
		int sessions = 2;
		int pages = 5;
		long writeMillis = 2000;
		long readMillis = 1500;
		int asyncPageStoreCapacity = pages * sessions;

		List<Metrics> results = runTest(sessions, pages, writeMillis, readMillis,
				asyncPageStoreCapacity);

		for (Metrics metrics : results)
			System.out.println(metrics);

		for (Metrics metrics : results)
		{
			assertEquals(metrics.storedPage, metrics.restoredPage);
			assertTrue(metrics.storingMillis < writeMillis);
			assertTrue(metrics.restoringMillis < readMillis);
		}
	}

	/**
	 * Store behaves sync from when number of pages handled exceeds the given asynchronous-storage
	 * capacity. It works asynchronous until the number of pages reaches the limit (capacity).
	 *
	 * @throws InterruptedException
	 */
	@Test
	void storeBehavesSyncFromWhenExceedingStoreCapacity() throws InterruptedException
	{
		int sessions = 2;
		int pages = 5;
		long writeMillis = 2000;
		long readMillis = 1500;
		int asyncPageStoreCapacity = pages; // only up to the first round of
		// pages

		List<Metrics> results = runTest(sessions, pages, writeMillis, readMillis,
				asyncPageStoreCapacity);

		for (Metrics metrics : results)
			System.out.println(metrics);

		int sync = 0;

		for (int i = 0; i < results.size(); i++)
		{
			Metrics metrics = results.get(i);

			assertEquals(metrics.storedPage.sessionId, metrics.restoredPage.sessionId);
			assertEquals(metrics.storedPage.getPageId(), metrics.restoredPage.getPageId());

			if (!metrics.storedPage.equals(metrics.restoredPage))
			{
				assertTrue(metrics.storingMillis >= metrics.storedPage.writeMillis);
				sync++;
			}
		}

		assertTrue(sync > 0);
	}

	private MetaDataKey<Serializable> KEY1 = new MetaDataKey<Serializable>()
	{
	};
	
	private MetaDataKey<Serializable> KEY2 = new MetaDataKey<Serializable>()
	{
	};
	
	/**
	 * Store does not allow modifications when pages are added asynchronously.
	 */
	@Test
	public void storeAsynchronousContextClosed() throws InterruptedException
	{
		IPageStore store = new MockPageStore() {
			
			@Override
			public boolean canBeAsynchronous(IPageContext context)
			{
				// can bind and get session id
				context.bind();
				context.getSessionId();
				
				// can access request data
				context.getRequestData(KEY1);
				context.setRequestData(KEY1, "value1");

				// can access session data
				context.getSessionData(KEY1);
				context.setSessionData(KEY1, "value1");

				// can access session
				context.getSessionAttribute("key1");
				context.setSessionAttribute("key1", "value1");

				return true;
			}
			
			@Override
			public synchronized void addPage(IPageContext context, IManageablePage page)
			{
				// can bind and get session id
				context.bind();
				context.getSessionId();
				
				// cannot access request
				try {
					context.getRequestData(KEY1);
					fail();
				} catch (WicketRuntimeException expected) {
				}
				try {
					context.setRequestData(KEY1, "value1");
					fail();
				} catch (WicketRuntimeException expected) {
				}
				try {
					context.getRequestData(KEY2);
					fail();
				} catch (WicketRuntimeException expected) {
				}
				try {
					context.setRequestData(KEY2, "value2");
					fail();
				} catch (WicketRuntimeException expected) {
				}

				// can read session data 
				context.getSessionData(KEY1);
				context.getSessionData(KEY2);
				// .. but cannot set
				try {
					context.setSessionData(KEY1, "value1");
					fail();
				} catch (WicketRuntimeException expected) {
				}
				try {
					context.setSessionData(KEY2, "value2");
					fail();
				} catch (WicketRuntimeException expected) {
				}
				
				// can read session already read
				context.getSessionAttribute("key1");
				// .. but nothing new
				try {
					context.getSessionAttribute("key2");
					fail();
				} catch (WicketRuntimeException expected) {
				}
				// .. but cannot set
				try {
					context.setSessionAttribute("key1", "value1");
					fail();
				} catch (WicketRuntimeException expected) {
				}
				try {
					context.setSessionAttribute("key2", "value2");
					fail();
				} catch (WicketRuntimeException expected) {
				}
			}
		};

		IPageStore asyncPageStore = new AsynchronousPageStore(store, 100);

		MockPage page = new MockPage();
		
		IPageContext context = new DummyPageContext();
		
		asyncPageStore.addPage(context , page);
		
		store.destroy();
	}
	
	// test run

	private class Metrics
	{
		private DummyPage storedPage;
		private long storingMillis;
		private DummyPage restoredPage;
		private long restoringMillis;

		public String toString()
		{
			return "Metrics[storedPage = " + storedPage + ", storingMillis = " + storingMillis +
					", restoredPage = " + restoredPage + ", restoringMillis = " + restoringMillis + "]";
		}
	}

	private List<Metrics> runTest(int sessions, int pages, long writeMillis, long readMillis,
	                              int asyncPageStoreCapacity) throws InterruptedException
	{

		List<Metrics> results = new ArrayList<>();

		final CountDownLatch lock = new CountDownLatch(pages * sessions);

		// ISerializer serializer = new DummySerializer();
		ISerializer serializer = new DeflatedJavaSerializer("applicationKey");

		IPageStore pageStore = new DiskPageStore("applicationName", new File("./target"),
				Bytes.bytes(10000l), serializer) {
			@Override
			public void addPage(IPageContext context, IManageablePage page)
			{

				super.addPage(context, page);

				lock.countDown();
			}
		};

		IPageStore asyncPageStore = new AsynchronousPageStore(pageStore, asyncPageStoreCapacity);

		Stopwatch stopwatch = Stopwatch.createUnstarted();

		for (int pageId = 1; pageId <= pages; pageId++)
		{
			for (int i = 1; i <= sessions; i++)
			{
				String sessionId = String.valueOf(i);
				IPageContext context = new DummyPageContext(sessionId);
				Metrics metrics = new Metrics();

				stopwatch.reset();
				DummyPage page = new DummyPage(pageId, around(writeMillis), around(readMillis),
						sessionId);
				stopwatch.start();
				asyncPageStore.addPage(context, page);
				metrics.storedPage = page;
				metrics.storingMillis = stopwatch.elapsed(TimeUnit.MILLISECONDS);

				stopwatch.reset();
				stopwatch.start();
				metrics.restoredPage = DummyPage.class
						.cast(asyncPageStore.getPage(context, pageId));
				metrics.restoringMillis = stopwatch.elapsed(TimeUnit.MILLISECONDS);

				results.add(metrics);
			}
		}

		lock.await(pages * sessions * (writeMillis + readMillis), TimeUnit.MILLISECONDS);

		pageStore.destroy();
		
		return results;
	}

	private long around(long target)
	{
		return RandomUtils.nextLong((long)(target * .9), (long)(target * 1.1));
	}
}
