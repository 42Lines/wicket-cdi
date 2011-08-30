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
package net.ftlines.wicket.cdi;

import javax.enterprise.inject.spi.BeanManager;

import org.apache.wicket.Application;
import org.apache.wicket.request.cycle.RequestCycleListenerCollection;
import org.apache.wicket.util.lang.Args;

/**
 * Configures Weld integration
 * 
 * @author igor
 * 
 */
public class CdiConfiguration
{
	private BeanManager beanManager;
	private ConversationPropagation propagation = ConversationPropagation.NONBOOKMARKABLE;

	public CdiConfiguration(BeanManager beanManager)
	{
		Args.notNull(beanManager, "beanManager");
		this.beanManager = beanManager;
	}

	/**
	 * Gets the configured bean manager
	 * 
	 * @return bean manager or {@code null} if none
	 */
	public BeanManager getBeanManager()
	{
		return beanManager;
	}

	public ConversationPropagation getPropagation()
	{
		return propagation;
	}

	public CdiConfiguration setPropagation(ConversationPropagation propagation)
	{
		this.propagation = propagation;
		return this;
	}

	/**
	 * Configures the specified application
	 * 
	 * @param application
	 * @return
	 */
	public CdiContainer configure(Application application)
	{
		if (beanManager == null)
		{
			throw new IllegalStateException(
				"Configuration does not have a BeanManager instance configured");
		}

		CdiContainer container = new CdiContainer(beanManager);
		container.bind(application);

		application.getComponentInstantiationListeners().add(new CdiInjector(container));

		RequestCycleListenerCollection listeners = new RequestCycleListenerCollection();

		if (getPropagation() != ConversationPropagation.NONE)
		{
			listeners.add(new ConversationPropagator(container, getPropagation()));
		}
		
		listeners.add(new DetachEventEmitter(container));

		application.getRequestCycleListeners().add(listeners);

		return container;
	}

}
