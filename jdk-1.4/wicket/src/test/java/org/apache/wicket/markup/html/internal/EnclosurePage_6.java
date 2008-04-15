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
package org.apache.wicket.markup.html.internal;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.Link;

/**
 * @author ivaynberg
 */
public class EnclosurePage_6 extends WebPage
{
	private int counter;

	/**
	 * Constructor
	 */
	public EnclosurePage_6()
	{
		add(new Link("link")
		{
			private static final long serialVersionUID = 1L;

			public void onClick()
			{
				counter++;
			}

		});

		add(new Label("label1", "content1")
		{
			private static final long serialVersionUID = 1L;

			public boolean isVisible()
			{
				return counter % 2 == 0;
			}
		});

		add(new Label("label2", "content2"));
	}
}
