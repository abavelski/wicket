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

import java.util.LinkedList;

import org.apache.wicket.MetaDataKey;
import org.apache.wicket.page.IManageablePage;
import org.apache.wicket.request.cycle.RequestCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Buffers storage of added pages until the end of the request, when they are delegated to the next store in
 * the identical order they where added.
 */
public class RequestPageStore extends DelegatingPageStore
{

	private static final Logger log = LoggerFactory.getLogger(RequestPageStore.class);

	private static final MetaDataKey<RequestData> KEY = new MetaDataKey<>()
	{
		private static final long serialVersionUID = 1L;
	};

	public RequestPageStore(IPageStore delegate)
	{
		super(delegate);
	}

	@Override
	public IManageablePage getPage(IPageContext context, int id)
	{
		IManageablePage page = getRequestData(context).get(id);
		if (page != null)
		{
			return page;
		}

		return super.getPage(context, id);
	}

	@Override
	public void addPage(IPageContext context, IManageablePage page)
	{
		getRequestData(context).add(page);
	}

	@Override
	public void removePage(IPageContext context, IManageablePage page)
	{
		getRequestData(context).remove(page);

		super.removePage(context, page);
	}

	@Override
	public void removeAllPages(IPageContext context)
	{
		getRequestData(context).removeAll();

		super.removeAllPages(context);
	}

	@Override
	public void detach(IPageContext context)
	{
		RequestData requestData = getRequestData(context);
		for (IManageablePage page : requestData.pages())
		{
			boolean isPageStateless;
			try
			{
				isPageStateless = page.isPageStateless();
			}
			catch (Exception x)
			{
				log.warn("An error occurred while checking whether a page is stateless. Assuming it is stateful.", x);
				isPageStateless = false;
			}

			if (isPageStateless == false)
			{
				super.addPage(context, page);
			}
		}
		requestData.removeAll();

		super.detach(context);
	}

	private RequestData getRequestData(IPageContext context)
	{
		RequestData requestData = context.getRequestData(KEY);
		if (requestData == null)
		{
			requestData = new RequestData();

			context.setRequestData(KEY, requestData);
		}
		return requestData;
	}
	
	/**
	 * Data kept in the {@link RequestCycle}.
	 */
	static class RequestData
	{
		private LinkedList<IManageablePage> pages = new LinkedList<>();
		
		public void add(IManageablePage page)
		{
			// add as last
			pages.remove(page);
			pages.addLast(page);
		}

		public Iterable<IManageablePage> pages()
		{
			return pages;
		}

		public IManageablePage get(int id) {
			for (IManageablePage page : pages) {
				if (page.getPageId() == id) {
					return page;
				}
			}
			return null;
		}

		public void remove(IManageablePage page)
		{
			pages.remove(page);
		}

		public void removeAll()
		{
			pages.clear();
		}		
	}
}