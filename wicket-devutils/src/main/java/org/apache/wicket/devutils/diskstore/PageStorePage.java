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
package org.apache.wicket.devutils.diskstore;

import org.apache.wicket.Session;
import org.apache.wicket.devutils.diskstore.browser.PersistedPanel;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.pageStore.DelegatingPageStore;
import org.apache.wicket.pageStore.IPageStore;
import org.apache.wicket.pageStore.IPersistentPageStore;
import org.apache.wicket.request.mapper.parameter.PageParameters;

/**
 * A page that shows the attributes (id, name, size) of the pages stored in the data stores.
 */
public class PageStorePage extends WebPage
{

	/**
	 * Construct.
	 * 
	 * @param parameters
	 *            the request parameters
	 */
	public PageStorePage(final PageParameters parameters)
	{
		super(parameters);

		add(new PersistedPanel("persisted", PageStorePage::getPersistentPageStore));
	}

	@Override
	public boolean isVersioned()
	{
		return false;
	}
	
	public static IPersistentPageStore getPersistentPageStore() {
		IPageStore store = Session.get().getPageManager().getPageStore();
		while (true) {
			if (store instanceof IPersistentPageStore) {
				return (IPersistentPageStore)store;
			}
			
			if (store instanceof DelegatingPageStore) {
				store = ((DelegatingPageStore)store).getDelegate();
			} else {
				break;
			}
		}
		
		return null;
	}
}
