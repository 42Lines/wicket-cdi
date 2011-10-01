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

import javax.enterprise.context.Conversation;
import javax.enterprise.context.ConversationScoped;
import javax.inject.Inject;

import org.apache.wicket.MetaDataKey;
import org.apache.wicket.Page;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.cycle.AbstractRequestCycleListener;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.handler.IPageClassRequestHandler;
import org.apache.wicket.request.handler.IPageRequestHandler;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.util.lang.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A request cycle listener that takes care of propagating persistent conversations.
 * 
 * @see ConversationScoped
 * 
 * @author igor
 */
public class ConversationPropagator extends AbstractRequestCycleListener
{
	private static final Logger logger = LoggerFactory.getLogger(ConversationPropagator.class);

	private static final MetaDataKey<String> CID_KEY = new MetaDataKey<String>()
	{
	};

	private static final MetaDataKey<Boolean> CONVERSATION_STARTED_KEY = new MetaDataKey<Boolean>()
	{
	};


	private static final String CID = "cid";

	private final CdiContainer container;

	/** propagation mode to use */
	private final ConversationPropagation propagation;

	@Inject
	Conversation conversation_;

	/**
	 * Constructor
	 * 
	 * @param container
	 * @param propagation
	 */
	public ConversationPropagator(CdiContainer container, ConversationPropagation propagation)
	{
		if (propagation == ConversationPropagation.NONE)
		{
			throw new IllegalArgumentException(
				"If propagation is NONE do not set up the propagator");
		}

		this.container = container;
		this.propagation = propagation;

		container.getNonContextualManager().postConstruct(this);
	}

	private Conversation getConversation(RequestCycle cycle)
	{
		return Boolean.TRUE.equals(cycle.getMetaData(CONVERSATION_STARTED_KEY)) ? conversation_
			: null;
	}

	@Override
	public void onBeginRequest(RequestCycle cycle)
	{
		String cid = cycle.getRequest().getRequestParameters().getParameterValue("cid").toString();

		// start a conversation based on the url cid parameter. this conversation can later be
		// deactivated if we find a different cid in a request handler we resolve later.

		logger.debug("Activating conversation {}", cid);

		container.activateConversationalContext(cycle, cid);
		cycle.setMetaData(CONVERSATION_STARTED_KEY, true);
	}

	public void onRequestHandlerResolved(RequestCycle cycle, IRequestHandler handler)
	{
		String cid = null;
		Page page = getPage(handler);

		if (page != null)
		{
			cid = page.getMetaData(CID_KEY);
		}

		Conversation conversation = getConversation(cycle);
		if (cid != null && !Objects.equal(conversation.getId(), cid))
		{
			// deactivate the current temporary conversation we started in onbeginrequest()

			logger.debug(
				"Deactivating conversation {} because another cid was found in a resolved request handler",
				cid);

			container.deactivateConversationalContext(cycle);

			// activate the converastion resolved from the request handler

			logger.debug("Activating conversation {}", cid);

			container.activateConversationalContext(cycle, cid);
		}
	}

	@Override
	public void onRequestHandlerExecuted(RequestCycle cycle, IRequestHandler handler)
	{
		Conversation conversation = getConversation(cycle);

		if (conversation == null)
		{
			return;
		}


		// propagate a conversation across non-bookmarkable page instances

		Page page = getPage(handler);
		if (!conversation.isTransient() && page != null)
		{
			logger.debug("Propagating non-transient conversation {} via meta of page instance {}",
				conversation.getId(), page);

			page.setMetaData(CID_KEY, conversation.getId());
		}

	}

	public void onRequestHandlerScheduled(RequestCycle cycle, IRequestHandler handler)
	{
		Conversation conversation = getConversation(cycle);

		if (conversation == null || conversation.isTransient())
		{
			return;
		}

		// propagate a conversation across non-bookmarkable page instances

		Page page = getPage(handler);
		if (page != null)
		{
			logger.debug("Propagating non-transient conversation {} via meta of page instance {}",
				conversation.getId(), page);

			page.setMetaData(CID_KEY, conversation.getId());
		}

		if (propagation == ConversationPropagation.ALL)
		{
			// propagate cid to a scheduled bookmarkable page

			logger.debug(
				"Propagating non-transient conversation {} vua page parameters of handler {}",
				conversation.getId(), handler);

			PageParameters parameters = getPageParameters(handler);
			if (parameters != null)
			{
				parameters.add(CID, conversation.getId());
			}
		}
	}

	@Override
	public void onUrlMapped(RequestCycle cycle, IRequestHandler handler, Url url)
	{
		Conversation conversation = getConversation(cycle);

		if (conversation == null || conversation.isTransient())
		{
			return;
		}

		if (propagation == ConversationPropagation.ALL)
		{
			// propagate cid to bookmarkable pages via urls

			logger.debug("Propagating non-transient conversation {} via url", conversation.getId());

			url.setQueryParameter(CID, conversation.getId());
		}
	}

	@Override
	public void onDetach(RequestCycle cycle)
	{
		Conversation conversation = getConversation(cycle);
		if (conversation != null)
		{
			logger.debug("Deactivating conversation {}", conversation.getId());

			container.deactivateConversationalContext(cycle);

			cycle.setMetaData(CONVERSATION_STARTED_KEY, null);
		}
	}

	/**
	 * Resolves a page instance from the request handler iff the page instance is already created
	 * 
	 * @param handler
	 * @return page or {@code null} if none
	 */
	protected Page getPage(IRequestHandler handler)
	{
		if (handler instanceof IPageRequestHandler)
		{
			IPageRequestHandler pageHandler = (IPageRequestHandler)handler;
			if (pageHandler.isPageInstanceCreated())
			{
				return (Page)pageHandler.getPage();
			}
		}
		return null;
	}

	/**
	 * Resolves page parameters from a request handler
	 * 
	 * @param handler
	 * @return page parameters or {@code null} if none
	 */
	protected PageParameters getPageParameters(IRequestHandler handler)
	{
		if (handler instanceof IPageClassRequestHandler)
		{
			IPageClassRequestHandler pageHandler = (IPageClassRequestHandler)handler;
			return pageHandler.getPageParameters();
		}
		return null;
	}
}
