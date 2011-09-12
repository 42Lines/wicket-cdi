package net.ftlines.wicket.cdi;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.wicket.Component;

/**
 * Manages lifecycle of non-contextual objects like {@link Component} instances, etc
 * 
 * @author igor
 * 
 */
public interface INonContextualManager
{
	/**
	 * Inject a noncontextual instance and invokes any {@link PostConstruct} callbacks
	 * 
	 * @param <T>
	 * @param instance
	 */
	<T> void postConstruct(T instance);

	/**
	 * Invokes any {@link PreDestroy} callbacks and cleans up
	 * 
	 * @param <T>
	 * @param instance
	 */
	<T> void preDestroy(T instance);
}
